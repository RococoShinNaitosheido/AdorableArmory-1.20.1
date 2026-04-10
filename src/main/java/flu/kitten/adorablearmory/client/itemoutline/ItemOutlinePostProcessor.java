package flu.kitten.adorablearmory.client.itemoutline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

public final class ItemOutlinePostProcessor {
    private static final int MASK_BUFFER_SIZE = 256 * 1024;
    private static final int MAX_SEARCH_RADIUS = 8;
    private static final float ALPHA_THRESHOLD = 0.01F;
    private static final float PROJECTED_RADIUS_REFERENCE_EXTENT = 96.0F;
    private static final float DEPTH_EPSILON = 2.5e-4F;
    private static final float MODEL_MIN = -0.30F;
    private static final float MODEL_MAX = 1.30F;
    private static final float CLIP_EPSILON = 1.0e-4F;
    private static final float[] BOX_CORNER_VALUES = {MODEL_MIN, MODEL_MAX};
    private static final BufferBuilder MASK_BUILDER = new BufferBuilder(MASK_BUFFER_SIZE);
    private static final MultiBufferSource.BufferSource MASK_BUFFER_SOURCE = MultiBufferSource.immediate(MASK_BUILDER);
    private static final Map<BakedModel, BakedModel> FLAT_MASK_MODELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<BakedModel, ModelBounds> MODEL_BOUNDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final RandomSource BOUNDS_RAND = RandomSource.create(0L);
    private static final int MAX_QUEUED_REGIONS = 64;
    private static final ArrayList<QueuedRegion> QUEUED_REGIONS = new ArrayList<>();
    private static final ClipInterval SCRATCH_INTERVAL = new ClipInterval();
    private static final int[][] BOX_EDGES = {{0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3}, {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}};
    private static final Matrix4f SCRATCH_COMBINED_MATRIX = new Matrix4f();
    private static final Vector4f SCRATCH_CLIP_VECTOR = new Vector4f();
    private static final float[] SCRATCH_CLIP_X = new float[8];
    private static final float[] SCRATCH_CLIP_Y = new float[8];
    private static final float[] SCRATCH_CLIP_Z = new float[8];
    private static final float[] SCRATCH_CLIP_W = new float[8];
    private static RenderTarget outlineTarget;
    private static boolean captureActive;
    private static boolean worldBatchActive;
    private static boolean batchInitialized;
    private static boolean currentCaptureNeedsDepth;
    private static boolean currentFirstPersonHandFastPath;
    private static boolean maskDirty;
    private static int dirtyMinX;
    private static int dirtyMinY;
    private static int dirtyMaxX;
    private static int dirtyMaxY;
    private static int dirtyMaxRadius;
    private static ShaderInstance cachedCompositeShader;
    private static AbstractUniform cachedAlphaThresholdUniform;
    private static AbstractUniform cachedMaxSearchRadiusUniform;
    private static AbstractUniform cachedDepthEpsilonUniform;
    private static AbstractUniform cachedEnableDepthOcclusionUniform;
    private static AbstractUniform cachedUseFirstPersonHandFastPathUniform;
    private record QueuedRegion(ScreenRect rect, int maxRadius) {}

