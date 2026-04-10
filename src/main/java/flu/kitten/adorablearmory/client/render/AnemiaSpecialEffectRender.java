package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.entity.effect.AnemiaSpecialEffect;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class AnemiaSpecialEffectRender extends EntityRenderer<AnemiaSpecialEffect> {

    public static AnemiaSpecialEffectRender effectRender;
    private static final ResourceLocation PATTERN = new ResourceLocation(AdorableArmory.MODID, "textures/entity/true_demon_pattern.png");
    private static final float[] LAYER_OPACITIES = {0.80f, 0.60f, 0.40f, 0.20f};
    private static final float SMOOTH_TRANSITION_START = 0.010f; // 淡入距离
    private static final float SMOOTH_TRANSITION_END = 3.24f; // 透明距离
    private static final int SUBDIVISION_LEVEL_FADE = 50; // 细分等级 该值越高 面细分越多
    private static final boolean USE_FACE_SUBDIVISION = true; // 启用或关闭面细分
    protected static boolean ENABLE_DISTANCE_FADE = true;
    private static final Map<ResourceLocation, RenderType> GLOW_RENDER_TYPES = new HashMap<>();
    private static final int MAX_SUBDIVIDE_CACHE = 80;
    private float[] gridX, gridY, gridZ;
    private int[] gridColor;
    private float[] edge0x, edge0y, edge0z;
    private float[] edge1x, edge1y, edge1z;
    private int cachedN = -1;
    private int cachedStride = 0;
    private static final int FBM_OCT = 4;
    private static final float FBM_GAIN = 0.32f;
    private static final float FBM_AMP0 = 0.5f;
    private static final float FBM_NORM = (float)(FBM_AMP0 * (1.0 - Math.pow(FBM_GAIN, FBM_OCT)) / (1.0 - FBM_GAIN));
    private static final float FBM_INV_NORM = 1.0f / FBM_NORM;

    private void ensureGridCapacity(int capacity) {
        if (capacity > MAX_SUBDIVIDE_CACHE) {
            throw new IllegalArgumentException("subdivision " + capacity + " exceeds MAX_SUBDIVIDE_CACHE " + MAX_SUBDIVIDE_CACHE);
        }
        if (cachedN == capacity && gridX != null) return;
        int stride = capacity + 1;
        int cap = stride * stride;
        gridX = new float[cap];
        gridY = new float[cap];
        gridZ = new float[cap];
        gridColor = new int[cap];
        edge0x = new float[stride];
        edge0y = new float[stride];
        edge0z = new float[stride];
        edge1x = new float[stride];
        edge1y = new float[stride];
        edge1z = new float[stride];
        cachedN = capacity;
        cachedStride = stride;
    }

    private static RenderType getGlowType(ResourceLocation texture) {
        return GLOW_RENDER_TYPES.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(AdorableArmoryShaders.RenderStateShardAccess.RENDERER_ENTITY_TRANSLUCENT)
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                    .setTransparencyState(AdorableArmoryShaders.RenderStateShardAccess.ADDITIVE_TRANSPARENCY)
                    .setOverlayState(AdorableArmoryShaders.RenderStateShardAccess.OVERLAY)
                    .setLightmapState(AdorableArmoryShaders.RenderStateShardAccess.LIGHT_MAP)
                    .setWriteMaskState(AdorableArmoryShaders.RenderStateShardAccess.COLOR_WRITE)
                    .setCullState(AdorableArmoryShaders.RenderStateShardAccess.NO_CULL)
                    .createCompositeState(false);
            return RenderType.create("textured_glow",
                    DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256,
                    false, true, state);
        });
    }

    public AnemiaSpecialEffectRender(EntityRendererProvider.Context context) {
        super(context);
        effectRender = this;
        this.shadowRadius = 0;
    }

    @Override
    public void render(@NotNull AnemiaSpecialEffect entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        if (entity.isMagnetized()) {
            applyMagnetizationTransforms(poseStack, entity, partialTicks);
        }

        renderRainbowCube(poseStack, bufferSource, entity, partialTicks, packedLight);

        //renderLayerCubes(poseStack, bufferSource, entity, partialTicks, packedLight);

        /*try {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.5f, 0.5f);
            float s = 1.0426427f;
            poseStack.scale(s, s, s);
            poseStack.translate(-0.5f, -0.5f, -0.5f);
            renderTexturedCube(poseStack, bufferSource, entity, PATTERN, partialTicks);
        } finally {
            poseStack.popPose();
        }*/

        poseStack.popPose();
    }

    private void renderTexturedCube(PoseStack poseStack, MultiBufferSource bufferSource, AnemiaSpecialEffect entity, ResourceLocation texture, float partialTicks) {
        RenderType renderType = getGlowType(texture);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);
        AABB boundingBox = entity.getBoundingBox();
        Matrix4f matrix = poseStack.last().pose();
        float time = (entity.tickCount + partialTicks) * 0.0246f;
        int light = 15728880;
        int seed = 32;

        float minX = (float) (boundingBox.minX - entity.getX());
        float minY = (float) (boundingBox.minY - entity.getY());
        float minZ = (float) (boundingBox.minZ - entity.getZ());
        float maxX = (float) (boundingBox.maxX - entity.getX());
        float maxY = (float) (boundingBox.maxY - entity.getY());
        float maxZ = (float) (boundingBox.maxZ - entity.getZ());

        emit(consumer, matrix, minX, minY, minZ, get3DRainbowColor(time, minX, minY, minZ, seed), 0, 1, light);
        emit(consumer, matrix, maxX, minY, minZ, get3DRainbowColor(time, maxX, minY, minZ, seed), 1, 1, light);
        emit(consumer, matrix, maxX, minY, maxZ, get3DRainbowColor(time, maxX, minY, maxZ, seed), 1, 0, light);
        emit(consumer, matrix, minX, minY, maxZ, get3DRainbowColor(time, minX, minY, maxZ, seed), 0, 0, light);

        emit(consumer, matrix, minX, maxY, maxZ, get3DRainbowColor(time, minX, maxY, maxZ, seed), 0, 1, light);
        emit(consumer, matrix, maxX, maxY, maxZ, get3DRainbowColor(time, maxX, maxY, maxZ, seed), 1, 1, light);
        emit(consumer, matrix, maxX, maxY, minZ, get3DRainbowColor(time, maxX, maxY, minZ, seed), 1, 0, light);
        emit(consumer, matrix, minX, maxY, minZ, get3DRainbowColor(time, minX, maxY, minZ, seed), 0, 0, light);

        emit(consumer, matrix, minX, minY, minZ, get3DRainbowColor(time, minX, minY, minZ, seed), 1, 1, light);
        emit(consumer, matrix, minX, maxY, minZ, get3DRainbowColor(time, minX, maxY, minZ, seed), 1, 0, light);
        emit(consumer, matrix, maxX, maxY, minZ, get3DRainbowColor(time, maxX, maxY, minZ, seed), 0, 0, light);
        emit(consumer, matrix, maxX, minY, minZ, get3DRainbowColor(time, maxX, minY, minZ, seed), 0, 1, light);

        emit(consumer, matrix, maxX, minY, maxZ, get3DRainbowColor(time, maxX, minY, maxZ, seed), 1, 1, light);
        emit(consumer, matrix, maxX, maxY, maxZ, get3DRainbowColor(time, maxX, maxY, maxZ, seed), 1, 0, light);
        emit(consumer, matrix, minX, maxY, maxZ, get3DRainbowColor(time, minX, maxY, maxZ, seed), 0, 0, light);
        emit(consumer, matrix, minX, minY, maxZ, get3DRainbowColor(time, minX, minY, maxZ, seed), 0, 1, light);

        emit(consumer, matrix, minX, minY, maxZ, get3DRainbowColor(time, minX, minY, maxZ, seed), 1, 1, light);
        emit(consumer, matrix, minX, maxY, maxZ, get3DRainbowColor(time, minX, maxY, maxZ, seed), 1, 0, light);
        emit(consumer, matrix, minX, maxY, minZ, get3DRainbowColor(time, minX, maxY, minZ, seed), 0, 0, light);
        emit(consumer, matrix, minX, minY, minZ, get3DRainbowColor(time, minX, minY, minZ, seed), 0, 1, light);

        emit(consumer, matrix, maxX, minY, minZ, get3DRainbowColor(time, maxX, minY, minZ, seed), 0, 1, light);
        emit(consumer, matrix, maxX, maxY, minZ, get3DRainbowColor(time, maxX, maxY, minZ, seed), 0, 0, light);
        emit(consumer, matrix, maxX, maxY, maxZ, get3DRainbowColor(time, maxX, maxY, maxZ, seed), 1, 0, light);
        emit(consumer, matrix, maxX, minY, maxZ, get3DRainbowColor(time, maxX, minY, maxZ, seed), 1, 1, light);
    }

    private int get3DRainbowColor(float time, float x, float y, float z, int seed) {
        float scale = 0.20f; // 空间尺度 越大越密
        float speedX = 0.80f; // 随时间推进的流速
        float speedY = 1.20f;
        float speedZ = -0.60f;
        int octaves = 4;
        float gain = 0.32f;
        float circularity = 2.32f;

        float px = x * scale + time * speedX;
        float py = y * scale + time * speedY;
        float pz = z * scale + time * speedZ;

        float hueBase = fbm(px, py, pz, seed, octaves, gain, circularity);
        float hue = frac(hueBase + time * 0.75f); // 速度

        float sat = 0.80f; // 饱和度
        float bri = 1.00f; // 亮度

        int rgb = Color.HSBtoRGB(hue, sat, bri);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }

    @SuppressWarnings("unused")
    private static float fbm(float x, float y, float z, int seed, int octaves, float gain, float circularity) {
        float amp = FBM_AMP0, sum = 0f;
        float fx = x, fy = y, fz = z;
        int s = seed;
        for (int i = 0; i < FBM_OCT; i++) {
            sum += amp * valueNoise3D(fx, fy, fz, s);
            amp *= FBM_GAIN;
            fx *= circularity; fy *= circularity; fz *= circularity;
            s += 131;
        }
        return sum * FBM_INV_NORM;
    }

    private static float valueNoise3D(float x, float y, float z, int seed) {
        int xi = fastFloor(x), yi = fastFloor(y), zi = fastFloor(z);
        float xf = x - xi, yf = y - yi, zf = z - zi;
        float u = smooths(xf);
        float v = smooths(yf);
        float w = smooths(zf);
        float c000 = rand(xi, yi, zi, seed);
        float c100 = rand(xi + 1, yi, zi, seed);
        float c010 = rand(xi, yi + 1, zi, seed);
        float c110 = rand(xi + 1, yi + 1, zi, seed);
        float c001 = rand(xi, yi, zi + 1, seed);
        float c101 = rand(xi + 1, yi, zi + 1, seed);
        float c011 = rand(xi, yi + 1, zi + 1, seed);
        float c111 = rand(xi + 1, yi + 1, zi + 1, seed);
        float x00 = Mth.lerp(u, c000, c100);
        float x10 = Mth.lerp(u, c010, c110);
        float x01 = Mth.lerp(u, c001, c101);
        float x11 = Mth.lerp(u, c011, c111);
        float y0 = Mth.lerp(v, x00, x10);
        float y1 = Mth.lerp(v, x01, x11);
        return Mth.lerp(w, y0, y1);
    }

    private static float frac(float v) {
        v = v - (float)Math.floor(v);
        if (v >= 1f) v -= 1f;
        if (v < 0f) v -= (float)Math.floor(v);
        return v;
    }

    private static int fastFloor(float x) {
        int i = (int)x; return x < i ? i - 1 : i;
    }

    private static float smooths(float t) {
        return t * t * (3f - 2f * t);
    }

    private static int index(int i, int j, int stride) {
        return i * stride + j;
    }

    private static float rand(int x, int y, int z, int seed) {
        int h = x * 374761393 + y * 668265263 ^ z * 1442695041 ^ seed * 60493;
        h = (h ^ (h >> 13)) * 1274126177;
        h ^= (h >> 16);
        return (h & 0x7FFFFFFF) / 2147483647f;
    }

    private static void emit(VertexConsumer consumer, Matrix4f mat, float x, float y, float z, int color, float u, float v, int packedLight) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        consumer.vertex(mat, x, y, z).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(packedLight).normal(1,0,0).endVertex();
    }

    private void applyMagnetizationTransforms(PoseStack poseStack, AnemiaSpecialEffect entity, float partialTicks) {
        float intensity = entity.getDistortionIntensity();
        float time = (entity.tickCount + partialTicks) * 0.1f;

        float scaleDistortion = 1.0f + (float) Math.sin(time * 2.0f) * intensity * 0.1f;
        poseStack.scale(scaleDistortion, scaleDistortion, scaleDistortion);

        float rotationDistortion = (float) Math.sin(time * 1.5f) * intensity * 5.0f;
        poseStack.mulPose(Axis.YP.rotationDegrees(rotationDistortion));

        float jitterX = (float) (Math.sin(time * 4.0f) * intensity * 0.05f);
        float jitterY = (float) (Math.cos(time * 3.7f) * intensity * 0.05f);
        float jitterZ = (float) (Math.sin(time * 4.3f) * intensity * 0.05f);
        poseStack.translate(jitterX, jitterY, jitterZ);
    }

    private Vec3 getViewerPos(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.getPosition();
        if (mc.player != null) {
            try {
                return mc.player.getEyePosition(partialTicks);
            } catch (Throwable t) {
                return camPos;
            }
        }
        return camPos;
    }

    private void renderRainbowCube(PoseStack poseStack, MultiBufferSource buffers, AnemiaSpecialEffect entity, float partialTicks, int packedLight) {
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.BARRIER);
        Matrix4f matrix4f = poseStack.last().pose();
        AABB boundingBox = entity.getBoundingBox();

        Vec3 viewerPos = getViewerPos(partialTicks);
        Vec3 entityPos = entity.position();

        float time = (entity.tickCount + partialTicks) * 0.005f;
        float distortionIntensity = entity.getDistortionIntensity();
        float flickerIntensity = entity.getFlickerIntensity();

        float minX = (float) (boundingBox.minX - entity.getX());
        float minY = (float) (boundingBox.minY - entity.getY());
        float minZ = (float) (boundingBox.minZ - entity.getZ());
        float maxX = (float) (boundingBox.maxX - entity.getX());
        float maxY = (float) (boundingBox.maxY - entity.getY());
        float maxZ = (float) (boundingBox.maxZ - entity.getZ());

        final float cx = (minX + maxX) * 0.5f;
        final float cy = (minY + maxY) * 0.5f;
        final float cz = (minZ + maxZ) * 0.5f;

        float halfX = (maxX - minX) * 0.5f;
        float halfY = (maxY - minY) * 0.5f;
        float halfZ = (maxZ - minZ) * 0.5f;

        float[][] baseVertices = new float[][]{
                {cx - halfX, cy - halfY, cz - halfZ},
                {cx + halfX, cy - halfY, cz - halfZ},
                {cx + halfX, cy - halfY, cz + halfZ},
                {cx - halfX, cy - halfY, cz + halfZ},
                {cx - halfX, cy + halfY, cz - halfZ},
                {cx + halfX, cy + halfY, cz - halfZ},
                {cx + halfX, cy + halfY, cz + halfZ},
                {cx - halfX, cy + halfY, cz + halfZ}
        };

        int[][] faces = {{0, 1, 2, 3}, {7, 6, 5, 4}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}};

        float[][] distortedVertices = new float[8][3];
        for (int i = 0; i < 8; i++) {
            distortedVertices[i] = applyVertexDistortion(baseVertices[i], distortionIntensity, entity.getDistortionPhase(), time, i);
        }

        for (int faceIndex = 0; faceIndex < faces.length; faceIndex++) {
            int[] face = faces[faceIndex];
            renderSubdividedFace(consumer, matrix4f, distortedVertices, face, viewerPos, entityPos, time, distortionIntensity, flickerIntensity, faceIndex, packedLight);
        }

        if (distortionIntensity > 0.5f) {
            renderSmoothInterferencePatterns(consumer, matrix4f, entity, partialTicks, packedLight, distortedVertices, viewerPos, entityPos);
        }
    }

    private void renderLayerCubes(PoseStack poseStack, MultiBufferSource buffers, AnemiaSpecialEffect entity, float partialTicks, int packedLight) {
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.LIGHTNING);
        Matrix4f matrix4f = poseStack.last().pose();
        AABB aabb = entity.getBoundingBox();

        float cx = (float) ((aabb.minX + aabb.maxX) * 0.5 - entity.getX());
        float cy = (float) ((aabb.minY + aabb.maxY) * 0.5 - entity.getY());
        float cz = (float) ((aabb.minZ + aabb.maxZ) * 0.5 - entity.getZ());

        float halfX = (float) ((aabb.maxX - aabb.minX) * 0.5);
        float halfY = (float) ((aabb.maxY - aabb.minY) * 0.5);
        float halfZ = (float) ((aabb.maxZ - aabb.minZ) * 0.5);
        float maxHalf = Math.min(halfX, Math.min(halfY, halfZ));
        float t = (entity.tickCount + partialTicks) * 0.005f;

        final int[][] faces = {{0, 1, 2, 3}, {7, 6, 5, 4}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}};

        for (int layer = 0; layer < LAYER_OPACITIES.length; layer++) {
            float half = maxHalf * (layer + 1) / (LAYER_OPACITIES.length + 1f);

            float[][] v = {
                    {cx - half, cy - half, cz - half},
                    {cx + half, cy - half, cz - half},
                    {cx + half, cy - half, cz + half},
                    {cx - half, cy - half, cz + half},
                    {cx - half, cy + half, cz - half},
                    {cx + half, cy + half, cz - half},
                    {cx + half, cy + half, cz + half},
                    {cx - half, cy + half, cz + half}
            };

            int alpha = (int) (255 * LAYER_OPACITIES[layer]);

            for (int f = 0; f < faces.length; f++) {
                int baseColor = getRainbowColor(t + layer * 0.12f + f * 0.03f);
                int colour = (baseColor & 0x00FFFFFF) | (alpha << 24);

                int[] face = faces[f];
                emitQuad(consumer, matrix4f, v[face[0]], v[face[1]], v[face[2]], v[face[3]], colour, colour, colour, colour, packedLight);
            }
        }
    }

    private void renderSmoothInterferencePatterns(VertexConsumer consumer, Matrix4f mat, AnemiaSpecialEffect entity, float partialTicks, int packedLight, float[][] vertices, Vec3 playerPos, Vec3 entityPos) {
        float time = (entity.tickCount + partialTicks) * 0.02f;
        float intensity = entity.getDistortionIntensity();

        for (int i = 0; i < 5; i++) {
            float scanY = -2.5f + (i * 1.0f) + (float) Math.sin(time * 2.0f + i) * intensity;

            Vec3 scanLineWorldPos = entityPos.add(0, scanY, 0);
            double scanLineDistance = playerPos.distanceTo(scanLineWorldPos);

            float scanLineAlpha = calculateSmoothAlpha(scanLineDistance, time, i + 100);
            int scanColor = getScanLineColorWithAlpha(time, intensity, i, scanLineAlpha);

            if (scanLineAlpha > 0.01f) {
                renderSmoothScanLine(consumer, mat, vertices, scanY, scanColor, packedLight, entity);
            }
        }
    }

    private ProximityVisibility calculateSmoothProximityVisibility(Vec3 playerPos, Vec3 entityPos, float[][] vertices, int[] face, int faceIndex, float time) {
        ProximityVisibility visibility = new ProximityVisibility();
        visibility.vertexAlphaModifiers = new float[4];
        float totalAlpha = 0.0f;

        for (int i = 0; i < 4; i++) {
            Vec3 vertexWorldPos = entityPos.add(vertices[face[i]][0], vertices[face[i]][1], vertices[face[i]][2]);
            double vertexDistance = playerPos.distanceTo(vertexWorldPos);

            float vertexAlpha = calculateSmoothAlpha(vertexDistance, time, faceIndex + i);
            visibility.vertexAlphaModifiers[i] = vertexAlpha;
            totalAlpha += vertexAlpha;
        }

        visibility.faceAlpha = totalAlpha / 4.0f;
        return visibility;
    }

    private float calculateSmoothAlpha(double distance, float time, int seed) {
        if (!ENABLE_DISTANCE_FADE) return 0.6f; // alpha

        float normalizedDistance = (float) ((distance - SMOOTH_TRANSITION_START) / (SMOOTH_TRANSITION_END - SMOOTH_TRANSITION_START));
        normalizedDistance = Mth.clamp(normalizedDistance, 0.0f, 1.0f);

        float smoothAlpha = smootherStep(normalizedDistance);
        smoothAlpha = 1.0f - smoothAlpha;

        float timeVariation = (float) (Math.sin(time * 1.5f + seed * 0.7f) * 0.1f + 0.9f);
        smoothAlpha *= timeVariation;

        if (distance < SMOOTH_TRANSITION_END * 0.7f) {
            float shimmerIntensity = 1.0f - (float) (distance / (SMOOTH_TRANSITION_END * 0.7f));
            float shimmer = (float) (Math.sin(time * 8.0f + seed * 1.3f) * shimmerIntensity * 0.15f + 1.0f);
            smoothAlpha *= shimmer;
        }

        return Mth.clamp(smoothAlpha, 0.0f, 1.0f);
    }

    private void renderSubdividedFace(VertexConsumer consumer, Matrix4f matrix4f, float[][] vertices, int[] face, Vec3 playerPos, Vec3 entityPos, float time, float distortionIntensity, float flickerIntensity, int faceIndex, int packedLight) {
        if (!USE_FACE_SUBDIVISION) {
            renderSingleFaceQuad(consumer, matrix4f, vertices, face, playerPos, entityPos, time, distortionIntensity, flickerIntensity, faceIndex, packedLight);
            return;
        }

        float[] v0 = vertices[face[0]];
        float[] v1 = vertices[face[1]];
        float[] v2 = vertices[face[2]];
        float[] v3 = vertices[face[3]];

        final int capacity = Math.max(1, getVisionLevel()); // 45
        ensureGridCapacity(capacity);

        final float step = 1.0f / capacity;
        final int stride = cachedStride;

        final double px = playerPos.x;
        final double py = playerPos.y;
        final double pz = playerPos.z;

        for (int i = 0; i <= capacity; i++) {
            float u = i * step;

            edge0x[i] = Mth.lerp(u, v0[0], v1[0]);
            edge0y[i] = Mth.lerp(u, v0[1], v1[1]);
            edge0z[i] = Mth.lerp(u, v0[2], v1[2]);

            edge1x[i] = Mth.lerp(u, v3[0], v2[0]);
            edge1y[i] = Mth.lerp(u, v3[1], v2[1]);
            edge1z[i] = Mth.lerp(u, v3[2], v2[2]);
        }

        for (int j = 0; j <= capacity; j++) {
            float vv = j * step;

            for (int i = 0; i <= capacity; i++) {
                int k = index(i, j, stride);

                float x = Mth.lerp(vv, edge0x[i], edge1x[i]);
                float y = Mth.lerp(vv, edge0y[i], edge1y[i]);
                float z = Mth.lerp(vv, edge0z[i], edge1z[i]);

                gridX[k] = x;
                gridY[k] = y;
                gridZ[k] = z;

                double wx = entityPos.x + x;
                double wy = entityPos.y + y;
                double wz = entityPos.z + z;

                int color = calculateVertexColorFast(x, y, z, wx, wy, wz, time, distortionIntensity, flickerIntensity, faceIndex, (i == 0 ? 0 : (i == capacity ? 1 : (j == capacity ? 2 : 3))), px, py, pz);

                gridColor[k] = color;
            }
        }

        for (int j = 0; j < capacity; j++) {
            int row = j * stride;
            int rowNext = (j + 1) * stride;

            for (int i = 0; i < capacity; i++) {
                int k00 = row + i;
                int k10 = row + i + 1;
                int k11 = rowNext + i + 1;
                int k01 = rowNext + i;

                int c0 = gridColor[k00];
                int c1 = gridColor[k10];
                int c2 = gridColor[k11];
                int c3 = gridColor[k01];

                if (((c0 | c1 | c2 | c3) >>> 24) == 0) continue;

                emit(consumer, matrix4f, gridX[k01], gridY[k01], gridZ[k01], c3, packedLight);
                emit(consumer, matrix4f, gridX[k11], gridY[k11], gridZ[k11], c2, packedLight);
                emit(consumer, matrix4f, gridX[k10], gridY[k10], gridZ[k10], c1, packedLight);
                emit(consumer, matrix4f, gridX[k00], gridY[k00], gridZ[k00], c0, packedLight);
            }
        }
    }

    @SuppressWarnings("SameParameterValue")
    private float[] bilinearInterpolateInto(float[] v0, float[] v1, float[] v2, float[] v3, float u, float v) {
        float[] result = new float[3];
        for (int i = 0; i < 3; i++) {
            float edge1 = Mth.lerp(u, v0[i], v1[i]);
            float edge2 = Mth.lerp(u, v3[i], v2[i]);
            result[i] = Mth.lerp(v, edge1, edge2);
        }
        return result;
    }

    private int applySmoothAlpha(int baseColor, float alpha) {
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        int originalAlpha = (baseColor >> 24) & 0xFF;

        int newAlpha = (int) (originalAlpha * alpha);
        newAlpha = Math.max(0, Math.min(255, newAlpha));

        return (newAlpha << 24) | (r << 16) | (g << 8) | b;
    }

    private void renderSingleFaceQuad(VertexConsumer consumer, Matrix4f mat, float[][] vertices, int[] face, Vec3 playerPos, Vec3 entityPos, float time, float distortionIntensity, float flickerIntensity, int faceIndex, int packedLight) {
        float[] v1 = vertices[face[0]];
        float[] v2 = vertices[face[1]];
        float[] v3 = vertices[face[2]];
        float[] v4 = vertices[face[3]];

        ProximityVisibility visibility = calculateSmoothProximityVisibility(playerPos, entityPos, vertices, face, faceIndex, time);
        if (visibility.faceAlpha <= 0.0001f) {
             /*
             Avoid aggressive face-level culling which can produce hard holes when
             distance-fade reduces per-vertex alpha. Allow very small alphas to
             proceed to per-sub quad processing for smoother transitions
             */
            return;
        }

        int[] colors = calculateSmoothProximityColors(vertices, time, distortionIntensity, flickerIntensity, faceIndex, face, visibility);
        emitQuad(consumer, mat, v1, v2, v3, v4, colors[0], colors[1], colors[2], colors[3], packedLight);
    }

    private int[] calculateSmoothProximityColors(float[][] vertices, float time, float distortionIntensity, float flickerIntensity, int faceIndex, int[] face, ProximityVisibility visibility) {
        int[] colors = new int[4];
        int seed = 42;

        for (int i = 0; i < 4; i++) {
            float[] v = vertices[face[i]];
            int baseColor = get3DRainbowColor(time, v[0], v[1], v[2], seed);
            if (distortionIntensity > 0.0f) {
                baseColor = applyMagnetizationColorEffects(baseColor, distortionIntensity, flickerIntensity, time, faceIndex, face[i]);
            }
            colors[i] = applySmoothAlpha(baseColor, visibility.vertexAlphaModifiers[i]);
        }
        return colors;
    }

    private float[] applyVertexDistortion(float[] baseVertex, float distortionIntensity, float distortionPhase, float time, int vertexIndex) {
        if (distortionIntensity <= 0.0f) {
            return baseVertex.clone();
        }

        float[] distorted = baseVertex.clone();

        float waveX = (float) Math.sin(distortionPhase + vertexIndex * 0.5f) * distortionIntensity * 0.3f;
        float waveY = (float) Math.cos(distortionPhase * 1.3f + vertexIndex * 0.7f) * distortionIntensity * 0.2f;
        float waveZ = (float) Math.sin(distortionPhase * 0.8f + vertexIndex * 0.3f) * distortionIntensity * 0.25f;

        Vector3f center = new Vector3f(0, 0, 0);
        Vector3f vertexPos = new Vector3f(baseVertex[0], baseVertex[1], baseVertex[2]);
        Vector3f direction = new Vector3f(vertexPos).sub(center).normalize();

        float radialIntensity = (float) Math.sin(time * 3.0f + vertexIndex) * distortionIntensity * 0.4f;
        direction.mul(radialIntensity);

        distorted[0] += waveX + direction.x;
        distorted[1] += waveY + direction.y;
        distorted[2] += waveZ + direction.z;

        return distorted;
    }

    @SuppressWarnings("unused")
    private int applyMagnetizationColorEffects(int baseColor, float distortionIntensity, float flickerIntensity, float time, int faceIndex, int vertexIndex) {
        int r = (baseColor >> 16) & 0xFF;
        int g = (baseColor >> 8) & 0xFF;
        int b = baseColor & 0xFF;
        int a = (baseColor >> 24) & 0xFF;

        float channelOffset = time * 10.0f + vertexIndex * 2.0f;

        float redDistortion = (float) Math.sin(channelOffset) * distortionIntensity;
        r = (int) Mth.clamp(r + redDistortion * 100, 0, 255);

        float greenDistortion = (float) Math.sin(channelOffset + 2.0f) * distortionIntensity;
        g = (int) Mth.clamp(g + greenDistortion * 80, 0, 255);

        float blueDistortion = (float) Math.cos(channelOffset + 1.0f) * distortionIntensity;
        b = (int) Mth.clamp(b + blueDistortion * 120, 0, 255);

        float flicker = 1.0f - (flickerIntensity * distortionIntensity * 0.6f);
        r = (int) (r * flicker);
        g = (int) (g * flicker);
        b = (int) (b * flicker);

        if (distortionIntensity > 0.3f) {
            float bleedStrength = (distortionIntensity - 0.3f) * 0.2f;
            int avgColor = (r + g + b) / 3;

            r = (int) Mth.lerp(bleedStrength, r, avgColor);
            g = (int) Mth.lerp(bleedStrength, g, avgColor);
            b = (int) Mth.lerp(bleedStrength, b, avgColor);
        }

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int getScanLineColorWithAlpha(float time, float intensity, int lineIndex, float alpha) {
        int r = (int) (255 * Math.sin(time + lineIndex) * intensity);
        int g = (int) (100 * Math.cos(time * 1.5f + lineIndex) * intensity);
        int b = (int) (200 * Math.sin(time * 0.8f + lineIndex) * intensity);

        r = Mth.clamp(r, 0, 255);
        g = Mth.clamp(g, 0, 255);
        b = Mth.clamp(b, 0, 255);
        int a = (int) (255 * intensity * alpha * 0.3f);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @SuppressWarnings("unused")
    private void renderSmoothScanLine(VertexConsumer c, Matrix4f m, float[][] verbs, float scanY, int color, int light, AnemiaSpecialEffect anemiaSpecialEffect) {
        AABB aabb = anemiaSpecialEffect.getBoundingBoxForCulling();
        float minX = (float)(aabb.minX - anemiaSpecialEffect.getX()), maxX = (float)(aabb.maxX - anemiaSpecialEffect.getX());
        float minZ = (float)(aabb.minZ - anemiaSpecialEffect.getZ()), maxZ = (float)(aabb.maxZ - anemiaSpecialEffect.getZ());
        float thickness = 0.032f * Math.min(maxX-minX, maxZ-minZ);
        float y0 = scanY - thickness, y1 = scanY + thickness;
        float[] v1 = {minX, y0, minZ}, v2 = {maxX, y0, minZ}, v3 = {maxX, y1, minZ}, v4 = {minX, y1, minZ};
        emitQuad(c, m, v1, v2, v3, v4, color, color, color, color, light);
    }

    private static int getRainbowColor(float time) {
        float hue = (time % 1.0f);
        int color = Color.HSBtoRGB(hue, 0.32f, 0.86f);
        return (color & 0x00FFFFFF) | (255 << 24);
    }

    private static void emitQuad(VertexConsumer consumer, Matrix4f mat, float[] v1, float[] v2, float[] v3, float[] v4, int c1, int c2, int c3, int c4, int packedLight) {
        emit(consumer, mat, v1[0], v1[1], v1[2], c1, packedLight);
        emit(consumer, mat, v2[0], v2[1], v2[2], c2, packedLight);
        emit(consumer, mat, v3[0], v3[1], v3[2], c3, packedLight);
        emit(consumer, mat, v4[0], v4[1], v4[2], c4, packedLight);
    }

    private static void emit(VertexConsumer consumer, Matrix4f mat, float x, float y, float z, int color, int packedLight) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (color >> 24) & 0xFF;
        consumer.vertex(mat, x, y, z).color(r, g, b, a).uv2(packedLight).endVertex();
    }

    private int calculateVertexColorFast(float lx, float ly, float lz, double wx, double wy, double wz, float time, float distortionIntensity, float flickerIntensity, int faceIndex, int vertexIndex, double px, double py, double pz) {
        int seed = 42;
        int baseRgb = get3DRainbowColor(time, lx, ly, lz, seed);

        double lfo = Math.sin((wx + wz) * 0.12 + time * 0.6) * 0.03;
        int tweakedRgb = tweakBrightness(baseRgb, (float) lfo);

        if (distortionIntensity > 0f) {
            tweakedRgb = applyMagnetizationColorEffects(tweakedRgb, distortionIntensity, flickerIntensity, time, faceIndex, vertexIndex);
        }

        float alpha;
        if (!ENABLE_DISTANCE_FADE) {
            alpha = 0.5f;
        } else {
            double dx = wx - px, dy = wy - py, dz = wz - pz;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            alpha = calculateSmoothAlpha(dist, time, faceIndex * 4 + vertexIndex);
        }

        if (VerticalFade.enabled) {
            float hy = VerticalFade.halfY <= 0f ? 1e-6f : VerticalFade.halfY;
            float t = (ly + hy) / (2f * hy);
            t = Mth.clamp(t, 0f, 1f);
            float d = Math.abs(t - 0.5f) / 0.5f;
            float vf = 1f - VerticalFade.strength * (float)Math.pow(d, VerticalFade.exponent);
            vf = Mth.clamp(vf, 0f, 1f);
            alpha *= vf;
        }

        alpha = Mth.clamp(alpha, 0f, 1f);
        int finalAlpha = Mth.clamp((int) (alpha * 255f), 0, 255);

        return (finalAlpha << 24) | (tweakedRgb & 0x00FFFFFF);
    }

    @SuppressWarnings("unused")
    private int calculateSubQuadColorFlat(float[] v0, float[] v1, float[] v2, float[] v3, Vec3 playerPos, Vec3 entityPos, float time, float distortionIntensity, float flickerIntensity, int faceIndex, int gridI, int gridJ, int subdivisions) {
        float[] c = bilinearInterpolateInto(v0, v1, v2, v3, 0.5f, 0.5f);
        double wx = entityPos.x + c[0];
        double wy = entityPos.y + c[1];
        double wz = entityPos.z + c[2];

        float alpha = calculateSmoothAlpha((float) playerPos.distanceTo(new Vec3(wx, wy, wz)), time, 0);
        alpha = Mth.clamp(alpha, 0f, 1f);

        int seed = 42;
        int rgb = get3DRainbowColor(time, c[0], c[1], c[2], seed);

        double lfo = Math.sin((wx + wz) * 0.12 + time * 0.6) * 0.03;
        rgb = tweakBrightness(rgb, (float) lfo);

        if (distortionIntensity > 0f) {
            rgb = applyMagnetizationColorEffects(rgb, distortionIntensity, flickerIntensity, time, faceIndex, 0);
        }

        int ai = Mth.clamp((int) (alpha * 255f), 0, 255);
        return (ai << 24) | (rgb & 0x00FFFFFF);
    }

    private static int tweakBrightness(int argb, float delta) {
        delta = clamp01(delta * 0.5f + 0.5f) * 2f - 1f;
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = (argb) & 0xFF;
        r = adjustChannel(r, delta);
        g = adjustChannel(g, delta);
        b = adjustChannel(b, delta);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int adjustChannel(int c, float delta) {
        float v;
        if (delta >= 0f) {
            v = c + (255 - c) * delta;
        } else {
            v = c * (1f + delta);
        }
        return clamp255(Math.round(v));
    }

    private static int clamp255(int x) {
        return x < 0 ? 0 : (Math.min(x, 255));
    }

    private static float clamp01(float x) {
        return x < 0f ? 0f : (Math.min(x, 1f));
    }

    @SuppressWarnings("unused")
    private float smoothStep(float t) {
        return t * t * (3.0f - 2.0f * t);
    }

    @SuppressWarnings("unused")
    private float smootherStep(float t) {
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }

    @SuppressWarnings("unused")
    private float exponentialEase(float t, float strength) {
        return (float) (1.0 - Math.pow(1.0 - t, strength));
    }

    private static int getVisionLevel() {
        return SUBDIVISION_LEVEL_FADE;
    }

    static class ProximityVisibility {
        float faceAlpha = 1.0f;
        float[] vertexAlphaModifiers = new float[4];
        ProximityVisibility() {
            for (int i = 0; i < 4; i++) {
                vertexAlphaModifiers[i] = 1.0f;
            }
        }
    }

    @SuppressWarnings("SameParameterValue,unused")
    static class VerticalFade {
        static boolean enabled = false;
        static float halfY = 1f;
        static float strength = 1f;
        static float exponent = 2f;

        static void enable(boolean en, float hy) {
            enabled = en; halfY = hy <= 0 ? 1f : hy; strength = 1f; exponent = 2f;
        }

        static void enable(boolean en, float hy, float s, float e) {
            enabled = en; halfY = hy <= 0 ? 1f : hy; strength = s; exponent = e;
        }

        static void disable() {
            enabled = false;
        }
    }

    public void renderFaceOfAABB(PoseStack poseStack, MultiBufferSource buffers, AABB boxWorld, Vec3 centerWorld, int faceIndex, float partialTicks, int packedLight, boolean verticalFade, float fadeHalfY) {
        boolean prevFade = ENABLE_DISTANCE_FADE;
        ENABLE_DISTANCE_FADE = false;

        try {
            final Vec3 viewer = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            final float time = (Minecraft.getInstance().level == null ? 0f : (Minecraft.getInstance().level.getGameTime() + partialTicks) * 0.005f);

            poseStack.pushPose();
            poseStack.translate(centerWorld.x, centerWorld.y, centerWorld.z);

            float minLX = (float) (boxWorld.minX - centerWorld.x);
            float minLY = (float) (boxWorld.minY - centerWorld.y);
            float minLZ = (float) (boxWorld.minZ - centerWorld.z);
            float maxLX = (float) (boxWorld.maxX - centerWorld.x);
            float maxLY = (float) (boxWorld.maxY - centerWorld.y);
            float maxLZ = (float) (boxWorld.maxZ - centerWorld.z);

            float[][] v = new float[][] {
                    {minLX, minLY, minLZ},
                    {maxLX, minLY, minLZ},
                    {maxLX, minLY, maxLZ},
                    {minLX, minLY, maxLZ},
                    {minLX, maxLY, minLZ},
                    {maxLX, maxLY, minLZ},
                    {maxLX, maxLY, maxLZ},
                    {minLX, maxLY, maxLZ}
            };
            int[][] faces = {{0,1,2,3}, {7,6,5,4}, {0,4,5,1}, {1,5,6,2}, {2,6,7,3}, {3,7,4,0}};

            VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.BARRIER);
            Matrix4f matrix4f = poseStack.last().pose();

            // fix strength+ 1.2~2.0 → 整体更透明 顶/底更早透明 exponent- 1.0~1.5 → 渐隐区域更宽更平滑
            VerticalFade.enable(verticalFade, fadeHalfY, 1.1326f, 1.156f);

            renderSubdividedFace(consumer, matrix4f, v, faces[faceIndex], viewer, centerWorld, time, 0f, 0f, faceIndex, packedLight);

            VerticalFade.disable();
            poseStack.popPose();
        } finally {
            ENABLE_DISTANCE_FADE = prevFade;
        }
    }

    @Override
    @SuppressWarnings("all")
    public @NotNull ResourceLocation getTextureLocation(@NotNull AnemiaSpecialEffect entity) {
        return PATTERN;
    }
}
