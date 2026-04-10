package flu.kitten.adorablearmory.entity.effect.entityrender;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.BlackHoleLateRenderQueue;
import flu.kitten.adorablearmory.client.BlackHoleLensClient;
import flu.kitten.adorablearmory.client.obj.ObjLoader;
import flu.kitten.adorablearmory.entity.effect.TrueDemonBlackHole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.*;

import java.lang.Math;
import java.util.SplittableRandom;

public class TrueDemonBlackHoleRender extends EntityRenderer<TrueDemonBlackHole> {

    public static final ResourceLocation DEMON_CAVE_TEX = new ResourceLocation(AdorableArmory.MODID, "textures/entity/true_demon_black_hole.png");
    public static final ResourceLocation DEMON_CAVE_OBJ = new ResourceLocation(AdorableArmory.MODID, "models/entity/true_demon_black_hole.obj");
    public static final ResourceLocation CIRCUIT_MASK_TEX = new ResourceLocation(AdorableArmory.MODID, "textures/entity/black_hole_circuit_mask.png");

    public TrueDemonBlackHoleRender(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public void render(@NotNull TrueDemonBlackHole trueDemonBlackHole, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffers, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        ModelHolder.INSTANCE.ensureLoaded(mc.getResourceManager());

        float width = trueDemonBlackHole.getBbWidth();
        float height = trueDemonBlackHole.getBbHeight();
        float centerYOffsetWorld = ModelHolder.INSTANCE.computeCenterYOffset(width, height);

        poseStack.pushPose();

        if (!BlackHoleLensClient.isLatePass()) {
            enqueueLensScreenSpace(poseStack, trueDemonBlackHole, centerYOffsetWorld);
            BlackHoleLateRenderQueue.queue(trueDemonBlackHole, entityYaw, packedLight);
            poseStack.popPose();
            return;
        }

        float billboardScale = computeBillboardScale();
        float timeTicks = trueDemonBlackHole.tickCount + partialTicks;

        RayTemps rayTemps = TL_RAY_TEMPS.get();
        fillCameraLocal(trueDemonBlackHole, centerYOffsetWorld, partialTicks, rayTemps.camLocal);

        renderBloomBillboard(poseStack, buffers, width, centerYOffsetWorld, billboardScale);
        renderFinalOccludeDisc(poseStack, buffers, trueDemonBlackHole, centerYOffsetWorld, billboardScale);
        renderCircuitGlowDisc(poseStack, buffers, trueDemonBlackHole, centerYOffsetWorld, timeTicks, billboardScale);
        render3DAbsorptionRays(poseStack, buffers, trueDemonBlackHole, centerYOffsetWorld, timeTicks);
        renderLightningArcs(poseStack, buffers, trueDemonBlackHole, centerYOffsetWorld, timeTicks, rayTemps.camLocal);

        poseStack.popPose();
        super.render(trueDemonBlackHole, entityYaw, partialTicks, poseStack, buffers, packedLight);
    }

    private static float computeBillboardScale() {
        Matrix4f projectionMatrix = RenderSystem.getProjectionMatrix();
        float aspect = projectionMatrix.m11() / projectionMatrix.m00();
        return Math.max(0.25f, aspect - 0.80f);
    }

    private static void fillCameraLocal(TrueDemonBlackHole demonBlackHole, float centerYOffsetWorld, float partialTicks, Vector3f out) {
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        double ex = Mth.lerp(partialTicks, demonBlackHole.xo, demonBlackHole.getX());
        double ey = Mth.lerp(partialTicks, demonBlackHole.yo, demonBlackHole.getY());
        double ez = Mth.lerp(partialTicks, demonBlackHole.zo, demonBlackHole.getZ());
        out.set((float) (camPos.x - ex), (float) (camPos.y - ey - centerYOffsetWorld), (float) (camPos.z - ez));
    }

    private static void renderBloomBillboard(PoseStack poseStack, MultiBufferSource buffers, float width, float centerYOffsetWorld, float billboardScale) {
        poseStack.pushPose();

        poseStack.translate(0, centerYOffsetWorld, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.scale(billboardScale, billboardScale, 1);

        float outerRadius = 1.035f * width;
        float ringInnerRadius = outerRadius * 0.60f;
        float coreRadius = outerRadius * 0.50f;

        int segments = Mth.clamp((int) (outerRadius * 40.0f), 48, 96);
        int fadeSteps = Mth.clamp((int) (outerRadius * 15.0f), 8, 16);

        CircleLUT circleLUT = TL_BLOOM_LUT.get();
        circleLUT.ensure(segments);
        float[] cosCache = circleLUT.cos;
        float[] sinCache = circleLUT.sin;

        int r = 255, g = 0, b = 255;
        int ringAlpha = 160;

        Matrix4f pose = poseStack.last().pose();

        VertexConsumer whiteCore = buffers.getBuffer(BloomRenderTypes.bloomDisc());
        whiteCore.vertex(pose, 0f, 0f, 0f).color(255, 255, 255, 255).endVertex();
        for (int i = 0; i <= segments; i++) {
            whiteCore.vertex(pose, cosCache[i] * coreRadius, sinCache[i] * coreRadius, 0f).color(255, 255, 255, 160).endVertex();
        }

        VertexConsumer transitionRing = buffers.getBuffer(BloomRenderTypes.bloomRing());
        for (int i = 0; i <= segments; i++) {
            float cos = cosCache[i];
            float sin = sinCache[i];
            transitionRing.vertex(pose, cos * coreRadius, sin * coreRadius, 0f).color(255, 255, 255, ringAlpha).endVertex();
            transitionRing.vertex(pose, cos * ringInnerRadius, sin * ringInnerRadius, 0f).color(r, g, b, ringAlpha).endVertex();
        }

        VertexConsumer purpleRing = buffers.getBuffer(BloomRenderTypes.bloomRing());
        for (int j = 0; j < fadeSteps; j++) {
            float tInner = (float) j / (float) fadeSteps;
            float tOuter = (float) (j + 1) / (float) fadeSteps;

            float radiusInner = Mth.lerp(tInner, ringInnerRadius, outerRadius);
            float radiusOuter = Mth.lerp(tOuter, ringInnerRadius, outerRadius);

            float aInner = 1.0f - smooths(tInner);
            float aOuter = 1.0f - smooths(tOuter);

            aInner *= aInner;
            aOuter *= aOuter;

            int alphaInner = (int) (ringAlpha * aInner);
            int alphaOuter = (int) (ringAlpha * aOuter);

            for (int i = 0; i <= segments; i++) {
                float cos = cosCache[i];
                float sin = sinCache[i];
                purpleRing.vertex(pose, cos * radiusInner, sin * radiusInner, 0f).color(r, g, b, alphaInner).endVertex();
                purpleRing.vertex(pose, cos * radiusOuter, sin * radiusOuter, 0f).color(r, g, b, alphaOuter).endVertex();
            }
        }

        poseStack.popPose();
    }

    // 黑色正圆
    private static void renderFinalOccludeDisc(PoseStack poseStack, MultiBufferSource buffers, TrueDemonBlackHole demonBlackHole, float centerYOffsetWorld, float billboardScale) {
        float width = demonBlackHole.getBbWidth();

        poseStack.pushPose();
        poseStack.translate(0, centerYOffsetWorld, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.scale(billboardScale, billboardScale, 1);

        float outerRadiusBase = 1.035f * width;
        float occludeMul = 0.556f;
        float outerRadius = outerRadiusBase * occludeMul;
        float featherWidth = outerRadius * 0.18f;
        float innerSolid = Math.max(0.0f, outerRadius - featherWidth);
        float discRadius = innerSolid + outerRadius * 0.0015f;

        int segments = Mth.clamp((int)(outerRadius * 40.0f), 48, 96);
        int curveSteps = Mth.clamp((int)(featherWidth * 20.0f), 6, 16);

        CircleLUT circleLUT = TL_OCCLUDE_LUT.get();
        circleLUT.ensure(segments);
        float[] cosCache = circleLUT.cos;
        float[] sinCache = circleLUT.sin;

        Matrix4f pose = poseStack.last().pose();

        VertexConsumer disc = buffers.getBuffer(BloomRenderTypes.occludeDisc());
        disc.vertex(pose, 0f, 0f, 0f).color(0, 0, 0, 255).endVertex();
        for (int i = 0; i <= segments; i++) {
            disc.vertex(pose, cosCache[i] * discRadius, sinCache[i] * discRadius, 0f).color(0, 0, 0, 255).endVertex();
        }

        VertexConsumer ring = buffers.getBuffer(BloomRenderTypes.occludeFeatherRing());
        for (int j = 0; j < curveSteps; j++) {
            float t0 = (float) j / curveSteps;
            float t1 = (float) (j + 1) / curveSteps;

            float r0 = innerSolid + t0 * featherWidth;
            float r1 = innerSolid + t1 * featherWidth;

            float a0 = 1.0f - smooths(t0);
            float a1 = 1.0f - smooths(t1);

            a0 = (float)Math.sqrt(Math.sqrt(a0));
            a1 = (float)Math.sqrt(Math.sqrt(a1));

            int alpha0 = Mth.clamp(Math.round(255f * a0), 0, 255);
            int alpha1 = Mth.clamp(Math.round(255f * a1), 0, 255);
            if (alpha0 == 0 && alpha1 == 0) continue;

            for (int i = 0; i <= segments; i++) {
                float cos = cosCache[i], sin = sinCache[i];
                ring.vertex(pose, cos * r0, sin * r0, 0f).color(0, 0, 0, alpha0).endVertex();
                ring.vertex(pose, cos * r1, sin * r1, 0f).color(0, 0, 0, alpha1).endVertex();
            }
        }

        poseStack.popPose();
    }

    private static float smooths(float x) {
        x = Mth.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    private static void renderCircuitGlowDisc(PoseStack poseStack, MultiBufferSource buffers, TrueDemonBlackHole demonBlackHole, float centerYOffsetWorld, float timeTicks, float billboardScale) {
        float width = demonBlackHole.getBbWidth();

        poseStack.pushPose();
        poseStack.translate(0, centerYOffsetWorld, 0);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.scale(billboardScale, billboardScale, 1);
        poseStack.translate(0, 0, -0.01f);

        float outerRadiusBase = 1.035f * width;
        float occludeMulBlack = 0.5032f;
        float blackRadius = outerRadiusBase * occludeMulBlack;
        float ringOuter = blackRadius * 1.45f;

        int segments = Mth.clamp((int) (ringOuter * 100.0f), 96, 160);
        int radialSteps = 30;

        float rot = timeTicks * 0.001f;
        float cosRot = Mth.cos(rot);
        float sinRot = Mth.sin(rot);

        CircuitLUT lut = TL_CIRCUIT_LUT.get();
        lut.ensure(segments);

        Matrix4f pose = poseStack.last().pose();
        Matrix3f normal = poseStack.last().normal();
        VertexConsumer consumer = buffers.getBuffer(BloomRenderTypes.circuitGlowRing());

        final int rBase = 255, gBase = 220, bBase = 255;
        final int rViolet = 150, gViolet = 20, bViolet = 255;
        final float alphaPeak = 156;

        float timePhase = timeTicks * 0.015f;

        for (int j = 0; j < radialSteps; j++) {
            float u0 = (float) j / radialSteps;
            float u1 = (float) (j + 1) / radialSteps;

            float r0 = Mth.lerp(u0, blackRadius, ringOuter);
            float r1 = Mth.lerp(u1, blackRadius, ringOuter);

            float rawFlow0 = (Mth.sin(u0 * 24.0f - timeTicks * 0.45f) + 1.0f) * 0.5f;
            float rawFlow1 = (Mth.sin(u1 * 24.0f - timeTicks * 0.45f) + 1.0f) * 0.5f;
            float flow0 = rawFlow0 * rawFlow0 * rawFlow0;
            float flow1 = rawFlow1 * rawFlow1 * rawFlow1;

            float diff0 = 1.0f - u0;
            float fade0 = diff0 * (float)Math.sqrt(diff0);
            float diff1 = 1.0f - u1;
            float fade1 = diff1 * (float)Math.sqrt(diff1);

            float baseEnergy0 = flow0 * fade0;
            float baseEnergy1 = flow1 * fade1;

            float sineU0 = Mth.sin((float)Math.PI * u0);
            float a0 = sineU0 * (float)Math.sqrt(sineU0);
            float sineU1 = Mth.sin((float)Math.PI * u1);
            float a1 = sineU1 * (float)Math.sqrt(sineU1);

            int baseAlpha0 = (int)(alphaPeak * a0);
            int baseAlpha1 = (int)(alphaPeak * a1);

            float uvRad0 = Mth.lerp(u0, 0.72f, 1.00f);
            float uvRad1 = Mth.lerp(u1, 0.72f, 1.00f);

            for (int i = 0; i <= segments; i++) {
                float c0 = lut.cosBase[i];
                float s0 = lut.sinBase[i];
                float cos = c0 * cosRot - s0 * sinRot;
                float sin = s0 * cosRot + c0 * sinRot;

                float pathPhase = lut.angle12[i] + Mth.sin(lut.angle2[i] + timePhase) * 1.5f;
                float angleMask = (Mth.sin(pathPhase) + 1.0f) * 0.5f;

                float energy0 = baseEnergy0 * angleMask;
                float energy1 = baseEnergy1 * angleMask;

                int rVal0 = (int) (rBase + energy0 * (rViolet - rBase));
                int gVal0 = (int) (gBase + energy0 * (gViolet - gBase));
                int bVal0 = (int) (bBase + energy0 * (bViolet - bBase));

                int rVal1 = (int) (rBase + energy1 * (rViolet - rBase));
                int gVal1 = (int) (gBase + energy1 * (gViolet - gBase));
                int bVal1 = (int) (bBase + energy1 * (bViolet - bBase));

                float hollowMask0 = Math.max(0.0f, 1.0f - energy0 * 2.5f);
                float hollowMask1 = Math.max(0.0f, 1.0f - energy1 * 2.5f);

                int finalAlpha0 = (int)(baseAlpha0 * hollowMask0);
                int finalAlpha1 = (int)(baseAlpha1 * hollowMask1);

                float uTex0 = cos * 0.5f * uvRad0 + 0.5f;
                float vTex0 = sin * 0.5f * uvRad0 + 0.5f;
                float uTex1 = cos * 0.5f * uvRad1 + 0.5f;
                float vTex1 = sin * 0.5f * uvRad1 + 0.5f;

                consumer.vertex(pose, cos * r0, sin * r0, 0f).color(rVal0, gVal0, bVal0, finalAlpha0).uv(uTex0, vTex0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal, 0f, 0f, 1f).endVertex();
                consumer.vertex(pose, cos * r1, sin * r1, 0f).color(rVal1, gVal1, bVal1, finalAlpha1).uv(uTex1, vTex1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0).normal(normal, 0f, 0f, 1f).endVertex();
            }
        }

        poseStack.popPose();
    }

    private static void render3DAbsorptionRays(PoseStack poseStack, MultiBufferSource buffers, TrueDemonBlackHole demonBlackHole, float centerYOffsetWorld, float timeTicks) {
        float width = demonBlackHole.getBbWidth();
        poseStack.pushPose();

        poseStack.translate(0, centerYOffsetWorld, 0);
        poseStack.mulPose(Axis.YP.rotationDegrees(timeTicks * 0.001f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(timeTicks * 0.001f));

        VertexConsumer consumer = buffers.getBuffer(BloomRenderTypes.bloomQuads());
        Matrix4f pose = poseStack.last().pose();

        float rStart = width * 1.0032f;
        float rEnd = width * 0.50f;

        float partialTicks = timeTicks - demonBlackHole.tickCount;
        Vec3 camPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        double leapX = Mth.lerp(partialTicks, demonBlackHole.xo, demonBlackHole.getX());
        double leapY = Mth.lerp(partialTicks, demonBlackHole.yo, demonBlackHole.getY());
        double leapZ = Mth.lerp(partialTicks, demonBlackHole.zo, demonBlackHole.getZ());

        RayTemps temps = TL_RAY_TEMPS.get();

        temps.camLocal.set((float) (camPos.x - leapX), (float) (camPos.y - leapY - centerYOffsetWorld), (float) (camPos.z - leapZ));

        float rotSmallRad = (float) Math.toRadians(-timeTicks * 0.0001f);
        temps.camLocal.rotateZ(rotSmallRad);
        temps.camLocal.rotateY(rotSmallRad);

        final int r = 255, g = 0, b = 255;

        for (int i = 0; i < ABSORB_RAY_COUNT; i++) {
            Vector3f dir = ABSORB_DIR[i];

            float speed = ABSORB_SPEED[i];
            float offset = ABSORB_OFFSET[i];

            float speedMul = 1.32f; // 速度
            float t = (timeTicks * (speed * speedMul) + offset) % 1.0f;

            float lifeAlpha = (float) Math.sin(t * Math.PI);
            lifeAlpha = (float) Math.pow(lifeAlpha, 1.5f);

            float rMid = Mth.lerp(t, rStart, rEnd);
            float length = width * (0.18f + ABSORB_LEN_RAND[i] * 0.32f);
            float thick = width * (0.0032f + ABSORB_THICK_RAND[i] * 0.0046f);

            float rIn = rMid - length * 0.5f;
            float rOut = rMid + length * 0.5f;

            temps.midPos.set(dir).mul(rMid);

            temps.viewDir.set(temps.camLocal).sub(temps.midPos);
            float viewLen2 = temps.viewDir.lengthSquared();
            if (viewLen2 < 1e-10f) {
                temps.viewDir.set(0, 0, 1);
            } else {
                temps.viewDir.mul(Mth.invSqrt(viewLen2));
            }

            temps.normal.set(dir).cross(temps.viewDir);
            float nLen2 = temps.normal.lengthSquared();
            if (nLen2 < 1e-10f || Float.isNaN(nLen2)) {
                temps.normal.set(0, 1, 0);
            } else {
                temps.normal.mul(Mth.invSqrt(nLen2));
            }

            int alpha = (int) (255 * lifeAlpha);
            drawRayPlane(consumer, pose, dir, temps.normal, rIn, rMid, rOut, thick, r, g, b, alpha);
        }

        poseStack.popPose();
    }

    // 闪电弧效果
    private static void renderLightningArcs(PoseStack poseStack, MultiBufferSource buffers, TrueDemonBlackHole demonBlackHole, float centerYOffsetWorld, float timeTicks, Vector3f cameraLocalToCenter) {
        float width = demonBlackHole.getBbWidth();
        float cameraDistanceSqr = cameraLocalToCenter.lengthSquared();

        int concurrentArcs = chooseLightningArcCount(cameraDistanceSqr, width);
        int detailGenerations = chooseLightningGenerations(cameraDistanceSqr, width);

        float baseLifespan = 20.0f;

        float arcRadius = width - 0.40f;
        float jaggedness = arcRadius * (detailGenerations >= 5 ? 0.40f : detailGenerations == 4 ? 0.34f : 0.28f);

        float outerWidth = width * 0.05f;
        float middleWidth = width * 0.03f;
        float coreWidth = width * 0.012f; // 黑色核心

        float flickerSpeed = 2.0f;
        float flickerIntensity = 0.20f;
        float headWidth = 0.16f;

        poseStack.pushPose();
        poseStack.translate(0, centerYOffsetWorld, 0);

        Matrix4f pose = poseStack.last().pose();

        LightningScratch scratch = TL_LIGHTNING.get();

        for (int i = 0; i < concurrentArcs; i++) {
            float lifespan = baseLifespan + (i * 3.3f) + (demonBlackHole.getId() % 5);
            int cycle = (int) (timeTicks / lifespan);
            float age = (timeTicks % lifespan) / lifespan;

            float lifeAlpha = Mth.sin(age * (float) Math.PI);

            float phase = (demonBlackHole.getId() * 0.137f + i * 1.731f);
            float flicker = (1.0f - flickerIntensity) + flickerIntensity * Mth.sin(timeTicks * flickerSpeed + i * 1.3f + phase * 6.28318f);
            lifeAlpha *= flicker;

            if (lifeAlpha <= 0.01f) continue;

            long seed = 0x9E3779B97F4A7C15L ^ ((long) demonBlackHole.getId() * 0xBF58476D1CE4E5B9L) ^ ((long) i * 0x94D049BB133111EBL) ^ ((long) cycle * 0xD2B74407B1CE6E93L);
            scratch.rng.setSeed(seed);

            fillRandomPointOnSphere(scratch.rng, arcRadius, scratch.start);
            fillRandomPointOnSphere(scratch.rng, arcRadius, scratch.end);
            if (scratch.start.distanceSquared(scratch.end) < arcRadius * arcRadius) {
                scratch.end.mul(-1.0f);
            }

            int pCount = fillLightningPath(scratch.rng, scratch.start, scratch.end, detailGenerations, jaggedness, scratch.path);
            if (pCount < 2) continue;

            float totalLen = 0.0f;
            for (int j = 0; j < pCount - 1; j++) {
                float segLen = scratch.path[j].distance(scratch.path[j + 1]);
                scratch.segmentLengths[j] = segLen;
                totalLen += segLen;
            }
            if (totalLen < 1e-6f) continue;

            float phase01 = phase - (float) Math.floor(phase);
            float delay = phase01 * 0.35f;

            float head = (age - delay) / (1.0f - delay);
            head = Mth.clamp(head, 0.0f, 1.0f);

            for (int j = 0; j < pCount; j++) {
                Vector3f tangent = scratch.tangent;
                if (j == 0) {
                    tangent.set(scratch.path[1]).sub(scratch.path[0]);
                } else if (j == pCount - 1) {
                    tangent.set(scratch.path[pCount - 1]).sub(scratch.path[pCount - 2]);
                } else {
                    tangent.set(scratch.path[j + 1]).sub(scratch.path[j - 1]);
                }

                float tLen2 = tangent.lengthSquared();
                if (tLen2 > 1e-10f) {
                    tangent.mul(Mth.invSqrt(tLen2));
                } else {
                    tangent.set(1, 0, 0);
                }

                scratch.viewDir.set(cameraLocalToCenter).sub(scratch.path[j]);

                Vector3f extrusion = scratch.extrusions[j];
                extrusion.set(tangent).cross(scratch.viewDir);

                float nLen2 = extrusion.lengthSquared();
                if (nLen2 > 1e-10f) {
                    extrusion.mul(Mth.invSqrt(nLen2));
                } else {
                    extrusion.set(0, 1, 0);
                }
            }

            float accum = 0.0f;
            for (int j = 0; j < pCount - 1; j++) {
                Vector3f p1 = scratch.path[j];
                Vector3f p2 = scratch.path[j + 1];
                Vector3f n1 = scratch.extrusions[j];
                Vector3f n2 = scratch.extrusions[j + 1];

                float segLen = scratch.segmentLengths[j];
                float u1 = accum / totalLen;
                float u2 = (accum + segLen) / totalLen;
                accum += segLen;

                float t1 = (float) j / (pCount - 1);
                float t2 = (float) (j + 1) / (pCount - 1);

                float edgeFade1 = (float) Math.sqrt(Math.max(0.0f, Mth.sin(t1 * (float) Math.PI)));
                float edgeFade2 = (float) Math.sqrt(Math.max(0.0f, Mth.sin(t2 * (float) Math.PI)));

                float pulse1 = pulseAt(u1, head, headWidth);
                float pulse2 = pulseAt(u2, head, headWidth);

                float tailLen = 0.25f;
                float vis1 = trailVisibility(u1, head, tailLen);
                float vis2 = trailVisibility(u2, head, tailLen);

                if (vis1 <= 0.0f && vis2 <= 0.0f) continue;

                pulse1 *= vis1;
                pulse2 *= vis2;

                drawLightningConnectedSegmentFlow(buffers, pose, p1, p2, n1, n2, outerWidth, middleWidth, coreWidth, lifeAlpha, edgeFade1, edgeFade2, pulse1, pulse2);
            }
        }

        poseStack.popPose();
    }

    private static int chooseLightningArcCount(float distanceSqr, float width) {
        float near = 14.0f + width * 6.0f;
        float mid = 26.0f + width * 6.0f;

        if (distanceSqr <= near * near) return 4;
        if (distanceSqr <= mid * mid) return 3;
        return 2;
    }

    private static int chooseLightningGenerations(float distanceSqr, float width) {
        float near = 14.0f + width * 6.0f;
        float mid = 26.0f + width * 6.0f;

        if (distanceSqr <= near * near) return 5;
        if (distanceSqr <= mid * mid) return 4;
        return 3;
    }

    private static void fillRandomPointOnSphere(FastRandom rng, float radius, Vector3f out) {
        float theta = rng.nextFloat() * TWO_PI;
        float cosPhi = rng.nextFloat() * 2.0f - 1.0f;
        float sinPhi = (float) Math.sqrt(Math.max(0.0f, 1.0f - cosPhi * cosPhi));

        float scale = 0.8f + rng.nextFloat() * 0.4f;
        float r = radius * scale;

        out.set(sinPhi * Mth.cos(theta) * r, cosPhi * r, sinPhi * Mth.sin(theta) * r);
    }

    private static int fillLightningPath(FastRandom rng, Vector3f start, Vector3f end, int generations, float maxOffset, Vector3f[] out) {
        int pointCount = (1 << generations) + 1;

        out[0].set(start);
        out[pointCount - 1].set(end);

        int step = pointCount - 1;
        float currentOffset = maxOffset;
        float offsetDecay = 0.55f;

        while (step > 1) {
            int halfStep = step >> 1;
            for (int i = 0; i < pointCount - 1; i += step) {
                Vector3f p1 = out[i];
                Vector3f p2 = out[i + step];
                out[i + halfStep].set(p1).add(p2).mul(0.5f).add(rng.nextSignedFloat() * currentOffset, rng.nextSignedFloat() * currentOffset, rng.nextSignedFloat() * currentOffset);
            }

            step = halfStep;
            currentOffset *= offsetDecay;
        }

        return pointCount;
    }

    private static float pulseAt(float u, float head, float w) {
        float d = Math.abs(u - head);
        float p = 1.0f - d / Math.max(w, 1e-4f);
        p = Mth.clamp(p, 0f, 1f);
        return p * p;
    }

    private static float trailVisibility(float u, float head, float tailLen) {
        float start = head - tailLen;
        if (u > head) return 0f;
        if (u < start) return 0f;

        float x = (u - start) / Math.max(tailLen, 1e-4f);
        x = Mth.clamp(x, 0f, 1f);

        final float tailSoft = 0.25f; // 尾部渐隐长度占比
        final float headSoft = 0.20f; // 头部渐隐长度占比
        float tailRamp = smooths01(x / Math.max(tailSoft, 1e-4f));
        float headRamp = smooths01((1f - x) / Math.max(headSoft, 1e-4f));

        final float headMin = 0.01f; // 头部最小可见度
        headRamp = headMin + (1f - headMin) * headRamp;

        return tailRamp * headRamp;
    }

    private static float smooths01(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    private static void drawLightningConnectedSegmentFlow(MultiBufferSource bufferSource, Matrix4f pose, Vector3f p1, Vector3f p2, Vector3f n1, Vector3f n2, float outerW, float midW, float coreW, float globalAlpha, float fade1, float fade2, float pulse1, float pulse2) {
        // RGB
        int outerR = 255, outerG = 0, outerB = 255;
        int midR = 255, midG = 100, midB = 255;
        int coreR = 0, coreG = 0, coreB = 0;

        // 透明度
        int outerMaxAlpha = 80; // 外层最透明
        int midMaxAlpha = 160; // 中层半透明
        int coreMaxAlpha = 255; // 核心不透明

        float mulOuter1 = 0.55f + 0.45f * pulse1;
        float mulOuter2 = 0.55f + 0.45f * pulse2;

        float mulMid1 = 0.35f + 0.65f * pulse1;
        float mulMid2 = 0.35f + 0.65f * pulse2;

        float mulCore1  = 0.15f + 0.85f * pulse1;
        float mulCore2  = 0.15f + 0.85f * pulse2;

        int aOuter1 = Mth.clamp((int)(globalAlpha * fade1 * outerMaxAlpha * mulOuter1), 0, 255);
        int aOuter2 = Mth.clamp((int)(globalAlpha * fade2 * outerMaxAlpha * mulOuter2), 0, 255);

        int aMid1 = Mth.clamp((int)(globalAlpha * fade1 * midMaxAlpha   * mulMid1), 0, 255);
        int aMid2 = Mth.clamp((int)(globalAlpha * fade2 * midMaxAlpha   * mulMid2), 0, 255);

        int aCore1 = Mth.clamp((int)(globalAlpha * fade1 * coreMaxAlpha  * mulCore1), 0, 255);
        int aCore2 = Mth.clamp((int)(globalAlpha * fade2 * coreMaxAlpha  * mulCore2), 0, 255);

        VertexConsumer glowConsumer = bufferSource.getBuffer(BloomRenderTypes.bloomQuads());
        VertexConsumer coreConsumer = bufferSource.getBuffer(BloomRenderTypes.blackLightningCore());

        drawLightningQuad(glowConsumer, pose, p1, p2, n1, n2, outerW, outerR, outerG, outerB, aOuter1, aOuter2);
        drawLightningQuad(glowConsumer, pose, p1, p2, n1, n2, midW,   midR,   midG,   midB,   aMid1,   aMid2);
        drawLightningQuad(coreConsumer, pose, p1, p2, n1, n2, coreW,  coreR,  coreG,  coreB,  aCore1,  aCore2);
    }

    private static void drawLightningQuad(VertexConsumer consumer, Matrix4f pose, Vector3f p1, Vector3f p2, Vector3f n1, Vector3f n2, float width, int r, int g, int b, int a1, int a2) {
        if (a1 <= 0 && a2 <= 0) return;

        float n1x = n1.x * width, n1y = n1.y * width, n1z = n1.z * width;
        float n2x = n2.x * width, n2y = n2.y * width, n2z = n2.z * width;

        float p1x_a = p1.x + n1x, p1y_a = p1.y + n1y, p1z_a = p1.z + n1z;
        float p1x_b = p1.x - n1x, p1y_b = p1.y - n1y, p1z_b = p1.z - n1z;

        float p2x_a = p2.x + n2x, p2y_a = p2.y + n2y, p2z_a = p2.z + n2z;
        float p2x_b = p2.x - n2x, p2y_b = p2.y - n2y, p2z_b = p2.z - n2z;

        consumer.vertex(pose, p1x_a, p1y_a, p1z_a).color(r, g, b, a1).endVertex();
        consumer.vertex(pose, p2x_a, p2y_a, p2z_a).color(r, g, b, a2).endVertex();
        consumer.vertex(pose, p2x_b, p2y_b, p2z_b).color(r, g, b, a2).endVertex();
        consumer.vertex(pose, p1x_b, p1y_b, p1z_b).color(r, g, b, a1).endVertex();
    }

    private static void drawRayPlane(VertexConsumer consumer, Matrix4f pose, Vector3f dir, Vector3f normal, float rIn, float rMid, float rOut, float thick, int r, int g, int b, int alpha) {
        float inX = dir.x * rIn, inY = dir.y * rIn, inZ = dir.z * rIn;
        float midX = dir.x * rMid, midY = dir.y * rMid, midZ = dir.z * rMid;
        float outX = dir.x * rOut, outY = dir.y * rOut, outZ = dir.z * rOut;

        drawQuadLayer(consumer, pose, inX, inY, inZ, midX, midY, midZ, outX, outY, outZ, normal, thick * 4.0f, r, g, b, (int)(alpha * 0.15f));
        drawQuadLayer(consumer, pose, inX, inY, inZ, midX, midY, midZ, outX, outY, outZ, normal, thick * 2.0f, r, g, b, (int)(alpha * 0.40f));
        drawQuadLayer(consumer, pose, inX, inY, inZ, midX, midY, midZ, outX, outY, outZ, normal, thick * 0.8f, r, g, b, alpha);
    }

    private static void drawQuadLayer(VertexConsumer consumer, Matrix4f pose, float inX, float inY, float inZ, float midX, float midY, float midZ, float outX, float outY, float outZ, Vector3f normal, float thick, int r, int g, int b, int alpha) {
        if (alpha <= 0) return;
        float nx = normal.x * thick, ny = normal.y * thick, nz = normal.z * thick;

        consumer.vertex(pose, inX, inY, inZ).color(r, g, b, 0).endVertex();
        consumer.vertex(pose, midX + nx, midY + ny, midZ + nz).color(r, g, b, alpha).endVertex();
        consumer.vertex(pose, midX - nx, midY - ny, midZ - nz).color(r, g, b, alpha).endVertex();
        consumer.vertex(pose, inX, inY, inZ).color(r, g, b, 0).endVertex();

        consumer.vertex(pose, midX + nx, midY + ny, midZ + nz).color(r, g, b, alpha).endVertex();
        consumer.vertex(pose, outX, outY, outZ).color(r, g, b, 0).endVertex();
        consumer.vertex(pose, outX, outY, outZ).color(r, g, b, 0).endVertex();
        consumer.vertex(pose, midX - nx, midY - ny, midZ - nz).color(r, g, b, alpha).endVertex();
    }

    private static void enqueueLensScreenSpace(PoseStack poseStack, TrueDemonBlackHole demonBlackHole, float centerYOffsetWorld) {
        Minecraft mc = Minecraft.getInstance();

        int screenW = mc.getMainRenderTarget().width;
        int screenH = mc.getMainRenderTarget().height;

        Matrix4f modelView = poseStack.last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();

        LensScratch scratch = TL_LENS.get();

        Vector4f viewCenter = scratch.v0.set(0f, centerYOffsetWorld, 0f, 1f);
        modelView.transform(viewCenter);

        if (viewCenter.z >= 0.0f) return;

        final float minZ = 0.15f;
        float zClamped = viewCenter.z;
        if (zClamped > -minZ) zClamped = -minZ;

        Vector4f clipC = scratch.v1.set(viewCenter.x, viewCenter.y, zClamped, 1.0f);
        projection.transform(clipC);
        if (clipC.w <= 0.00001f) return;

        float invWc = 1.0f / clipC.w;
        float ndcX = clipC.x * invWc;
        float ndcY = clipC.y * invWc;
        float ndcZ = clipC.z * invWc;

        float centerXpx = (ndcX * 0.5f + 0.5f) * screenW;
        float centerYpx = (1.0f - (ndcY * 0.5f + 0.5f)) * screenH;

        float worldRadius = Math.max(demonBlackHole.getBbWidth(), demonBlackHole.getBbHeight()) * 1.05f;

        Vector4f clipR = scratch.v2.set(viewCenter.x + worldRadius, viewCenter.y, zClamped, 1.0f);
        projection.transform(clipR);
        if (clipR.w <= 0.00001f) return;
        float ndcXR = clipR.x / clipR.w;
        float xRpx = (ndcXR * 0.5f + 0.5f) * screenW;
        float radiusPxX = Math.abs(xRpx - centerXpx);

        Vector4f clipU = scratch.v3.set(viewCenter.x, viewCenter.y + worldRadius, zClamped, 1.0f);
        projection.transform(clipU);
        if (clipU.w <= 0.00001f) return;
        float ndcYU = clipU.y / clipU.w;
        float yUpPx = (1.0f - (ndcYU * 0.5f + 0.5f)) * screenH;
        float radiusPxY = Math.abs(yUpPx - centerYpx);

        float radiusPx = Math.max(radiusPxX, radiusPxY);
        if (radiusPx < 2f) return;

        if (centerXpx + radiusPx < 0 || centerXpx - radiusPx > screenW || centerYpx + radiusPx < 0 || centerYpx - radiusPx > screenH) return;

        float lensDepth01 = Mth.clamp(ndcZ * 0.5f + 0.5f, 0f, 1f);
        BlackHoleLensClient.queueLens(BlackHoleLensClient.DEFAULT_STYLE.bake(centerXpx, centerYpx, radiusPx, radiusPx * BlackHoleLensClient.DEFAULT_STYLE.strengthScale, lensDepth01));
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull TrueDemonBlackHole trueDemonCave) {
        return DEMON_CAVE_TEX;
    }

    @Override
    public boolean shouldRender(@NotNull TrueDemonBlackHole trueDemonCave, @NotNull Frustum frustum, double x, double y, double z) {
        var aabb = trueDemonCave.getBoundingBox().inflate(Math.max(trueDemonCave.getBbWidth(), trueDemonCave.getBbHeight()) * 1.8);
        return frustum.isVisible(aabb);
    }

    public static class ModelHolder extends SimplePreparableReloadListener<Object> {
        public static final ModelHolder INSTANCE = new ModelHolder();
        public ObjLoader.TriMesh mesh;
        private boolean attemptedLoad = false;
        private boolean boundsReady = false;
        private float modelW = 1.0f;
        private float modelH = 1.0f;
        private float modelD = 1.0f;
        private float centerFromBottom = 0.5f;

        @Override
        protected @NotNull Object prepare(@NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
            return new Object();
        }

        @Override
        protected void apply(@NotNull Object placeholder, @NotNull ResourceManager resourceManager, @NotNull ProfilerFiller profiler) {
            attemptedLoad = true;
            tryLoad(resourceManager);
            AdorableArmory.LOGGER.info("[blackball] OBJ reloaded: {}", (mesh != null));
        }

        public void ensureLoaded(ResourceManager resourceManager) {
            if (!attemptedLoad) {
                attemptedLoad = true;
                tryLoad(resourceManager);
            }
        }

        public float computeCenterYOffset(float width, float height) {
            if (!boundsReady) {
                return height * 0.5f;
            }

            float s = Math.min(Math.min(width / modelW, height / modelH), width / modelD);
            return centerFromBottom * s;
        }

        public void tryLoad(ResourceManager resourceManager) {
            try {
                boolean flipV = false;
                this.mesh = ObjLoader.load(resourceManager, DEMON_CAVE_OBJ, flipV);
                if (this.mesh != null) {
                    updateBounds(this.mesh);
                } else {
                    boundsReady = false;
                }
            } catch (Exception e) {
                AdorableArmory.LOGGER.error("Failed to load OBJ: {}", DEMON_CAVE_OBJ, e);
                this.mesh = null;
                this.boundsReady = false;
            }
        }

        private void updateBounds(ObjLoader.TriMesh mesh) {
            this.modelW = Math.max(mesh.maxX - mesh.minX, 1e-4f);
            this.modelH = Math.max(mesh.maxY - mesh.minY, 1e-4f);
            this.modelD = Math.max(mesh.maxZ - mesh.minZ, 1e-4f);

            float cy = (mesh.minY + mesh.maxY) * 0.5f;
            this.centerFromBottom = cy - mesh.minY;
            this.boundsReady = true;
        }
    }

    // Absorption rays precomputed
    private static final int ABSORB_RAY_COUNT = 75;
    private static final Vector3f[] ABSORB_DIR = new Vector3f[ABSORB_RAY_COUNT];
    private static final float[] ABSORB_SPEED = new float[ABSORB_RAY_COUNT];
    private static final float[] ABSORB_OFFSET = new float[ABSORB_RAY_COUNT];
    private static final float[] ABSORB_LEN_RAND = new float[ABSORB_RAY_COUNT];
    private static final float[] ABSORB_THICK_RAND = new float[ABSORB_RAY_COUNT];
    private static final ThreadLocal<RayTemps> TL_RAY_TEMPS = ThreadLocal.withInitial(RayTemps::new);
    // Circle LUT caches
    private static final ThreadLocal<CircleLUT> TL_BLOOM_LUT = ThreadLocal.withInitial(CircleLUT::new);
    private static final ThreadLocal<CircleLUT> TL_OCCLUDE_LUT = ThreadLocal.withInitial(CircleLUT::new);
    private static final ThreadLocal<CircuitLUT> TL_CIRCUIT_LUT = ThreadLocal.withInitial(CircuitLUT::new);

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final int LIGHTNING_MAX_GENERATIONS = 5;
    private static final int LIGHTNING_MAX_POINTS = (1 << LIGHTNING_MAX_GENERATIONS) + 1;
    private static final ThreadLocal<LightningScratch> TL_LIGHTNING = ThreadLocal.withInitial(LightningScratch::new);
    private static final ThreadLocal<LensScratch> TL_LENS = ThreadLocal.withInitial(LensScratch::new);

    private static final class FastRandom {
        private long state;

        void setSeed(long seed) {
            this.state = seed;
        }

        long nextLong() {
            long z = (state += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }

        float nextFloat() {
            return (float) ((nextLong() >>> 40) * 0x1.0p-24);
        }

        float nextSignedFloat() {
            return nextFloat() - 0.5f;
        }
    }

    private static final class LightningScratch {
        final Vector3f[] path = new Vector3f[LIGHTNING_MAX_POINTS];
        final Vector3f[] extrusions = new Vector3f[LIGHTNING_MAX_POINTS];
        final float[] segmentLengths = new float[LIGHTNING_MAX_POINTS - 1];

        final Vector3f start = new Vector3f();
        final Vector3f end = new Vector3f();
        final Vector3f tangent = new Vector3f();
        final Vector3f viewDir = new Vector3f();
        final FastRandom rng = new FastRandom();

        LightningScratch() {
            for (int i = 0; i < LIGHTNING_MAX_POINTS; i++) {
                path[i] = new Vector3f();
                extrusions[i] = new Vector3f();
            }
        }
    }

    private static final class LensScratch {
        final Vector4f v0 = new Vector4f();
        final Vector4f v1 = new Vector4f();
        final Vector4f v2 = new Vector4f();
        final Vector4f v3 = new Vector4f();
    }

    private static final class CircleLUT {
        int segments = -1;
        float[] cos = new float[0];
        float[] sin = new float[0];

        void ensure(int seg) {
            if (seg == segments && cos.length == seg + 1 && sin.length == seg + 1) return;

            segments = seg;
            cos = new float[seg + 1];
            sin = new float[seg + 1];

            float angleStep = (float) (2.0 * Math.PI / (double) seg);
            for (int i = 0; i <= seg; i++) {
                float a = i * angleStep;
                cos[i] = Mth.cos(a);
                sin[i] = Mth.sin(a);
            }
        }
    }

    private static final class CircuitLUT {
        int segments = -1;
        float[] cosBase = new float[0];
        float[] sinBase = new float[0];
        float[] angle12 = new float[0]; // baseAngle * 12
        float[] angle2  = new float[0]; // baseAngle * 2

        void ensure(int seg) {
            if (seg == segments && cosBase.length == seg + 1 && sinBase.length == seg + 1 && angle12.length == seg + 1 && angle2.length == seg + 1) return;
            segments = seg;
            cosBase = new float[seg + 1];
            sinBase = new float[seg + 1];
            angle12 = new float[seg + 1];
            angle2  = new float[seg + 1];

            float angleStep = (float) (2.0 * Math.PI / (double) seg);
            for (int i = 0; i <= seg; i++) {
                float baseAngle = i * angleStep;
                cosBase[i] = Mth.cos(baseAngle);
                sinBase[i] = Mth.sin(baseAngle);
                angle12[i] = baseAngle * 12.0f;
                angle2[i]  = baseAngle * 2.0f;
            }
        }
    }

    private static final class RayTemps {
        final Vector3f camLocal = new Vector3f();
        final Vector3f midPos = new Vector3f();
        final Vector3f viewDir = new Vector3f();
        final Vector3f normal = new Vector3f();
    }

    static {
        initAbsorbRayParams();
    }

    private static void initAbsorbRayParams() {
        SplittableRandom rng = new SplittableRandom(114514L);
        for (int i = 0; i < ABSORB_RAY_COUNT; i++) {
            float theta = (float) (rng.nextDouble() * Math.PI * 2.0);
            float u = (float) rng.nextDouble();
            float phi = (float) Math.acos(2.0f * u - 1.0f);

            float dx = Mth.sin(phi) * Mth.cos(theta);
            float dy = Mth.cos(phi);
            float dz = Mth.sin(phi) * Mth.sin(theta);

            ABSORB_DIR[i] = new Vector3f(dx, dy, dz); // unit
            ABSORB_SPEED[i] = 0.0072f + (float)rng.nextDouble() * 0.0146f;
            ABSORB_OFFSET[i] = (float)rng.nextDouble();
            ABSORB_LEN_RAND[i] = (float)rng.nextDouble();
            ABSORB_THICK_RAND[i] = (float)rng.nextDouble();
        }
    }

    private static final class BloomRenderTypes extends RenderStateShard {
        private static final RenderType BLOOM_DISC;
        private static final RenderType BLOOM_RING;
        private static final RenderType OCCLUDE_DISC;
        private static final RenderType OCCLUDE_FEATHER_RING;
        private static final RenderType CIRCUIT_GLOW_RING;
        private static final RenderType BLOOM_QUADS;
        private static final RenderType BLACK_LIGHTNING_CORE;

        static {
            {
                RenderType.CompositeState state = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLightningShader))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false);
                BLOOM_DISC = RenderType.create("bloom_disc", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN, 256, false, true, state);
            }

            {
                RenderType.CompositeState state = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLightningShader))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false);
                BLOOM_RING = RenderType.create("bloom_ring", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP, 8192, false, true, state);
            }

            {
                RenderType.CompositeState state = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                        .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false);
                OCCLUDE_DISC = RenderType.create("black_hole_occlude_disc", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_FAN, 256, false, false, state);
            }

            {
                RenderType.CompositeState state = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false);
                OCCLUDE_FEATHER_RING = RenderType.create("black_hole_occlude_feather_ring", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLE_STRIP, 8192, false, true, state);
            }

            {
                RenderType.CompositeState state = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEyesShader))
                        .setTextureState(new RenderStateShard.TextureStateShard(CIRCUIT_MASK_TEX, false, false))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .createCompositeState(false);
                CIRCUIT_GLOW_RING = RenderType.create("black_hole_circuit_glow_ring", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.TRIANGLE_STRIP, 8192, false, true, state);
            }

            {
                RenderType.CompositeState state = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLightningShader))
                        .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false);
                BLOOM_QUADS = RenderType.create("bloom_quads", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, true, state);
            }

            {
                RenderType.CompositeState state = RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false);
                BLACK_LIGHTNING_CORE = RenderType.create("black_lightning_core", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 2048, false, true, state);
            }
        }

        static RenderType bloomDisc() {
            return BLOOM_DISC;
        }

        static RenderType bloomRing() {
            return BLOOM_RING;
        }

        static RenderType occludeDisc() {
            return OCCLUDE_DISC;
        }

        static RenderType occludeFeatherRing() {
            return OCCLUDE_FEATHER_RING;
        }

        static RenderType circuitGlowRing() {
            return CIRCUIT_GLOW_RING;
        }

        static RenderType bloomQuads() {
            return BLOOM_QUADS;
        }

        static RenderType blackLightningCore() {
            return BLACK_LIGHTNING_CORE;
        }

        private BloomRenderTypes(String string, Runnable runnable, Runnable runnable1) {
            super(string, runnable, runnable1);
        }
    }
}