    private record ModelBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        boolean isValid() {
            return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ) && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ) && maxX > minX && maxY > minY && maxZ > minZ;
        }
    }

    public static void prepareCapture(ItemDisplayContext context) {
        currentFirstPersonHandFastPath = false;

        if (isWorldBatchedContext(context)) {
            if (!worldBatchActive) {
                worldBatchActive = true;
                batchInitialized = false;
                maskDirty = false;
                dirtyMaxRadius = 1;
                resetDirtyRect();
            }

            captureActive = true;
            currentCaptureNeedsDepth = true;
            return;
        }

        if (worldBatchActive) {
            compositeWorldMaskIfActive();
        }

        captureActive = true;
        batchInitialized = false;
        currentCaptureNeedsDepth = needsMainDepth(context);
        maskDirty = false;
        dirtyMaxRadius = 1;
        resetDirtyRect();
    }

    public static boolean shouldDeferComposite(ItemDisplayContext context) {
        return worldBatchActive && isWorldBatchedContext(context);
    }

    public static void renderItemMask(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, BakedModel model, ItemOutlineData data) {
        if (!captureActive) return;

        ensureTargets();

        // 只对“第一人称主手”开 fast path（保持视觉一致，仅改变性能开关）
        currentFirstPersonHandFastPath = isFirstPersonHandContext(context) && isFirstPersonMainHand(context);

        BakedModel maskModel = resolveMaskModel(stack, model, context);

        // 半径参考 rect：保持你当前行为不变（必须）
        ScreenRect legacyRectForRadius = projectItemBoundsToScreenLegacy(poseStack);
        // 工作 rect：tight（修复全屏膨胀问题）
        ScreenRect tightRect = projectItemBoundsToScreenTight(poseStack, maskModel);
        if (tightRect.isEmpty() && legacyRectForRadius.isEmpty()) {
            captureActive = false;
            return;
        }

        ScreenRect baseRectForWork = tightRect.isEmpty() ? legacyRectForRadius : tightRect;
        ScreenRect rectForRadius = legacyRectForRadius.isEmpty() ? baseRectForWork : legacyRectForRadius;

        int effectiveRadius = resolveEffectiveRadius(data, context, rectForRadius);

        // 重要：仍然用同样的半径扩张，不改变视觉
        ScreenRect itemRect = clampToTarget(baseRectForWork.expand(effectiveRadius + 2));

        ScissorState capturedScissor = ScissorState.capture();
        ScreenRect effectiveRect = capturedScissor.intersect(itemRect);
        if (effectiveRect.isEmpty()) {
            captureActive = false;
            return;
        }

        ScreenRect clearRect = clampToTarget(effectiveRect.expand(effectiveRadius + 1));

        if (isWorldBatchedContext(context)) {
            flushWorldBatchIfNeeded(effectiveRect);
            initializeWorldBatchIfNeeded(capturedScissor);
        } else {
            initializeImmediateCapture();

            boolean clearDepth = !currentCaptureNeedsDepth;
            clearTargetRect(outlineTarget, clearRect, clearDepth, capturedScissor);
        }

        mergeDirtyRect(effectiveRect, effectiveRadius);

        int packedOverlay = pack2x16(data.red(), data.green());
        int packedLight = pack2x16(data.blue(), effectiveRadius);

        RenderType seedRenderType = ItemOutlineRenderTypes.itemMask();

        runWithScissor(capturedScissor, effectiveRect, () -> {
            VertexConsumer seedConsumer = MASK_BUFFER_SOURCE.getBuffer(seedRenderType);
            renderer.renderModelLists(maskModel, stack, packedLight, packedOverlay, poseStack, seedConsumer);
            MASK_BUFFER_SOURCE.endBatch(seedRenderType);
        });

        maskDirty = true;
        captureActive = false;
    }

    public static void compositeItemMaskIfActive() {
        if (worldBatchActive) return;
        compositeInternal();
    }

    public static void compositeWorldMaskIfActive() {
        if (!worldBatchActive) return;

        if (maskDirty && !isDirtyRectEmpty()) {
            queueCurrentDirtyRegion();
        }

        compositeQueuedRegions();
        worldBatchActive = false;
    }

    private static BakedModel resolveMaskModel(ItemStack stack, BakedModel model, ItemDisplayContext context) {
        if (stack.getItem() instanceof BlockItem) return model;
        if (context == ItemDisplayContext.GUI) {
            return FLAT_MASK_MODELS.computeIfAbsent(model, FlatItemMaskModel::new);
        }
        return model;
    }

    private static boolean isWorldBatchedContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.GROUND || context == ItemDisplayContext.FIXED;
    }

    private static boolean isFirstPersonHandContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private static boolean isFirstPersonMainHand(ItemDisplayContext context) {
        Minecraft mc = Minecraft.getInstance();
        HumanoidArm main = mc.options.mainHand().get(); // RIGHT / LEFT
        HumanoidArm rendered = (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) ? HumanoidArm.RIGHT : HumanoidArm.LEFT;
        return rendered == main;
    }

    private static boolean needsMainDepth(ItemDisplayContext context) {
        return switch (context) {
            case GROUND, FIXED, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, HEAD -> true;
            default -> false;
        };
    }

    private static int resolveEffectiveRadius(ItemOutlineData data, ItemDisplayContext context, ScreenRect rawRectForRadius) {
        int baseRadius = Mth.clamp(data.radiusPixels(), 1, MAX_SEARCH_RADIUS);

        if (context == ItemDisplayContext.GUI) {
            return baseRadius;
        }

        int referenceExtent = Math.max(1, Math.min(rawRectForRadius.width(), rawRectForRadius.height()));
        float scale = referenceExtent / PROJECTED_RADIUS_REFERENCE_EXTENT;
        int dynamicRadius = Math.round(baseRadius * scale);
        return Mth.clamp(dynamicRadius, 1, MAX_SEARCH_RADIUS);
    }

    private static void flushWorldBatchIfNeeded(ScreenRect nextRect) {
        if (!worldBatchActive || !batchInitialized || !maskDirty || isDirtyRectEmpty()) return;

        ScreenRect current = new ScreenRect(dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY);
        if (current.expand(MAX_SEARCH_RADIUS).intersects(nextRect.expand(MAX_SEARCH_RADIUS))) return;

        queueCurrentDirtyRegion();
        resetDirtyRect();
        dirtyMaxRadius = 1;
    }

    private static void queueCurrentDirtyRegion() {
        if (isDirtyRectEmpty()) return;

        ScreenRect rect = new ScreenRect(dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY);
        int radius = dirtyMaxRadius;

        if (QUEUED_REGIONS.size() >= MAX_QUEUED_REGIONS) {
            int last = QUEUED_REGIONS.size() - 1;
            QueuedRegion previous = QUEUED_REGIONS.get(last);
            QUEUED_REGIONS.set(last, new QueuedRegion(union(previous.rect, rect), Math.max(previous.maxRadius, radius)));
            return;
        }

        QUEUED_REGIONS.add(new QueuedRegion(rect, radius));
    }

    private static ScreenRect union(ScreenRect a, ScreenRect b) {
        return new ScreenRect(
                Math.min(a.minX(), b.minX()),
                Math.min(a.minY(), b.minY()),
                Math.max(a.maxX(), b.maxX()),
                Math.max(a.maxY(), b.maxY())
        );
    }

    private static void initializeWorldBatchIfNeeded(ScissorState capturedScissor) {
        if (batchInitialized) return;

        ensureTargets();
        copyDepthFromMainTarget();
        clearTargetFullColor(outlineTarget, capturedScissor);

        batchInitialized = true;
    }

    private static void initializeImmediateCapture() {
        if (batchInitialized) return;

        ensureTargets();

        if (currentCaptureNeedsDepth) {
            copyDepthFromMainTarget();
        }

        batchInitialized = true;
    }

    private static void copyDepthFromMainTarget() {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        outlineTarget.copyDepthFrom(mainTarget);
    }

    private static void clearTargetFullColor(RenderTarget target, ScissorState previousScissor) {
        target.bindWrite(false);
        RenderSystem.disableScissor();
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        restoreMainTarget();
        previousScissor.restore();
    }

    private static void clearTargetRect(RenderTarget target, ScreenRect rect, boolean clearDepth, ScissorState capturedScissor) {
        if (rect.isEmpty()) return;

        target.bindWrite(false);
        runWithScissor(capturedScissor, rect, () -> {
            RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);

            int clearMask = GL11.GL_COLOR_BUFFER_BIT;
            if (clearDepth) {
                RenderSystem.clearDepth(1.0D);
                clearMask |= GL11.GL_DEPTH_BUFFER_BIT;
            }

            RenderSystem.clear(clearMask, Minecraft.ON_OSX);
        });
        restoreMainTarget();
    }

    private static void compositeInternal() {
        if (!maskDirty || isDirtyRectEmpty()) {
            resetCaptureState();
            return;
        }

        ensureTargets();

        ShaderInstance shader = ItemOutlineRenderTypes.compositeShader();
        if (shader == null) {
            resetCaptureState();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        mainTarget.bindWrite(false);

        cacheCompositeUniforms(shader);
        bindCompositeSamplers(shader, mainTarget);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        ScreenRect dirtyRect = new ScreenRect(dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY);
        ScissorState capturedScissor = ScissorState.capture();

        runWithScissor(capturedScissor, dirtyRect, () -> drawCompositeRegion(shader));

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        resetCaptureState();
    }

    private static void compositeQueuedRegions() {
        if (QUEUED_REGIONS.isEmpty()) {
            resetCaptureState();
            return;
        }

        ensureTargets();

        ShaderInstance shader = ItemOutlineRenderTypes.compositeShader();
        if (shader == null) {
            QUEUED_REGIONS.clear();
            resetCaptureState();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        mainTarget.bindWrite(false);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        cacheCompositeUniforms(shader);
        bindCompositeSamplers(shader, mainTarget);

        if (cachedEnableDepthOcclusionUniform != null) cachedEnableDepthOcclusionUniform.set(1.0F);
        if (cachedUseFirstPersonHandFastPathUniform != null) cachedUseFirstPersonHandFastPathUniform.set(0.0F);

        ScissorState capturedScissor = ScissorState.capture();

        for (QueuedRegion region : QUEUED_REGIONS) {
            if (cachedMaxSearchRadiusUniform != null) {
                cachedMaxSearchRadiusUniform.set((float) Mth.clamp(region.maxRadius, 1, MAX_SEARCH_RADIUS));
            }
            runWithScissor(capturedScissor, region.rect, () -> drawCompositeRegion(shader));
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        QUEUED_REGIONS.clear();
        resetCaptureState();
    }

    private static void cacheCompositeUniforms(ShaderInstance shader) {
        if (cachedCompositeShader != shader) {
            cachedCompositeShader = shader;
            cachedAlphaThresholdUniform = shader.safeGetUniform("AlphaThreshold");
            cachedMaxSearchRadiusUniform = shader.safeGetUniform("MaxSearchRadius");
            cachedDepthEpsilonUniform = shader.safeGetUniform("DepthEpsilon");
            cachedEnableDepthOcclusionUniform = shader.safeGetUniform("EnableDepthOcclusion");
            cachedUseFirstPersonHandFastPathUniform = shader.safeGetUniform("UseFirstPersonHandFastPath");
        }

        if (cachedAlphaThresholdUniform != null) cachedAlphaThresholdUniform.set(ALPHA_THRESHOLD);
        if (cachedMaxSearchRadiusUniform != null) cachedMaxSearchRadiusUniform.set((float) Mth.clamp(dirtyMaxRadius, 1, MAX_SEARCH_RADIUS));
        if (cachedDepthEpsilonUniform != null) cachedDepthEpsilonUniform.set(DEPTH_EPSILON);

        if (cachedEnableDepthOcclusionUniform != null) {
            float enabled = (worldBatchActive || currentCaptureNeedsDepth) ? 1.0F : 0.0F;
            cachedEnableDepthOcclusionUniform.set(enabled);
        }

        if (cachedUseFirstPersonHandFastPathUniform != null) {
            cachedUseFirstPersonHandFastPathUniform.set(currentFirstPersonHandFastPath ? 1.0F : 0.0F);
        }
    }

    private static void bindCompositeSamplers(ShaderInstance shader, RenderTarget mainTarget) {
        RenderSystem.setShader(() -> shader);

        shader.setSampler("OutlineSampler", outlineTarget.getColorTextureId());
        shader.setSampler("OutlineDepthSampler", outlineTarget.getDepthTextureId());

        int mainDepth = mainTarget.getDepthTextureId();
        if (mainDepth <= 0) mainDepth = outlineTarget.getDepthTextureId();
        shader.setSampler("MainDepthSampler", mainDepth);
    }

    private static void drawCompositeRegion(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        builder.vertex( 1.0D, -1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        builder.vertex( 1.0D,  1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0D,  1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }

    private static ScreenRect projectItemBoundsToScreenLegacy(PoseStack poseStack) {
        ensureTargets();

        int width = outlineTarget.width;
        int height = outlineTarget.height;

        SCRATCH_COMBINED_MATRIX.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());

        int index = 0;
        for (float x : BOX_CORNER_VALUES) {
            for (float y : BOX_CORNER_VALUES) {
                for (float z : BOX_CORNER_VALUES) {
                    storeTransformedCorner(index++, x, y, z);
                }
            }
        }

        float minScreenX = Float.POSITIVE_INFINITY;
        float minScreenY = Float.POSITIVE_INFINITY;
        float maxScreenX = Float.NEGATIVE_INFINITY;
        float maxScreenY = Float.NEGATIVE_INFINITY;
        int projectedCount = 0;

        for (int i = 0; i < 8; i++) {
            float clipX = SCRATCH_CLIP_X[i];
            float clipY = SCRATCH_CLIP_Y[i];
            float clipW = SCRATCH_CLIP_W[i];

            if (!Float.isFinite(clipX) || !Float.isFinite(clipY) || !Float.isFinite(clipW) || clipW <= CLIP_EPSILON) {
                continue;
            }

            float ndcX = clipX / clipW;
            float ndcY = clipY / clipW;
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) continue;

            float screenX = (ndcX * 0.5F + 0.5F) * width;
            float screenY = (ndcY * 0.5F + 0.5F) * height;

            minScreenX = Math.min(minScreenX, screenX);
            minScreenY = Math.min(minScreenY, screenY);
            maxScreenX = Math.max(maxScreenX, screenX);
            maxScreenY = Math.max(maxScreenY, screenY);
            projectedCount++;
        }

        for (int[] edge : BOX_EDGES) {
            int aIndex = edge[0];
            int bIndex = edge[1];

            float ax = SCRATCH_CLIP_X[aIndex];
            float ay = SCRATCH_CLIP_Y[aIndex];
            float aw = SCRATCH_CLIP_W[aIndex];
            float bx = SCRATCH_CLIP_X[bIndex];
            float by = SCRATCH_CLIP_Y[bIndex];
            float bw = SCRATCH_CLIP_W[bIndex];

            if (!Float.isFinite(ax) || !Float.isFinite(ay) || !Float.isFinite(aw) || !Float.isFinite(bx) || !Float.isFinite(by) || !Float.isFinite(bw)) {
                continue;
            }

            boolean aVisible = aw > CLIP_EPSILON;
            boolean bVisible = bw > CLIP_EPSILON;
            if (aVisible == bVisible) continue;

            float denominator = bw - aw;
            if (!Float.isFinite(denominator) || Math.abs(denominator) < 1.0e-7F) continue;

            float t = Mth.clamp((CLIP_EPSILON - aw) / denominator, 0.0F, 1.0F);
            float clipX = Mth.lerp(t, ax, bx);
            float clipY = Mth.lerp(t, ay, by);
            float clipW = Mth.lerp(t, aw, bw);

            if (!Float.isFinite(clipX) || !Float.isFinite(clipY) || !Float.isFinite(clipW) || clipW <= CLIP_EPSILON) continue;

            float ndcX = clipX / clipW;
            float ndcY = clipY / clipW;
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) continue;

            float screenX = (ndcX * 0.5F + 0.5F) * width;
            float screenY = (ndcY * 0.5F + 0.5F) * height;

            minScreenX = Math.min(minScreenX, screenX);
            minScreenY = Math.min(minScreenY, screenY);
            maxScreenX = Math.max(maxScreenX, screenX);
            maxScreenY = Math.max(maxScreenY, screenY);
            projectedCount++;
        }

        if (projectedCount == 0 || !Float.isFinite(minScreenX) || !Float.isFinite(minScreenY) || !Float.isFinite(maxScreenX) || !Float.isFinite(maxScreenY)) {
            return new ScreenRect(0, 0, 0, 0);
        }

        int x0 = Mth.clamp((int) Math.floor(minScreenX), 0, width);
        int y0 = Mth.clamp((int) Math.floor(minScreenY), 0, height);
        int x1 = Mth.clamp((int) Math.ceil(maxScreenX), 0, width);
        int y1 = Mth.clamp((int) Math.ceil(maxScreenY), 0, height);

        if (x1 <= x0 || y1 <= y0) return new ScreenRect(0, 0, 0, 0);
        return new ScreenRect(x0, y0, x1, y1);
    }

    private static ScreenRect projectItemBoundsToScreenTight(PoseStack poseStack, BakedModel model) {
        ensureTargets();

        int width = outlineTarget.width;
        int height = outlineTarget.height;

        ModelBounds b = getModelBounds(model);
        if (!b.isValid()) {
            return new ScreenRect(0, 0, 0, 0);
        }

        SCRATCH_COMBINED_MATRIX.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());

        float minX = b.minX, minY = b.minY, minZ = b.minZ;
        float maxX = b.maxX, maxY = b.maxY, maxZ = b.maxZ;

        int idx = 0;
        for (float x : new float[]{minX, maxX}) {
            for (float y : new float[]{minY, maxY}) {
                for (float z : new float[]{minZ, maxZ}) {
                    storeTransformedCorner(idx++, x, y, z);
                }
            }
        }

        float minNdcX = Float.POSITIVE_INFINITY;
        float minNdcY = Float.POSITIVE_INFINITY;
        float maxNdcX = Float.NEGATIVE_INFINITY;
        float maxNdcY = Float.NEGATIVE_INFINITY;
        int count = 0;

        for (int i = 0; i < 8; i++) {
            float x = SCRATCH_CLIP_X[i];
            float y = SCRATCH_CLIP_Y[i];
            float z = SCRATCH_CLIP_Z[i];
            float w = SCRATCH_CLIP_W[i];
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w)) continue;

            if (isInsideClipVolume(x, y, z, w)) {
                float ndcX = x / w;
                float ndcY = y / w;
                minNdcX = Math.min(minNdcX, ndcX);
                minNdcY = Math.min(minNdcY, ndcY);
                maxNdcX = Math.max(maxNdcX, ndcX);
                maxNdcY = Math.max(maxNdcY, ndcY);
                count++;
            }
        }

        final ClipInterval interval = SCRATCH_INTERVAL;

        for (int[] e : BOX_EDGES) {
            int a = e[0];
            int c = e[1];

            float ax = SCRATCH_CLIP_X[a], ay = SCRATCH_CLIP_Y[a], az = SCRATCH_CLIP_Z[a], aw = SCRATCH_CLIP_W[a];
            float bx = SCRATCH_CLIP_X[c], by = SCRATCH_CLIP_Y[c], bz = SCRATCH_CLIP_Z[c], bw = SCRATCH_CLIP_W[c];

            if (!Float.isFinite(ax) || !Float.isFinite(ay) || !Float.isFinite(az) || !Float.isFinite(aw)
                    || !Float.isFinite(bx) || !Float.isFinite(by) || !Float.isFinite(bz) || !Float.isFinite(bw)) {
                continue;
            }

            interval.t0 = 0.0F;
            interval.t1 = 1.0F;

            if (!clipPlane(ax + aw,  bx + bw,  interval)) continue;
            if (!clipPlane(-ax + aw, -bx + bw, interval)) continue;
            if (!clipPlane(ay + aw,  by + bw,  interval)) continue;
            if (!clipPlane(-ay + aw, -by + bw, interval)) continue;
            if (!clipPlane(az + aw,  bz + bw,  interval)) continue;
            if (!clipPlane(-az + aw, -bz + bw, interval)) continue;

            float t0 = interval.t0;
            float t1 = interval.t1;
            if (t1 <= t0) continue;

            float dx = bx - ax, dy = by - ay, dz = bz - az, dw = bw - aw;

            for (float t : new float[]{t0, t1}) {
                float x = ax + dx * t;
                float y = ay + dy * t;
                float z = az + dz * t;
                float w = aw + dw * t;

                if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w)) continue;
                if (!isInsideClipVolume(x, y, z, w)) continue;

                float ndcX = x / w;
                float ndcY = y / w;

                minNdcX = Math.min(minNdcX, ndcX);
                minNdcY = Math.min(minNdcY, ndcY);
                maxNdcX = Math.max(maxNdcX, ndcX);
                maxNdcY = Math.max(maxNdcY, ndcY);
                count++;
            }
        }

        if (count == 0 || !Float.isFinite(minNdcX) || !Float.isFinite(minNdcY) || !Float.isFinite(maxNdcX) || !Float.isFinite(maxNdcY)) {
            return new ScreenRect(0, 0, 0, 0);
        }

        minNdcX = Mth.clamp(minNdcX, -1.0F, 1.0F);
        maxNdcX = Mth.clamp(maxNdcX, -1.0F, 1.0F);
        minNdcY = Mth.clamp(minNdcY, -1.0F, 1.0F);
        maxNdcY = Mth.clamp(maxNdcY, -1.0F, 1.0F);

        float minScreenX = (minNdcX * 0.5F + 0.5F) * width;
        float maxScreenX = (maxNdcX * 0.5F + 0.5F) * width;
        float minScreenY = (minNdcY * 0.5F + 0.5F) * height;
        float maxScreenY = (maxNdcY * 0.5F + 0.5F) * height;

        int x0 = Mth.clamp((int) Math.floor(minScreenX), 0, width);
        int y0 = Mth.clamp((int) Math.floor(minScreenY), 0, height);
        int x1 = Mth.clamp((int) Math.ceil(maxScreenX), 0, width);
        int y1 = Mth.clamp((int) Math.ceil(maxScreenY), 0, height);

        if (x1 <= x0 || y1 <= y0) return new ScreenRect(0, 0, 0, 0);
        return new ScreenRect(x0, y0, x1, y1);
    }

    private static final class ClipInterval {
        float t0;
        float t1;
    }

    private static boolean clipPlane(float f0, float f1, ClipInterval interval) {
        if (f0 >= 0.0F && f1 >= 0.0F) {
            return true;
        }
        if (f0 < 0.0F && f1 < 0.0F) {
            return false;
        }

        float denom = (f0 - f1);
        if (!Float.isFinite(denom) || Math.abs(denom) < 1.0e-20F) {
            return false;
        }

        float t = f0 / denom;
        if (!Float.isFinite(t)) return false;

        if (f0 < 0.0F) {
            interval.t0 = Math.max(interval.t0, t);
        } else {
            interval.t1 = Math.min(interval.t1, t);
        }

        return interval.t0 <= interval.t1;
    }

    private static boolean isInsideClipVolume(float x, float y, float z, float w) {
        return w > 0.0F && x >= -w && x <= w && y >= -w && y <= w && z >= -w && z <= w;
    }

    private static ModelBounds getModelBounds(BakedModel model) {
        ModelBounds cached = MODEL_BOUNDS.get(model);
        if (cached != null) return cached;

        ModelBounds computed = computeModelBounds(model);
        MODEL_BOUNDS.put(model, computed);
        return computed;
    }

    private static ModelBounds computeModelBounds(BakedModel model) {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        boolean any = false;

        for (int pass = -1; pass < Direction.values().length; pass++) {
            @Nullable Direction side = (pass < 0) ? null : Direction.values()[pass];

            BOUNDS_RAND.setSeed(0L);
            var quads = model.getQuads(null, side, BOUNDS_RAND);
            if (quads.isEmpty()) continue;

            for (BakedQuad q : quads) {
                int[] v = q.getVertices();
                if (v.length < 12) continue;

                int stride = v.length / 4;
                for (int i = 0; i < 4; i++) {
                    int base = i * stride;
                    float x = Float.intBitsToFloat(v[base]);
                    float y = Float.intBitsToFloat(v[base + 1]);
                    float z = Float.intBitsToFloat(v[base + 2]);

                    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) continue;

                    minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
                    any = true;
                }
            }
        }

        if (!any) {
            return new ModelBounds(MODEL_MIN, MODEL_MIN, MODEL_MIN, MODEL_MAX, MODEL_MAX, MODEL_MAX);
        }

        float pad = 1.0e-3F;
        return new ModelBounds(minX - pad, minY - pad, minZ - pad, maxX + pad, maxY + pad, maxZ + pad);
    }

    private static void storeTransformedCorner(int index, float x, float y, float z) {
        SCRATCH_CLIP_VECTOR.set(x, y, z, 1.0F);
        SCRATCH_COMBINED_MATRIX.transform(SCRATCH_CLIP_VECTOR);

        SCRATCH_CLIP_X[index] = SCRATCH_CLIP_VECTOR.x;
        SCRATCH_CLIP_Y[index] = SCRATCH_CLIP_VECTOR.y;
        SCRATCH_CLIP_Z[index] = SCRATCH_CLIP_VECTOR.z;
        SCRATCH_CLIP_W[index] = SCRATCH_CLIP_VECTOR.w;
    }

    private static void mergeDirtyRect(ScreenRect rect, int radiusPixels) {
        if (isDirtyRectEmpty()) {
            dirtyMinX = rect.minX();
            dirtyMinY = rect.minY();
            dirtyMaxX = rect.maxX();
            dirtyMaxY = rect.maxY();
            dirtyMaxRadius = radiusPixels;
            return;
        }

        dirtyMinX = Math.min(dirtyMinX, rect.minX());
        dirtyMinY = Math.min(dirtyMinY, rect.minY());
        dirtyMaxX = Math.max(dirtyMaxX, rect.maxX());
        dirtyMaxY = Math.max(dirtyMaxY, rect.maxY());
        dirtyMaxRadius = Math.max(dirtyMaxRadius, radiusPixels);
    }

    private static boolean isDirtyRectEmpty() {
        return dirtyMinX >= dirtyMaxX || dirtyMinY >= dirtyMaxY;
    }

    private static void resetDirtyRect() {
        dirtyMinX = Integer.MAX_VALUE;
        dirtyMinY = Integer.MAX_VALUE;
        dirtyMaxX = Integer.MIN_VALUE;
        dirtyMaxY = Integer.MIN_VALUE;
    }

    private static void resetCaptureState() {
        captureActive = false;
        batchInitialized = false;
        currentCaptureNeedsDepth = false;
        currentFirstPersonHandFastPath = false;
        maskDirty = false;
        dirtyMaxRadius = 1;
        resetDirtyRect();
    }

    static void bindOutlineTarget() {
        ensureTargets();
        outlineTarget.bindWrite(false);
    }

    static void restoreMainTarget() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
    }

    private static void ensureTargets() {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();

        if (outlineTarget == null) {
            outlineTarget = new TextureTarget(mainTarget.width, mainTarget.height, true, Minecraft.ON_OSX);
            configureTarget(outlineTarget);
        }

        if (outlineTarget.width != mainTarget.width || outlineTarget.height != mainTarget.height) {
            outlineTarget.resize(mainTarget.width, mainTarget.height, Minecraft.ON_OSX);
            configureTarget(outlineTarget);
            resetCaptureState();
        }
    }

    private static void configureTarget(RenderTarget target) {
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.setFilterMode(GL11.GL_NEAREST);
    }

    private static void runWithScissor(ScissorState capturedScissor, ScreenRect requestedRect, Runnable action) {
        ScreenRect effectiveRect = capturedScissor.intersect(requestedRect);
        if (effectiveRect.isEmpty()) return;

        try {
            RenderSystem.enableScissor(effectiveRect.minX(), effectiveRect.minY(), effectiveRect.width(), effectiveRect.height());
            action.run();
        } finally {
            capturedScissor.restore();
        }
    }

    private static ScreenRect clampToTarget(ScreenRect rect) {
        ensureTargets();

        int width = outlineTarget.width;
        int height = outlineTarget.height;

        int x0 = Mth.clamp(rect.minX(), 0, width);
        int y0 = Mth.clamp(rect.minY(), 0, height);
        int x1 = Mth.clamp(rect.maxX(), 0, width);
        int y1 = Mth.clamp(rect.maxY(), 0, height);

        return new ScreenRect(x0, y0, x1, y1);
    }

    private static int pack2x16(int lo, int hi) {
        return (lo & 0xFFFF) | ((hi & 0xFFFF) << 16);
    }

    private record ScreenRect(int minX, int minY, int maxX, int maxY) {
        int width() { return maxX - minX; }
        int height() { return maxY - minY; }
        boolean isEmpty() { return width() <= 0 || height() <= 0; }

        ScreenRect expand(int pad) {
            return new ScreenRect(minX - pad, minY - pad, maxX + pad, maxY + pad);
        }

        boolean intersects(ScreenRect other) {
            return this.maxX > other.minX && this.minX < other.maxX && this.maxY > other.minY && this.minY < other.maxY;
        }
    }

    private record ScissorState(boolean enabled, int x, int y, int width, int height) {
        static ScissorState capture() {
            boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            if (!enabled) return new ScissorState(false, 0, 0, 0, 0);

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer box = stack.mallocInt(4);
                GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
                return new ScissorState(true, box.get(0), box.get(1), box.get(2), box.get(3));
            }
        }

        ScreenRect intersect(ScreenRect rect) {
            if (!enabled) return rect;

            int x0 = Math.max(x, rect.minX());
            int y0 = Math.max(y, rect.minY());
            int x1 = Math.min(x + width, rect.maxX());
            int y1 = Math.min(y + height, rect.maxY());

            if (x1 <= x0 || y1 <= y0) return new ScreenRect(0, 0, 0, 0);
            return new ScreenRect(x0, y0, x1, y1);
        }

        void restore() {
            if (!enabled || width <= 0 || height <= 0) {
                RenderSystem.disableScissor();
                return;
            }
            RenderSystem.enableScissor(x, y, width, height);
        }
    }

    private ItemOutlinePostProcessor() {}
}
