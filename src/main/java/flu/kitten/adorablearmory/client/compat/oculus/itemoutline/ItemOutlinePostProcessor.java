package flu.kitten.adorablearmory.client.compat.oculus.itemoutline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import flu.kitten.adorablearmory.client.compat.oculus.ItemShaderModCompat;
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
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;
import javax.annotation.Nullable;
import java.nio.IntBuffer;
import java.util.*;

public final class ItemOutlinePostProcessor {
    private static final int MASK_BUFFER_SIZE = 256 * 1024;
    private static final int MAX_SEARCH_RADIUS = 8;
    private static final float ALPHA_THRESHOLD = 0.01F;
    private static final float DEPTH_EPSILON = 2.5e-4F;
    private static final float MODEL_MIN = -0.30F;
    private static final float MODEL_MAX = 1.30F;
    private static final float CLIP_EPSILON = 1.0e-4F;
    private static final float TIGHT_W_EPSILON = 1.0e-4F;
    private static final int MAX_QUEUED_REGIONS = 64;
    private static final int FIRST_PERSON_STABILIZE_PAD = 10;
    private static final int FIRST_PERSON_MAX_SHRINK_PER_FRAME = 12;
    private static final int FIRST_PERSON_EXTRA_SCISSOR_PAD = 10;
    private static final int NON_FIRST_PERSON_EXTRA_SCISSOR_PAD = 4;
    private static final long FIRST_PERSON_PREVIOUS_RECT_FALLBACK_NS = 200_000_000L;
    private static final float[] BOX_CORNER_VALUES = {MODEL_MIN, MODEL_MAX};
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final int[][] BOX_EDGES = {{0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3}, {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}};
    private static final BufferBuilder MASK_BUILDER = new BufferBuilder(MASK_BUFFER_SIZE);
    private static final MultiBufferSource.BufferSource MASK_BUFFER_SOURCE = MultiBufferSource.immediate(MASK_BUILDER);
    private static final Map<BakedModel, BakedModel> FLAT_MASK_MODELS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<BakedModel, ModelBounds> MODEL_BOUNDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<BakedModel, ModelProjectionData> MODEL_PROJECTION_DATA = Collections.synchronizedMap(new WeakHashMap<>());
    private static final RandomSource BOUNDS_RAND = RandomSource.create(0L);
    private static final ArrayList<QueuedRegion> QUEUED_REGIONS = new ArrayList<>();
    private static final ArrayList<QueuedRegion> DEFERRED_FIRST_PERSON_REGIONS = new ArrayList<>();
    private static final ArrayList<QueuedRegion> DEFERRED_FIRST_PERSON_TRANSIENT_REGIONS = new ArrayList<>();
    private static final ClipInterval SCRATCH_INTERVAL = new ClipInterval();
    private static final Matrix4f SCRATCH_COMBINED_MATRIX = new Matrix4f();
    private static final Vector4f SCRATCH_CLIP_VECTOR = new Vector4f();
    private static final float[] SCRATCH_CLIP_X = new float[8];
    private static final float[] SCRATCH_CLIP_Y = new float[8];
    private static final float[] SCRATCH_CLIP_Z = new float[8];
    private static final float[] SCRATCH_CLIP_W = new float[8];
    private static RenderTarget outlineTarget;
    private static RenderTarget transientOutlineTarget;
    private static boolean captureActive;
    private static boolean currentCaptureUsesTransientTarget;
    private static boolean worldBatchActive;
    private static boolean worldBatchSuspended;
    private static int guiEntityPreviewDepth;
    private static boolean guiEntityBatchActive;
    private static final CaptureBatchState CAPTURE_STATE = new CaptureBatchState();
    private static final CaptureBatchState SUSPENDED_CAPTURE_STATE = new CaptureBatchState();
    private static ShaderInstance cachedCompositeShader;
    private static AbstractUniform cachedAlphaThresholdUniform;
    private static AbstractUniform cachedMaxSearchRadiusUniform;
    private static AbstractUniform cachedDepthEpsilonUniform;
    private static AbstractUniform cachedEnableDepthOcclusionUniform;
    private static AbstractUniform cachedUseFirstPersonHandFastPathUniform;
    private static boolean depthCopyUnavailable;
    private static final HandState[] FIRST_PERSON_HANDS = {new HandState(), new HandState()};

    private record QueuedRegion(ScreenRect rect, int maxRadius) {}

    private record ModelBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {}

    private record ModelVertex(float x, float y, float z) {}

    private record ModelEdge(int a, int b) {}

    private record ModelProjectionData(List<ModelVertex> vertices, List<ModelEdge> edges) {
        boolean isValid() {
            return !vertices.isEmpty() && !edges.isEmpty();
        }
    }

    private record ScreenRect(int minX, int minY, int maxX, int maxY) {
        int width() { return maxX - minX; }
        int height() { return maxY - minY; }
        boolean isEmpty() { return width() <= 0 || height() <= 0; }
        ScreenRect expand(int pad) { return new ScreenRect(minX - pad, minY - pad, maxX + pad, maxY + pad); }

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
            if (x1 <= x0 || y1 <= y0) return emptyRect();
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

    private static final class ClipInterval {
        float t0;
        float t1;
    }

    private static final class CaptureBatchState {
        boolean initialized;
        boolean needsDepth;
        boolean firstPersonHandFastPath;
        boolean maskDirty;
        int dirtyMinX = Integer.MAX_VALUE;
        int dirtyMinY = Integer.MAX_VALUE;
        int dirtyMaxX = Integer.MIN_VALUE;
        int dirtyMaxY = Integer.MIN_VALUE;
        int dirtyMaxRadius = 1;

        boolean hasDirtyRect() {
            return dirtyMinX < dirtyMaxX && dirtyMinY < dirtyMaxY;
        }

        ScreenRect dirtyRect() {
            return hasDirtyRect() ? new ScreenRect(dirtyMinX, dirtyMinY, dirtyMaxX, dirtyMaxY) : emptyRect();
        }

        void mergeDirtyRect(ScreenRect rect, int radiusPixels) {
            if (!hasDirtyRect()) {
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

        void resetDirtyRect() {
            dirtyMinX = Integer.MAX_VALUE;
            dirtyMinY = Integer.MAX_VALUE;
            dirtyMaxX = Integer.MIN_VALUE;
            dirtyMaxY = Integer.MIN_VALUE;
        }

        void reset() {
            initialized = false;
            needsDepth = false;
            firstPersonHandFastPath = false;
            maskDirty = false;
            dirtyMaxRadius = 1;
            resetDirtyRect();
        }

        void copyFrom(CaptureBatchState other) {
            initialized = other.initialized;
            needsDepth = other.needsDepth;
            firstPersonHandFastPath = other.firstPersonHandFastPath;
            maskDirty = other.maskDirty;
            dirtyMinX = other.dirtyMinX;
            dirtyMinY = other.dirtyMinY;
            dirtyMaxX = other.dirtyMaxX;
            dirtyMaxY = other.dirtyMaxY;
            dirtyMaxRadius = other.dirtyMaxRadius;
        }
    }

    private static final class HandState {
        @Nullable ScreenRect workRect;
        long rectUpdateNanos;
        int rectModelId;
        int rectItemId;
        int radius = -1;
        int radiusModelId;
        int radiusItemId;

        boolean hasDifferentRectIdentity(int modelId, int itemId) {
            return rectModelId != modelId || rectItemId != itemId;
        }

        boolean hasDifferentRadiusIdentity(int modelId, int itemId) {
            return radius < 0 || radiusModelId != modelId || radiusItemId != itemId;
        }

        void storeRect(ScreenRect rect, int modelId, int itemId) {
            workRect = rect;
            rectModelId = modelId;
            rectItemId = itemId;
            rectUpdateNanos = System.nanoTime();
        }

        void storeRadius(int newRadius, int modelId, int itemId) {
            radius = newRadius;
            radiusModelId = modelId;
            radiusItemId = itemId;
        }

        void reset() {
            workRect = null;
            rectUpdateNanos = 0L;
            rectModelId = 0;
            rectItemId = 0;
            radius = -1;
            radiusModelId = 0;
            radiusItemId = 0;
        }
    }

    public static void renderItemMask(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, BakedModel model, ItemOutlineData data) {
        if (!captureActive) {
            return;
        }
        ensureTargets();
        CAPTURE_STATE.firstPersonHandFastPath = isFirstPersonHandContext(context);
        BakedModel maskModel = resolveMaskModel(stack, model, context);
        ScreenRect tightRect = projectItemBoundsToScreenTight(poseStack, maskModel);
        ScreenRect previousFpRect = isFirstPersonHandContext(context) ? getPreviousFirstPersonWorkRect(context, stack, maskModel) : null;
        ScreenRect legacyRect = emptyRect();
        if (tightRect.isEmpty()) {
            if (previousFpRect != null) {
                tightRect = previousFpRect;
            } else {
                legacyRect = projectItemBoundsToScreenLegacy(poseStack);
            }
        }
        if (tightRect.isEmpty() && legacyRect.isEmpty()) {
            captureActive = false;
            return;
        }
        ScreenRect baseRectForWork = tightRect.isEmpty() ? legacyRect : tightRect;
        int effectiveRadius = resolveEffectiveRadius(data);
        if (isFirstPersonHandContext(context)) {
            baseRectForWork = stabilizeFirstPersonWorkRect(context, baseRectForWork, stack, maskModel);
            effectiveRadius = stabilizeFirstPersonRadius(context, stack, maskModel, effectiveRadius);
        } else if (!baseRectForWork.isEmpty()) {
            baseRectForWork = clampToTarget(baseRectForWork.expand(2));
        }
        int outerPad = effectiveRadius + (isFirstPersonHandContext(context) ? FIRST_PERSON_EXTRA_SCISSOR_PAD : NON_FIRST_PERSON_EXTRA_SCISSOR_PAD);
        ScreenRect itemRect = clampToTarget(baseRectForWork.expand(outerPad));
        ScissorState capturedScissor = ScissorState.capture();
        ScreenRect effectiveRect = capturedScissor.intersect(itemRect);
        if (effectiveRect.isEmpty()) {
            captureActive = false;
            return;
        }
        int clearPad = isFirstPersonHandContext(context) ? (MAX_SEARCH_RADIUS + FIRST_PERSON_EXTRA_SCISSOR_PAD) : (effectiveRadius + NON_FIRST_PERSON_EXTRA_SCISSOR_PAD);
        ScreenRect clearRect = clampToTarget(itemRect.expand(clearPad));
        beginCapturePass(context, capturedScissor, effectiveRect, clearRect);
        CAPTURE_STATE.mergeDirtyRect(effectiveRect, effectiveRadius);
        int packedOverlay = pack2x16(data.red(), data.green());
        int packedLight = pack2x16(data.blue(), effectiveRadius);
        RenderType seedRenderType = ItemOutlineRenderTypes.itemMask();
        renderSeedPass(capturedScissor, effectiveRect, () -> {
            VertexConsumer seedConsumer = MASK_BUFFER_SOURCE.getBuffer(seedRenderType);
            renderer.renderModelLists(maskModel, stack, packedLight, packedOverlay, poseStack, seedConsumer);
            MASK_BUFFER_SOURCE.endBatch(seedRenderType);
        });
        CAPTURE_STATE.maskDirty = true;
        captureActive = false;
    }

    private static ScreenRect stabilizeFirstPersonWorkRect(ItemDisplayContext context, ScreenRect candidate, ItemStack stack, BakedModel model) {
        HandState hand = FIRST_PERSON_HANDS[firstPersonHandSlot(context)];
        int modelId = System.identityHashCode(model);
        int itemId = System.identityHashCode(stack.getItem());
        ScreenRect padded = clampToTarget(candidate.expand(FIRST_PERSON_STABILIZE_PAD));
        ScreenRect previous = hand.workRect;
        if (previous == null || previous.isEmpty() || hand.hasDifferentRectIdentity(modelId, itemId)) {
            hand.storeRect(padded, modelId, itemId);
            return padded;
        }
        ScreenRect stabilized = new ScreenRect(stabilizeMinEdge(previous.minX(), padded.minX()), stabilizeMinEdge(previous.minY(), padded.minY()), stabilizeMaxEdge(previous.maxX(), padded.maxX()), stabilizeMaxEdge(previous.maxY(), padded.maxY()));
        stabilized = clampToTarget(stabilized);
        hand.storeRect(stabilized, modelId, itemId);
        return stabilized;
    }

    private static ScreenRect projectItemBoundsToScreenTight(PoseStack poseStack, BakedModel model) {
        ensureTargets();
        updateCombinedMatrix(poseStack);
        ModelProjectionData projectionData = getModelProjectionData(model);
        if (!projectionData.isValid()) {
            return projectItemBoundsToScreenLegacy(poseStack);
        }
        FloatBounds ndcBounds = new FloatBounds();
        int count = accumulateProjectedModelBounds(projectionData, ndcBounds);
        if (count == 0 || ndcBounds.hasNonFinite()) {
            return emptyRect();
        }
        ndcBounds.clampToNdc();
        int width = outlineTarget.width;
        int height = outlineTarget.height;
        float minScreenX = (ndcBounds.minX * 0.5F + 0.5F) * width;
        float maxScreenX = (ndcBounds.maxX * 0.5F + 0.5F) * width;
        float minScreenY = (ndcBounds.minY * 0.5F + 0.5F) * height;
        float maxScreenY = (ndcBounds.maxY * 0.5F + 0.5F) * height;
        return toScreenRect(minScreenX, minScreenY, maxScreenX, maxScreenY, width, height);
    }

    private static ScreenRect projectItemBoundsToScreenLegacy(PoseStack poseStack) {
        ensureTargets();
        updateCombinedMatrix(poseStack);
        int width = outlineTarget.width;
        int height = outlineTarget.height;
        int index = 0;
        for (float x : BOX_CORNER_VALUES) {
            for (float y : BOX_CORNER_VALUES) {
                for (float z : BOX_CORNER_VALUES) {
                    storeTransformedCorner(index, x, y, z);
                    index++;
                }
            }
        }
        FloatBounds bounds = new FloatBounds();
        int projectedCount = accumulateLegacyProjectedBounds(bounds, width, height);
        if (projectedCount == 0 || bounds.hasNonFinite()) {
            return emptyRect();
        }
        return toScreenRect(bounds.minX, bounds.minY, bounds.maxX, bounds.maxY, width, height);
    }

    private static int accumulateProjectedModelBounds(ModelProjectionData projectionData, FloatBounds bounds) {
        List<ModelVertex> vertices = projectionData.vertices();
        int vertexCount = vertices.size();
        float[] clipX = new float[vertexCount];
        float[] clipY = new float[vertexCount];
        float[] clipZ = new float[vertexCount];
        float[] clipW = new float[vertexCount];
        int count = 0;
        for (int i = 0; i < vertexCount; i++) {
            ModelVertex vertex = vertices.get(i);
            SCRATCH_CLIP_VECTOR.set(vertex.x(), vertex.y(), vertex.z(), 1.0F);
            SCRATCH_COMBINED_MATRIX.transform(SCRATCH_CLIP_VECTOR);
            clipX[i] = SCRATCH_CLIP_VECTOR.x;
            clipY[i] = SCRATCH_CLIP_VECTOR.y;
            clipZ[i] = SCRATCH_CLIP_VECTOR.z;
            clipW[i] = SCRATCH_CLIP_VECTOR.w;
            if (hasNonFiniteClipVertex(clipX[i], clipY[i], clipZ[i], clipW[i]) || isOutsideClipVolumeXYZ(clipX[i], clipY[i], clipZ[i], clipW[i])) {
                continue;
            }
            bounds.include(clipX[i] / clipW[i], clipY[i] / clipW[i]);
            count++;
        }
        for (ModelEdge edge : projectionData.edges()) {
            int a = edge.a();
            int b = edge.b();
            count += accumulateClippedEdge(bounds, clipX[a], clipY[a], clipZ[a], clipW[a], clipX[b], clipY[b], clipZ[b], clipW[b], TIGHT_W_EPSILON);
        }
        return count;
    }

    private static int accumulateLegacyProjectedBounds(FloatBounds bounds, int width, int height) {
        int projectedCount = 0;
        for (int i = 0; i < 8; i++) {
            float clipX = SCRATCH_CLIP_X[i];
            float clipY = SCRATCH_CLIP_Y[i];
            float clipW = SCRATCH_CLIP_W[i];
            if (!Float.isFinite(clipX) || !Float.isFinite(clipY) || !Float.isFinite(clipW) || clipW <= CLIP_EPSILON) continue;
            float ndcX = clipX / clipW;
            float ndcY = clipY / clipW;
            if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) continue;
            bounds.include(ndcX, ndcY);
            projectedCount++;
        }
        for (int[] edge : BOX_EDGES) {
            int aIndex = edge[0];
            int bIndex = edge[1];
            projectedCount += accumulateClippedEdge(bounds, SCRATCH_CLIP_X[aIndex], SCRATCH_CLIP_Y[aIndex], SCRATCH_CLIP_Z[aIndex], SCRATCH_CLIP_W[aIndex], SCRATCH_CLIP_X[bIndex], SCRATCH_CLIP_Y[bIndex], SCRATCH_CLIP_Z[bIndex], SCRATCH_CLIP_W[bIndex], CLIP_EPSILON);
        }
        bounds.clampToNdc();
        bounds.scaleToScreen(width, height);
        return projectedCount;
    }

    private static void compositeRegions(Iterable<QueuedRegion> regions, boolean forceDepthOcclusion, RenderTarget sourceTarget) {
        ensureTargets();
        ShaderInstance shader = ItemOutlineRenderTypes.compositeShader();
        if (shader == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        mainTarget.bindWrite(false);
        cacheCompositeUniforms(shader);
        bindCompositeSamplers(shader, mainTarget, sourceTarget);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (cachedEnableDepthOcclusionUniform != null && forceDepthOcclusion && isDepthOcclusionSafe()) {
            cachedEnableDepthOcclusionUniform.set(1.0F);
        }
        if (cachedUseFirstPersonHandFastPathUniform != null && forceDepthOcclusion) {
            cachedUseFirstPersonHandFastPathUniform.set(0.0F);
        }
        ScissorState capturedScissor = ScissorState.capture();
        try {
            for (QueuedRegion region : regions) {
                if (cachedMaxSearchRadiusUniform != null) {
                    cachedMaxSearchRadiusUniform.set((float) Mth.clamp(region.maxRadius(), 1, MAX_SEARCH_RADIUS));
                }
                runWithScissor(capturedScissor, region.rect(), () -> drawCompositeRegion(shader));
            }
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.disableBlend();
        }
    }

    private static int stabilizeFirstPersonRadius(ItemDisplayContext context, ItemStack stack, BakedModel model, int computedRadius) {
        HandState hand = FIRST_PERSON_HANDS[firstPersonHandSlot(context)];
        int modelId = System.identityHashCode(model);
        int itemId = System.identityHashCode(stack.getItem());
        if (hand.hasDifferentRadiusIdentity(modelId, itemId)) {
            hand.storeRadius(computedRadius, modelId, itemId);
            return computedRadius;
        }

        if (Math.abs(computedRadius - hand.radius) <= 1) {
            computedRadius = hand.radius;
        }

        hand.storeRadius(computedRadius, modelId, itemId);
        return computedRadius;
    }

    public static void prepareCapture(ItemDisplayContext context) {
        CAPTURE_STATE.firstPersonHandFastPath = false;
        currentCaptureUsesTransientTarget = false;
        if (isGuiEntityPreviewBatchContext(context)) {
            if (!guiEntityBatchActive) {
                guiEntityBatchActive = true;
                CAPTURE_STATE.reset();
            }
            captureActive = true;
            CAPTURE_STATE.needsDepth = true;
            return;
        }
        if (shouldUseWorldBatch(context)) {
            if (!worldBatchActive) {
                worldBatchActive = true;
                CAPTURE_STATE.reset();
            }
            captureActive = true;
            CAPTURE_STATE.needsDepth = true;
            return;
        }
        if (worldBatchActive) {
            suspendWorldBatchForTransientCapture();
        }
        currentCaptureUsesTransientTarget = worldBatchSuspended || ItemShaderModCompat.shouldDeferFirstPersonOutlineComposite(context);
        captureActive = true;
        CAPTURE_STATE.reset();
        CAPTURE_STATE.needsDepth = needsMainDepth(context);
    }

    public static void compositeWorldMaskIfActive() {
        if (!worldBatchActive) return;
        if (CAPTURE_STATE.maskDirty && CAPTURE_STATE.hasDirtyRect()) queueCurrentDirtyRegion();
        compositeQueuedRegions();
        worldBatchActive = false;
    }

    private static void compositeCurrentDirtyRegion() {
        if (!CAPTURE_STATE.maskDirty || !CAPTURE_STATE.hasDirtyRect()) {
            resetCaptureState();
            return;
        }
        compositeRegions(Collections.singletonList(new QueuedRegion(CAPTURE_STATE.dirtyRect(), CAPTURE_STATE.dirtyMaxRadius)), false, currentCaptureTarget());
        resetCaptureState();
    }

    private static void compositeQueuedRegions() {
        if (QUEUED_REGIONS.isEmpty()) {
            resetCaptureState();
            return;
        }
        compositeRegions(QUEUED_REGIONS, true, outlineTarget);
        QUEUED_REGIONS.clear();
        resetCaptureState();
    }

    private static void queueDeferredFirstPersonRegion() {
        if (!CAPTURE_STATE.maskDirty || !CAPTURE_STATE.hasDirtyRect()) {
            resetCaptureState();
            return;
        }
        queueRegion(currentCaptureUsesTransientTarget ? DEFERRED_FIRST_PERSON_TRANSIENT_REGIONS : DEFERRED_FIRST_PERSON_REGIONS, CAPTURE_STATE.dirtyRect(), CAPTURE_STATE.dirtyMaxRadius);
        resetCaptureState();
    }

    private static void beginCapturePass(ItemDisplayContext context, ScissorState capturedScissor, ScreenRect effectiveRect, ScreenRect clearRect) {
        if (isGuiEntityPreviewBatchContext(context)) {
            initializeGuiEntityBatchIfNeeded(capturedScissor);
            return;
        }
        if (shouldUseWorldBatch(context)) {
            flushWorldBatchIfNeeded(effectiveRect);
            initializeWorldBatchIfNeeded(capturedScissor);
            return;
        }
        initializeImmediateCapture();
        clearTargetRect(currentCaptureTarget(), clearRect, !CAPTURE_STATE.needsDepth, capturedScissor);
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
        if (cachedAlphaThresholdUniform != null) {
            cachedAlphaThresholdUniform.set(ALPHA_THRESHOLD);
        }
        if (cachedMaxSearchRadiusUniform != null) {
            cachedMaxSearchRadiusUniform.set((float) Mth.clamp(CAPTURE_STATE.dirtyMaxRadius, 1, MAX_SEARCH_RADIUS));
        }
        if (cachedDepthEpsilonUniform != null) {
            cachedDepthEpsilonUniform.set(DEPTH_EPSILON);
        }
        if (cachedEnableDepthOcclusionUniform != null) {
            float enabled = isDepthOcclusionSafe() && (worldBatchActive || CAPTURE_STATE.needsDepth) ? 1.0F : 0.0F;
            cachedEnableDepthOcclusionUniform.set(enabled);
        }
        if (cachedUseFirstPersonHandFastPathUniform != null) {
            cachedUseFirstPersonHandFastPathUniform.set(CAPTURE_STATE.firstPersonHandFastPath ? 1.0F : 0.0F);
        }
    }

    private static void bindCompositeSamplers(ShaderInstance shader, RenderTarget mainTarget, RenderTarget sourceTarget) {
        RenderSystem.setShader(() -> shader);
        int outlineColor = safeColorTextureId(sourceTarget);
        int outlineDepth = safeDepthTextureId(sourceTarget, outlineColor);
        int mainDepth = isDepthOcclusionSafe() ? safeDepthTextureId(mainTarget, outlineDepth) : outlineDepth;
        shader.setSampler("OutlineSampler", outlineColor);
        shader.setSampler("OutlineDepthSampler", outlineDepth);
        shader.setSampler("MainDepthSampler", mainDepth);
    }

    private static void initializeWorldBatchIfNeeded(ScissorState capturedScissor) {
        if (CAPTURE_STATE.initialized) return;
        ensureTargets();
        if (isDepthOcclusionSafe()) copyDepthFromMainTarget(outlineTarget);
        clearTargetFullColor(outlineTarget, capturedScissor);
        CAPTURE_STATE.initialized = true;
    }

    private static void initializeImmediateCapture() {
        if (CAPTURE_STATE.initialized) return;
        ensureTargets();
        RenderTarget target = currentCaptureTarget();
        if (CAPTURE_STATE.needsDepth && isDepthOcclusionSafe()) copyDepthFromMainTarget(target);
        CAPTURE_STATE.initialized = true;
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

    private static void clearTargetFullColor(RenderTarget target, ScissorState previousScissor) {
        clearTargetFull(target, previousScissor, false);
    }

    private static void clearTargetFull(RenderTarget target, ScissorState previousScissor, boolean clearDepth) {
        target.bindWrite(false);
        RenderSystem.disableScissor();
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        int clearMask = GL11.GL_COLOR_BUFFER_BIT;
        if (clearDepth) {
            RenderSystem.clearDepth(1.0D);
            clearMask |= GL11.GL_DEPTH_BUFFER_BIT;
        }
        RenderSystem.clear(clearMask, Minecraft.ON_OSX);
        restoreMainTarget();
        previousScissor.restore();
    }

    private static void clearTargetColor(@Nullable RenderTarget target) {
        if (target == null) {
            return;
        }
        ScissorState previousScissor = ScissorState.capture();
        target.bindWrite(false);
        RenderSystem.disableScissor();
        RenderSystem.clearColor(0.0F, 0.0F, 0.0F, 0.0F);
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, Minecraft.ON_OSX);
        restoreMainTargetSafe();
        previousScissor.restore();
    }

    private static void flushWorldBatchIfNeeded(ScreenRect nextRect) {
        if (!worldBatchActive || !CAPTURE_STATE.initialized || !CAPTURE_STATE.maskDirty || !CAPTURE_STATE.hasDirtyRect()) {
            return;
        }
        ScreenRect current = CAPTURE_STATE.dirtyRect();
        if (current.expand(MAX_SEARCH_RADIUS).intersects(nextRect.expand(MAX_SEARCH_RADIUS))) {
            return;
        }
        queueCurrentDirtyRegion();
        CAPTURE_STATE.resetDirtyRect();
        CAPTURE_STATE.dirtyMaxRadius = 1;
    }

    private static void queueCurrentDirtyRegion() {
        if (!CAPTURE_STATE.hasDirtyRect()) {
            return;
        }
        queueRegion(QUEUED_REGIONS, CAPTURE_STATE.dirtyRect(), CAPTURE_STATE.dirtyMaxRadius);
    }

    private static void initializeGuiEntityBatchIfNeeded(ScissorState capturedScissor) {
        if (CAPTURE_STATE.initialized) return;
        ensureTargets();
        clearTargetFull(outlineTarget, capturedScissor, true);
        CAPTURE_STATE.initialized = true;
        CAPTURE_STATE.needsDepth = true;
    }

    private static void drawCompositeRegion(ShaderInstance shader) {
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        builder.vertex(1.0D, -1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        builder.vertex(1.0D, 1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        builder.vertex(-1.0D, 1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }

    public static void compositeItemMaskIfActive(ItemDisplayContext context) {
        try {
            if (worldBatchActive) {
                return;
            }
            if (guiEntityBatchActive) {
                return;
            }
            if (ItemShaderModCompat.shouldDeferFirstPersonOutlineComposite(context)) {
                queueDeferredFirstPersonRegion();
                return;
            }
            compositeCurrentDirtyRegion();
        } finally {
            if (!shouldUseWorldBatch(context)) {
                resumeWorldBatchAfterTransientCaptureIfNeeded();
            }
        }
    }

    public static void compositeDeferredFirstPersonIfActive() {
        if (!DEFERRED_FIRST_PERSON_REGIONS.isEmpty()) {
            compositeRegions(DEFERRED_FIRST_PERSON_REGIONS, false, outlineTarget);
            DEFERRED_FIRST_PERSON_REGIONS.clear();
            clearTargetColor(outlineTarget);
        }
        if (!DEFERRED_FIRST_PERSON_TRANSIENT_REGIONS.isEmpty()) {
            compositeRegions(DEFERRED_FIRST_PERSON_TRANSIENT_REGIONS, false, transientOutlineTarget);
            DEFERRED_FIRST_PERSON_TRANSIENT_REGIONS.clear();
            clearTargetColor(transientOutlineTarget);
        }
    }

    private static int includeClippedEndpoint(FloatBounds bounds, float ax, float ay, float az, float aw, float dx, float dy, float dz, float dw, float t) {
        float x = ax + dx * t;
        float y = ay + dy * t;
        float z = az + dz * t;
        float w = aw + dw * t;
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w)) {
            return 0;
        }
        if (isOutsideClipVolumeXYZ(x, y, z, w)) {
            return 0;
        }
        bounds.include(x / w, y / w);
        return 1;
    }

    private static int accumulateClippedEdge(FloatBounds bounds, float ax, float ay, float az, float aw, float bx, float by, float bz, float bw, float minW) {
        if (hasNonFiniteClipVertex(ax, ay, az, aw) || hasNonFiniteClipVertex(bx, by, bz, bw)) {
            return 0;
        }
        ClipInterval interval = SCRATCH_INTERVAL;
        interval.t0 = 0.0F;
        interval.t1 = 1.0F;
        if (rejectByClipVolume(ax, ay, az, aw, bx, by, bz, bw, minW, interval) || interval.t1 <= interval.t0) {
            return 0;
        }
        float dx = bx - ax;
        float dy = by - ay;
        float dz = bz - az;
        float dw = bw - aw;
        return includeClippedEndpoint(bounds, ax, ay, az, aw, dx, dy, dz, dw, interval.t0) + includeClippedEndpoint(bounds, ax, ay, az, aw, dx, dy, dz, dw, interval.t1);
    }

    private static void copyDepthFromMainTarget(RenderTarget target) {
        if (!isDepthOcclusionSafe()) return;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            target.copyDepthFrom(minecraft.getMainRenderTarget());
        } catch (Throwable throwable) {
            depthCopyUnavailable = true;
        }
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

    private static void queueRegion(ArrayList<QueuedRegion> queue, ScreenRect rect, int maxRadius) {
        if (queue.size() >= MAX_QUEUED_REGIONS) {
            int last = queue.size() - 1;
            QueuedRegion previous = queue.get(last);
            queue.set(last, new QueuedRegion(union(previous.rect(), rect), Math.max(previous.maxRadius(), maxRadius)));
            return;
        }
        queue.add(new QueuedRegion(rect, maxRadius));
    }

    private static ScreenRect clampToTarget(ScreenRect rect) {
        ensureTargets();
        return new ScreenRect(Mth.clamp(rect.minX(), 0, outlineTarget.width), Mth.clamp(rect.minY(), 0, outlineTarget.height), Mth.clamp(rect.maxX(), 0, outlineTarget.width), Mth.clamp(rect.maxY(), 0, outlineTarget.height));
    }
    private static void resetFirstPersonHandCache() { for (HandState hand : FIRST_PERSON_HANDS) hand.reset(); }

    private static void resetCaptureState() {
        captureActive = false;
        currentCaptureUsesTransientTarget = false;
        CAPTURE_STATE.reset();
    }

    private static void suspendWorldBatchForTransientCapture() {
        if (worldBatchSuspended || !worldBatchActive) return;
        worldBatchSuspended = true;
        SUSPENDED_CAPTURE_STATE.copyFrom(CAPTURE_STATE);
        worldBatchActive = false;
        CAPTURE_STATE.reset();
    }

    private static void resumeWorldBatchAfterTransientCaptureIfNeeded() {
        if (!worldBatchSuspended) return;
        worldBatchSuspended = false;
        worldBatchActive = true;
        CAPTURE_STATE.copyFrom(SUSPENDED_CAPTURE_STATE);
    }

    private static void clearSuspendedWorldBatchState() {
        worldBatchSuspended = false;
        SUSPENDED_CAPTURE_STATE.reset();
    }

    private static void ensureTargets() {
        Minecraft minecraft = Minecraft.getInstance();
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (outlineTarget == null) {
            outlineTarget = new TextureTarget(mainTarget.width, mainTarget.height, true, Minecraft.ON_OSX);
            configureTarget(outlineTarget);
        }
        if (transientOutlineTarget == null) {
            transientOutlineTarget = new TextureTarget(mainTarget.width, mainTarget.height, true, Minecraft.ON_OSX);
            configureTarget(transientOutlineTarget);
        }
        if (outlineTarget.width != mainTarget.width || outlineTarget.height != mainTarget.height) {
            outlineTarget.resize(mainTarget.width, mainTarget.height, Minecraft.ON_OSX);
            configureTarget(outlineTarget);
            transientOutlineTarget.resize(mainTarget.width, mainTarget.height, Minecraft.ON_OSX);
            configureTarget(transientOutlineTarget);
            resetFirstPersonHandCache();
            DEFERRED_FIRST_PERSON_REGIONS.clear();
            DEFERRED_FIRST_PERSON_TRANSIENT_REGIONS.clear();
            resetCaptureState();
            clearSuspendedWorldBatchState();
            guiEntityBatchActive = false;
        }
    }
    private static ModelBounds getModelBounds(BakedModel model) { return MODEL_BOUNDS.computeIfAbsent(model, ItemOutlinePostProcessor::computeModelBounds); }

    private static ModelBounds computeModelBounds(BakedModel model) {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        boolean any = false;
        for (int pass = -1; pass < DIRECTIONS.length; pass++) {
            Direction side = pass < 0 ? null : DIRECTIONS[pass];
            BOUNDS_RAND.setSeed(0L);
            for (BakedQuad quad : model.getQuads(null, side, BOUNDS_RAND)) {
                int[] vertices = quad.getVertices();
                if (vertices.length < 12) {
                    continue;
                }
                int stride = vertices.length / 4;
                for (int i = 0; i < 4; i++) {
                    int base = i * stride;
                    float x = Float.intBitsToFloat(vertices[base]);
                    float y = Float.intBitsToFloat(vertices[base + 1]);
                    float z = Float.intBitsToFloat(vertices[base + 2]);
                    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                        continue;
                    }
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    minZ = Math.min(minZ, z);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    maxZ = Math.max(maxZ, z);
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
    private static ModelProjectionData getModelProjectionData(BakedModel model) { return MODEL_PROJECTION_DATA.computeIfAbsent(model, ItemOutlinePostProcessor::computeModelProjectionData); }

    private static ModelProjectionData computeModelProjectionData(BakedModel model) {
        ArrayList<ModelVertex> vertices = new ArrayList<>();
        ArrayList<ModelEdge> edges = new ArrayList<>();
        for (int pass = -1; pass < DIRECTIONS.length; pass++) {
            Direction side = pass < 0 ? null : DIRECTIONS[pass];
            BOUNDS_RAND.setSeed(0L);
            for (BakedQuad quad : model.getQuads(null, side, BOUNDS_RAND)) {
                int[] quadVertices = quad.getVertices();
                if (quadVertices.length < 12) {
                    continue;
                }
                int stride = quadVertices.length / 4;
                int firstIndex = vertices.size();
                boolean validQuad = true;
                for (int i = 0; i < 4; i++) {
                    int base = i * stride;
                    float x = Float.intBitsToFloat(quadVertices[base]);
                    float y = Float.intBitsToFloat(quadVertices[base + 1]);
                    float z = Float.intBitsToFloat(quadVertices[base + 2]);
                    if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)) {
                        validQuad = false;
                        break;
                    }
                    vertices.add(new ModelVertex(x, y, z));
                }
                if (!validQuad) {
                    while (vertices.size() > firstIndex) {
                        vertices.remove(vertices.size() - 1);
                    }
                    continue;
                }
                edges.add(new ModelEdge(firstIndex, firstIndex + 1));
                edges.add(new ModelEdge(firstIndex + 1, firstIndex + 2));
                edges.add(new ModelEdge(firstIndex + 2, firstIndex + 3));
                edges.add(new ModelEdge(firstIndex + 3, firstIndex));
            }
        }
        if (vertices.isEmpty() || edges.isEmpty()) {
            ModelBounds bounds = getModelBounds(model);
            ArrayList<ModelVertex> fallbackVertices = new ArrayList<>(8);
            fallbackVertices.add(new ModelVertex(bounds.minX(), bounds.minY(), bounds.minZ()));
            fallbackVertices.add(new ModelVertex(bounds.minX(), bounds.minY(), bounds.maxZ()));
            fallbackVertices.add(new ModelVertex(bounds.minX(), bounds.maxY(), bounds.minZ()));
            fallbackVertices.add(new ModelVertex(bounds.minX(), bounds.maxY(), bounds.maxZ()));
            fallbackVertices.add(new ModelVertex(bounds.maxX(), bounds.minY(), bounds.minZ()));
            fallbackVertices.add(new ModelVertex(bounds.maxX(), bounds.minY(), bounds.maxZ()));
            fallbackVertices.add(new ModelVertex(bounds.maxX(), bounds.maxY(), bounds.minZ()));
            fallbackVertices.add(new ModelVertex(bounds.maxX(), bounds.maxY(), bounds.maxZ()));
            ArrayList<ModelEdge> fallbackEdges = new ArrayList<>(BOX_EDGES.length);
            for (int[] edge : BOX_EDGES) {
                fallbackEdges.add(new ModelEdge(edge[0], edge[1]));
            }
            return new ModelProjectionData(Collections.unmodifiableList(fallbackVertices), Collections.unmodifiableList(fallbackEdges));
        }
        return new ModelProjectionData(Collections.unmodifiableList(vertices), Collections.unmodifiableList(edges));
    }

    @Nullable
    private static ScreenRect getPreviousFirstPersonWorkRect(ItemDisplayContext context, ItemStack stack, BakedModel model) {
        if (!isFirstPersonHandContext(context)) return null;
        HandState hand = FIRST_PERSON_HANDS[firstPersonHandSlot(context)];
        long rectAgeNanos = System.nanoTime() - hand.rectUpdateNanos;
        if (rectAgeNanos > FIRST_PERSON_PREVIOUS_RECT_FALLBACK_NS) return null;
        ScreenRect previous = hand.workRect;
        if (previous == null || previous.isEmpty()) return null;
        if (hand.hasDifferentRectIdentity(System.identityHashCode(model), System.identityHashCode(stack.getItem()))) return null;
        return previous;
    }
    private static void updateCombinedMatrix(PoseStack poseStack) { SCRATCH_COMBINED_MATRIX.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose()); }

    private static BakedModel resolveMaskModel(ItemStack stack, BakedModel model, ItemDisplayContext context) {
        return stack.getItem() instanceof BlockItem || context != ItemDisplayContext.GUI ? model : FLAT_MASK_MODELS.computeIfAbsent(model, FlatItemMaskModel::new);
    }
    private static int resolveEffectiveRadius(ItemOutlineData data) { return Mth.clamp(data.radiusPixels(), 1, MAX_SEARCH_RADIUS); }

    private static void renderSeedPass(ScissorState capturedScissor, ScreenRect effectiveRect, Runnable action) {
        bindOutlineTargetSafe();
        try {
            runWithScissor(capturedScissor, effectiveRect, action);
        } finally {
            restoreMainTargetSafe();
        }
    }
    private static RenderTarget currentCaptureTarget() { ensureTargets(); return currentCaptureUsesTransientTarget ? transientOutlineTarget : outlineTarget; }
    private static void bindOutlineTargetSafe() { currentCaptureTarget().bindWrite(false); }
    private static void restoreMainTargetSafe() { Minecraft.getInstance().getMainRenderTarget().bindWrite(false); }
    private static boolean isDepthOcclusionSafe() { return !depthCopyUnavailable; }
    private static int safeColorTextureId(RenderTarget target) { return Math.max(target.getColorTextureId(), 0); }

    private static int safeDepthTextureId(RenderTarget target, int fallback) {
        try {
            int depth = target.getDepthTextureId();
            if (depth > 0) {
                return depth;
            }
        } catch (Throwable throwable) {
            depthCopyUnavailable = true;
        }
        return fallback;
    }
    static void bindOutlineTarget() { bindOutlineTargetSafe(); }
    static void restoreMainTarget() { restoreMainTargetSafe(); }
    public static boolean shouldDeferComposite(ItemDisplayContext context) {
        return (worldBatchActive && shouldUseWorldBatch(context)) || (guiEntityBatchActive && isGuiEntityPreviewBatchContext(context));
    }

    public static void beginGuiEntityPreview() {
        guiEntityPreviewDepth++;
    }

    public static void endGuiEntityPreview() {
        if (guiEntityPreviewDepth <= 0) {
            return;
        }

        try {
            if (guiEntityPreviewDepth == 1) {
                compositeGuiEntityPreviewIfActive();
            }
        } finally {
            guiEntityPreviewDepth--;
        }
    }

    private static void compositeGuiEntityPreviewIfActive() {
        if (!guiEntityBatchActive) {
            return;
        }

        try {
            if (CAPTURE_STATE.maskDirty && CAPTURE_STATE.hasDirtyRect()) {
                compositeRegions(Collections.singletonList(new QueuedRegion(CAPTURE_STATE.dirtyRect(), CAPTURE_STATE.dirtyMaxRadius)), true, outlineTarget);
                clearTargetColor(outlineTarget);
            }
        } finally {
            guiEntityBatchActive = false;
            resetCaptureState();
        }
    }

    private static ScreenRect union(ScreenRect a, ScreenRect b) {
        return new ScreenRect(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()));
    }

    private static void storeTransformedCorner(int index, float x, float y, float z) {
        SCRATCH_CLIP_VECTOR.set(x, y, z, 1.0F);
        SCRATCH_COMBINED_MATRIX.transform(SCRATCH_CLIP_VECTOR);
        SCRATCH_CLIP_X[index] = SCRATCH_CLIP_VECTOR.x;
        SCRATCH_CLIP_Y[index] = SCRATCH_CLIP_VECTOR.y;
        SCRATCH_CLIP_Z[index] = SCRATCH_CLIP_VECTOR.z;
        SCRATCH_CLIP_W[index] = SCRATCH_CLIP_VECTOR.w;
    }

    private static ScreenRect toScreenRect(float minScreenX, float minScreenY, float maxScreenX, float maxScreenY, int width, int height) {
        if (!Float.isFinite(minScreenX) || !Float.isFinite(minScreenY) || !Float.isFinite(maxScreenX) || !Float.isFinite(maxScreenY)) {
            return emptyRect();
        }
        int x0 = Mth.clamp((int) Math.floor(minScreenX), 0, width);
        int y0 = Mth.clamp((int) Math.floor(minScreenY), 0, height);
        int x1 = Mth.clamp((int) Math.ceil(maxScreenX), 0, width);
        int y1 = Mth.clamp((int) Math.ceil(maxScreenY), 0, height);
        if (x1 <= x0 || y1 <= y0) {
            return emptyRect();
        }
        return new ScreenRect(x0, y0, x1, y1);
    }

    private static void configureTarget(RenderTarget target) {
        target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        target.setFilterMode(GL11.GL_NEAREST);
    }

    private static boolean rejectByClipPlane(float f0, float f1, ClipInterval interval) {
        if (f0 >= 0.0F && f1 >= 0.0F) return false;
        if (f0 < 0.0F && f1 < 0.0F) return true;

        float denominator = f0 - f1;
        if (!Float.isFinite(denominator) || Math.abs(denominator) < 1.0e-20F) return true;

        float t = f0 / denominator;
        if (!Float.isFinite(t)) return true;

        if (f0 < 0.0F) {
            interval.t0 = Math.max(interval.t0, t);
        } else {
            interval.t1 = Math.min(interval.t1, t);
        }

        return interval.t0 > interval.t1;
    }

    private static boolean rejectByClipVolume(float ax, float ay, float az, float aw, float bx, float by, float bz, float bw, float minW, ClipInterval interval) {
        return rejectByClipPlane(ax + aw, bx + bw, interval) || rejectByClipPlane(-ax + aw, -bx + bw, interval) || rejectByClipPlane(ay + aw, by + bw, interval) || rejectByClipPlane(-ay + aw, -by + bw, interval) || rejectByClipPlane(az + aw, bz + bw, interval) || rejectByClipPlane(-az + aw, -bz + bw, interval) || rejectByClipPlane(aw - minW, bw - minW, interval);
    }

    private static boolean isOutsideClipVolumeXYZ(float x, float y, float z, float w) {
        return w <= TIGHT_W_EPSILON || x < -w || x > w || y < -w || y > w || z < -w || z > w;
    }

    private static boolean hasNonFiniteClipVertex(float x, float y, float z, float w) {
        return !Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(w);
    }

    private static int stabilizeMinEdge(int previous, int current) { return current <= previous ? current : Math.min(current, previous + FIRST_PERSON_MAX_SHRINK_PER_FRAME); }

    private static int stabilizeMaxEdge(int previous, int current) { return current >= previous ? current : Math.max(current, previous - FIRST_PERSON_MAX_SHRINK_PER_FRAME); }

    private static int firstPersonHandSlot(ItemDisplayContext context) { return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ? 0 : 1; }

    private static boolean isWorldBatchedContext(ItemDisplayContext context) {
        return switch (context) {
            case GROUND, FIXED, THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, HEAD -> true;
            default -> false;
        };
    }

    private static boolean shouldUseWorldBatch(ItemDisplayContext context) {
        return guiEntityPreviewDepth <= 0 && isWorldBatchedContext(context);
    }

    private static boolean isGuiEntityPreviewBatchContext(ItemDisplayContext context) {
        return guiEntityPreviewDepth > 0 && isWorldBatchedContext(context);
    }

    private static boolean isFirstPersonHandContext(ItemDisplayContext context) { return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND; }

    private static boolean needsMainDepth(ItemDisplayContext context) {
        return shouldUseWorldBatch(context);
    }

    private static int pack2x16(int lo, int hi) { return (lo & 0xFFFF) | ((hi & 0xFFFF) << 16); }

    private static ScreenRect emptyRect() { return new ScreenRect(0, 0, 0, 0); }

    private static final class FloatBounds {
        float minX = Float.POSITIVE_INFINITY;
        float minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY;
        float maxY = Float.NEGATIVE_INFINITY;

        void include(float x, float y) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        boolean hasNonFinite() {
            return !Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(maxX) || !Float.isFinite(maxY);
        }

        void clampToNdc() {
            minX = Mth.clamp(minX, -1.0F, 1.0F);
            minY = Mth.clamp(minY, -1.0F, 1.0F);
            maxX = Mth.clamp(maxX, -1.0F, 1.0F);
            maxY = Mth.clamp(maxY, -1.0F, 1.0F);
        }

        void scaleToScreen(int width, int height) {
            minX = (minX * 0.5F + 0.5F) * width;
            minY = (minY * 0.5F + 0.5F) * height;
            maxX = (maxX * 0.5F + 0.5F) * width;
            maxY = (maxY * 0.5F + 0.5F) * height;
        }
    }
    private ItemOutlinePostProcessor() {}
}
