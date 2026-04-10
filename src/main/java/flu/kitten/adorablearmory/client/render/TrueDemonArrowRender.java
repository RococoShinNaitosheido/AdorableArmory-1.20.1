package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.entity.weapons.TrueDemonArrow;
import net.minecraft.Util;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class TrueDemonArrowRender extends ArrowRenderer<TrueDemonArrow> {
    private static final ResourceLocation TRUE_MAGIC_ARROW = new ResourceLocation(AdorableArmory.MODID, "textures/entity/true_magic_arrow.png");
    private static final ResourceLocation TRUE_MAGIC_TIPPED_ARROW = new ResourceLocation(AdorableArmory.MODID, "textures/entity/true_magic_tipped_arrow.png");
    private static final int ARROW_COLOR = 0xFF00FF; // Magenta
    private static final int ARROW_ALPHA = 200; // 80%
    private static final float TRAIL_BASE_WIDTH = 0.10F;
    private static final boolean TRAIL_USE_ADDITIVE = true; // true = 发光; false = 普通半透明
    private static final int TUBE_SIDES = 8;
    private static final float SHRINK_FEATHER = 3.56f;
    private static final float EDGE_FEATHER = 0.75f;
    private static final double POINT_EPS2 = 1.0e-6;

    public TrueDemonArrowRender(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override public void render(@NotNull TrueDemonArrow arrow, float yaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        float scale = TrueDemonArrow.DEMON_ARROW_SCALE;
        poseStack.scale(scale, scale, scale);
        poseStack.pushPose();
        applyArrowPose(arrow, partialTicks, poseStack);

        VertexConsumer bodyConsumer = bufferSource.getBuffer(TrueDemonArrowRenderType.trueDemonArrowLight(getTextureLocation(arrow)));

        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMat = pose.pose();
        Matrix3f normalMat = pose.normal();

        final int fullBright = 15728880;

        renderArrowBody(poseMat, normalMat, bodyConsumer, fullBright);
        renderArrowFins(poseStack, bodyConsumer, fullBright);

        poseStack.popPose();
        renderTrailTube(arrow, partialTicks, poseStack, bufferSource);
    }

    private void applyArrowPose(TrueDemonArrow arrow, float partialTicks, PoseStack poseStack) {
        poseStack.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, arrow.yRotO, arrow.getYRot()) - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, arrow.xRotO, arrow.getXRot())));

        float shake = (float) arrow.shakeTime - partialTicks;
        if (shake > 0.0F) {
            float wobble = -Mth.sin(shake * 3.0F) * shake;
            poseStack.mulPose(Axis.ZP.rotationDegrees(wobble));
        }
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.scale(0.05625F, 0.05625F, 0.05625F);
        poseStack.translate(-4.0F, 0.0F, 0.0F);
    }

    private void renderArrowBody(Matrix4f poseMat, Matrix3f normalMat, VertexConsumer vc, int light) {
        vertexColored(poseMat, normalMat, vc, -7, -2, -2, 0.0F, 0.15625F, ARROW_COLOR, -1, 0, 0, light);
        vertexColored(poseMat, normalMat, vc, -7, -2,  2, 0.15625F, 0.15625F, ARROW_COLOR, -1, 0, 0, light);
        vertexColored(poseMat, normalMat, vc, -7,  2,  2, 0.15625F, 0.3125F,  ARROW_COLOR, -1, 0, 0, light);
        vertexColored(poseMat, normalMat, vc, -7,  2, -2, 0.0F, 0.3125F,  ARROW_COLOR, -1, 0, 0, light);
        vertexColored(poseMat, normalMat, vc, -7,  2, -2, 0.0F, 0.15625F, ARROW_COLOR,  1, 0, 0, light);
        vertexColored(poseMat, normalMat, vc, -7,  2,  2, 0.15625F, 0.15625F, ARROW_COLOR,  1, 0, 0, light);
        vertexColored(poseMat, normalMat, vc, -7, -2,  2, 0.15625F, 0.3125F,  ARROW_COLOR,  1, 0, 0, light);
        vertexColored(poseMat, normalMat, vc, -7, -2, -2, 0.0F, 0.3125F,  ARROW_COLOR,  1, 0, 0, light);
    }

    private void renderArrowFins(PoseStack poseStack, VertexConsumer vc, int light) {
        for (int i = 0; i < 4; i++) {
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            PoseStack.Pose pose = poseStack.last();
            Matrix4f poseMat = pose.pose();
            Matrix3f normalMat = pose.normal();

            vertexColored(poseMat, normalMat, vc, -8, -2, 0, 0.0F, 0.0F,       ARROW_COLOR, 0, 1, 0, light);
            vertexColored(poseMat, normalMat, vc,  8, -2, 0, 0.5F, 0.0F,       ARROW_COLOR, 0, 1, 0, light);
            vertexColored(poseMat, normalMat, vc,  8,  2, 0, 0.5F, 0.15625F,   ARROW_COLOR, 0, 1, 0, light);
            vertexColored(poseMat, normalMat, vc, -8,  2, 0, 0.0F, 0.15625F,   ARROW_COLOR, 0, 1, 0, light);
        }
    }

    private void renderTrailTube(TrueDemonArrow entity, float partialTicks, PoseStack poseStack, MultiBufferSource buffer) {
        float visibleLen = entity.getTrailVisibleLen(partialTicks);
        if (visibleLen < 0.001f) return;

        var rawTrail = entity.getTrail();
        if (rawTrail == null || rawTrail.size() < 2) return;

        Vec3 basePos = entity.getPosition(partialTicks);

        List<TrailPoint> points = new ArrayList<>(52);
        points.add(new TrailPoint(basePos, 0f));

        Vec3 forward = pickForward(entity, partialTicks);

        boolean foundBehind = false;
        for (var sample : rawTrail) {
            Vec3 p = new Vec3(sample.x, sample.y, sample.z);

            if (!foundBehind) {
                double ahead = p.subtract(basePos).dot(forward);
                if (ahead > 0.012) continue;
                foundBehind = true;
            }

            points.add(new TrailPoint(p, sample.age + partialTicks));
            if (points.size() >= 1 + 48) break;
        }

        points = compactPoints(points);
        if (points.size() < 4) return;

        List<TrailPoint> smoothed = smoothPath(points);
        smoothed = compactPoints(smoothed);

        int total = smoothed.size();
        if (total < 2) return;

        double[] cum = new double[total];
        cum[0] = 0.0;

        double maxDist = visibleLen + SHRINK_FEATHER + 0.25f;
        int n = 1;
        for (int i = 1; i < total; i++) {
            cum[i] = cum[i - 1] + smoothed.get(i).pos.distanceTo(smoothed.get(i - 1).pos);
            if (cum[i] <= maxDist) n = i + 1;
            else break;
        }
        if (n < 2) return;

        List<TrailPoint> path = smoothed.subList(0, n);

        Vec3[] right = new Vec3[n];
        Vec3[] up = new Vec3[n];
        computeRMF(path, right, up);

        float[] radius = new float[n];
        float[] alpha = new float[n];
        float[] rr = new float[n], gg = new float[n], bb = new float[n];

        float widthMul = Mth.clamp(0.75f + 0.25f * (visibleLen / 12.0f), 0.75f, 1.0f);

        for (int k = 0; k < n; k++) {
            float dist = (float) cum[k];

            float u = dist / Math.max(visibleLen, 1.0e-6f); // 0-1
            u = Mth.clamp(u, 0f, 1f);

            float base = TRAIL_BASE_WIDTH * widthMul;
            float tailScale = 0.85f + 0.15f * (1.0f - (float) Math.pow(u, 2.0));
            radius[k] = 0.5f * base * tailScale;

            float aLen = tailAlpha(u);
            float a = aLen * lengthMask(dist, visibleLen);
            alpha[k] = a;

            int color = calculateTrailColor(u);
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            if (TRAIL_USE_ADDITIVE) {
                r *= a; g *= a; b *= a;
            }

            rr[k] = r; gg[k] = g; bb[k] = b;
        }

        Matrix4f pose = poseStack.last().pose();
        VertexConsumer consumer = buffer.getBuffer(AdorableArmoryShaders.LIGHTNING);

        for (int k = 0; k < n - 1; k++) {
            Vec3 c0 = path.get(k).pos;
            Vec3 c1 = path.get(k + 1).pos;

            for (int s = 0; s < TUBE_SIDES; s++) {
                double a0 = 2.0 * Math.PI * s / TUBE_SIDES;
                double a1 = 2.0 * Math.PI * (s + 1) / TUBE_SIDES;

                Vec3 off0A = right[k].scale(Math.cos(a0) * radius[k]).add(up[k].scale(Math.sin(a0) * radius[k]));
                Vec3 off0B = right[k].scale(Math.cos(a1) * radius[k]).add(up[k].scale(Math.sin(a1) * radius[k]));
                Vec3 off1A = right[k + 1].scale(Math.cos(a0) * radius[k + 1]).add(up[k + 1].scale(Math.sin(a0) * radius[k + 1]));
                Vec3 off1B = right[k + 1].scale(Math.cos(a1) * radius[k + 1]).add(up[k + 1].scale(Math.sin(a1) * radius[k + 1]));

                Vec3 v0 = c0.add(off0A).subtract(basePos);
                Vec3 v1 = c0.add(off0B).subtract(basePos);
                Vec3 v2 = c1.add(off1B).subtract(basePos);
                Vec3 v3 = c1.add(off1A).subtract(basePos);

                // tube quad
                consumer.vertex(pose, (float) v0.x, (float) v0.y, (float) v0.z).color(rr[k], gg[k], bb[k], alpha[k]).endVertex();
                consumer.vertex(pose, (float) v1.x, (float) v1.y, (float) v1.z).color(rr[k], gg[k], bb[k], alpha[k]).endVertex();
                consumer.vertex(pose, (float) v2.x, (float) v2.y, (float) v2.z).color(rr[k + 1], gg[k + 1], bb[k + 1], alpha[k + 1]).endVertex();
                consumer.vertex(pose, (float) v3.x, (float) v3.y, (float) v3.z).color(rr[k + 1], gg[k + 1], bb[k + 1], alpha[k + 1]).endVertex();

                float r0o = radius[k] * (1f + EDGE_FEATHER);
                float r1o = radius[k + 1] * (1f + EDGE_FEATHER);

                Vec3 off0A_o = right[k].scale(Math.cos(a0) * r0o).add(up[k].scale(Math.sin(a0) * r0o));
                Vec3 off0B_o = right[k].scale(Math.cos(a1) * r0o).add(up[k].scale(Math.sin(a1) * r0o));
                Vec3 off1A_o = right[k + 1].scale(Math.cos(a0) * r1o).add(up[k + 1].scale(Math.sin(a0) * r1o));
                Vec3 off1B_o = right[k + 1].scale(Math.cos(a1) * r1o).add(up[k + 1].scale(Math.sin(a1) * r1o));

                Vec3 w0 = c0.add(off0A_o).subtract(basePos);
                Vec3 w1 = c0.add(off0B_o).subtract(basePos);
                Vec3 w2 = c1.add(off1B_o).subtract(basePos);
                Vec3 w3 = c1.add(off1A_o).subtract(basePos);

                // feather quads
                consumer.vertex(pose, (float) v0.x, (float) v0.y, (float) v0.z).color(rr[k], gg[k], bb[k], alpha[k]).endVertex();
                consumer.vertex(pose, (float) w0.x, (float) w0.y, (float) w0.z).color(0f, 0f, 0f, 0f).endVertex();
                consumer.vertex(pose, (float) w2.x, (float) w2.y, (float) w2.z).color(0f, 0f, 0f, 0f).endVertex();
                consumer.vertex(pose, (float) v2.x, (float) v2.y, (float) v2.z).color(rr[k + 1], gg[k + 1], bb[k + 1], alpha[k + 1]).endVertex();

                consumer.vertex(pose, (float) w1.x, (float) w1.y, (float) w1.z).color(0f, 0f, 0f, 0f).endVertex();
                consumer.vertex(pose, (float) v1.x, (float) v1.y, (float) v1.z).color(rr[k], gg[k], bb[k], alpha[k]).endVertex();
                consumer.vertex(pose, (float) v3.x, (float) v3.y, (float) v3.z).color(rr[k + 1], gg[k + 1], bb[k + 1], alpha[k + 1]).endVertex();
                consumer.vertex(pose, (float) w3.x, (float) w3.y, (float) w3.z).color(0f, 0f, 0f, 0f).endVertex();
            }
        }
    }

    private static Vec3 pickForward(TrueDemonArrow entity, float partialTicks) {
        Vec3 movement = entity.getDeltaMovement();
        if (movement.lengthSqr() > 1.0e-8) {
            return movement.normalize();
        }

        Vec3 view = entity.getViewVector(partialTicks);
        if (view.lengthSqr() < 1.0e-8) view = new Vec3(0, 0, 1);
        return view.normalize();
    }

    private static float lengthMask(float dist, float visibleLen) {
        float t0 = visibleLen - SHRINK_FEATHER;
        if (dist <= t0) return 1f;
        if (dist >= visibleLen) return 0f;
        float x = (dist - t0) / Math.max(SHRINK_FEATHER, 1e-6f);
        return 1f - (x * x * (3f - 2f * x));
    }

    private static Vec3 rotateRodriguez(Vec3 v, Vec3 axis, double angle) {
        double c = Math.cos(angle), s = Math.sin(angle);
        Vec3 term1 = v.scale(c);
        Vec3 term2 = axis.cross(v).scale(s);
        Vec3 term3 = axis.scale(axis.dot(v) * (1.0 - c));
        return term1.add(term2).add(term3);
    }

    private static Vec3 initialNormal(Vec3 tangent) {
        Vec3 refUp = (Math.abs(tangent.y) < 0.90) ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 n = refUp.cross(tangent);
        if (n.lengthSqr() < 1e-12) n = new Vec3(0, 0, 1);
        return n.normalize();
    }

    private static void computeRMF(List<TrailPoint> pts, Vec3[] right, Vec3[] up) {
        int n = pts.size();
        if (n < 2) return;

        Vec3[] t = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 pPrev = pts.get(Math.max(0, i - 1)).pos;
            Vec3 pNext = pts.get(Math.min(n - 1, i + 1)).pos;

            Vec3 tan = pNext.subtract(pPrev);
            if (tan.lengthSqr() < 1e-10) tan = new Vec3(0, 0, 1);
            t[i] = tan.normalize();
        }

        Vec3 n0 = initialNormal(t[0]);
        right[0] = n0;
        up[0] = t[0].cross(n0).normalize();

        Vec3 curN = n0;
        for (int i = 1; i < n; i++) {
            Vec3 tPrev = t[i - 1];
            Vec3 tCur = t[i];

            double cos = Mth.clamp(tPrev.dot(tCur), -1.0, 1.0);
            Vec3 axis = tPrev.cross(tCur);
            double axisLen2 = axis.lengthSqr();

            if (axisLen2 > 1e-12) {
                axis = axis.scale(1.0 / Math.sqrt(axisLen2));
                double ang = Math.acos(cos);
                curN = rotateRodriguez(curN, axis, ang);
            } else if (cos < -0.9999) {
                // 180° 反向：补一个 PI 旋转，不然 frame 会跳变
                Vec3 axis2 = curN.cross(tPrev);
                if (axis2.lengthSqr() < 1e-12) axis2 = initialNormal(tPrev);
                axis2 = axis2.normalize();
                curN = rotateRodriguez(curN, axis2, Math.PI);
            }

            // 正交化 + 归一化
            curN = curN.subtract(tCur.scale(curN.dot(tCur)));
            if (curN.lengthSqr() < 1e-12) curN = initialNormal(tCur);
            curN = curN.normalize();

            right[i] = curN;
            up[i] = tCur.cross(curN).normalize();
        }
    }

    private static List<TrailPoint> compactPoints(List<TrailPoint> in) {
        ArrayList<TrailPoint> out = new ArrayList<>(in.size());
        TrailPoint prev = null;

        for (TrailPoint p : in) {
            if (prev == null || p.pos.distanceToSqr(prev.pos) > POINT_EPS2) {
                out.add(p);
                prev = p;
            }
        }
        return out;
    }

    private List<TrailPoint> smoothPath(List<TrailPoint> points) {
        if (points.size() < 4) return points;

        List<TrailPoint> out = new ArrayList<>();
        out.add(points.get(0));

        for (int i = 0; i < points.size() - 1; i++) {
            TrailPoint p0 = points.get(Math.max(0, i - 1));
            TrailPoint p1 = points.get(i);
            TrailPoint p2 = points.get(Math.min(points.size() - 1, i + 1));
            TrailPoint p3 = points.get(Math.min(points.size() - 1, i + 2));

            final int segment = 6;
            for (int j = 1; j <= segment; j++) {
                float t = j / (float) segment;
                Vec3 p = catmullRomCentripetal(p0.pos, p1.pos, p2.pos, p3.pos, t);
                float age = Mth.lerp(t, p1.age, p2.age);
                out.add(new TrailPoint(p, age));
            }
        }

        out.add(points.get(points.size() - 1));
        return out;
    }

    private Vec3 catmullRomCentripetal(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float t) {
        final double a = 0.5; // centripetal
        double t0 = 0.0;
        double t1 = t0 + Math.pow(p0.distanceTo(p1), a);
        double t2 = t1 + Math.pow(p1.distanceTo(p2), a);
        double t3 = t2 + Math.pow(p2.distanceTo(p3), a);

        if (t1 - t0 < 1e-4) t1 = t0 + 1e-4;
        if (t2 - t1 < 1e-4) t2 = t1 + 1e-4;
        if (t3 - t2 < 1e-4) t3 = t2 + 1e-4;

        double tt = Mth.lerp(t, t1, t2);

        Vec3 A1 = p0.scale((float) ((t1 - tt) / (t1 - t0))).add(p1.scale((float) ((tt - t0) / (t1 - t0))));
        Vec3 A2 = p1.scale((float) ((t2 - tt) / (t2 - t1))).add(p2.scale((float) ((tt - t1) / (t2 - t1))));
        Vec3 A3 = p2.scale((float) ((t3 - tt) / (t3 - t2))).add(p3.scale((float) ((tt - t2) / (t3 - t2))));

        Vec3 B1 = A1.scale((float) ((t2 - tt) / (t2 - t0))).add(A2.scale((float) ((tt - t0) / (t2 - t0))));
        Vec3 B2 = A2.scale((float) ((t3 - tt) / (t3 - t1))).add(A3.scale((float) ((tt - t1) / (t3 - t1))));

        return B1.scale((float) ((t2 - tt) / (t2 - t1))).add(B2.scale((float) ((tt - t1) / (t2 - t1))));
    }

    private static float tailAlpha(float u) {
        u = Mth.clamp(u, 0f, 1f);

        if (u < 0.20f) return 1.0f;

        if (u < 0.75f) {
            float t = (u - 0.20f) / 0.55f; // 0..1
            return 1.0f - t * 0.35f;       // 1.0 -> 0.65
        }

        float t = (u - 0.75f) / 0.25f;    // 0..1
        float ease = 1.0f - (float) Math.pow(t, 1.32);
        return 0.65f * ease;              // 0.65 -> 0
    }

    private int calculateTrailColor(float t) {
        t = Mth.clamp(t, 0f, 1f);

        final int COL_PURPLE = 0xAA00FF;
        final int COL_PINK = 0xFF69B4;
        final int COL_WHITE = 0xFFFFFF;

        if (t < 0.5f) return leapRGB(COL_PURPLE, COL_PINK, t / 0.5f);
        return leapRGB(COL_PINK, COL_WHITE, (t - 0.5f) / 0.5f);
    }

    private static int leapRGB(int c0, int c1, float k) {
        int r0 = (c0 >> 16) & 0xFF, g0 = (c0 >> 8) & 0xFF, b0 = c0 & 0xFF;
        int r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;

        int r = Mth.clamp(Math.round(Mth.lerp(k, r0, r1)), 0, 255);
        int g = Mth.clamp(Math.round(Mth.lerp(k, g0, g1)), 0, 255);
        int b = Mth.clamp(Math.round(Mth.lerp(k, b0, b1)), 0, 255);

        return (r << 16) | (g << 8) | b;
    }

    @SuppressWarnings("all")
    private void vertexColored(Matrix4f poseMat, Matrix3f normalMat, VertexConsumer consumer, int x, int y, int z, float u, float v, int packedRGB, int nx, int ny, int nz, int light) {
        int r = (packedRGB >> 16) & 0xFF;
        int g = (packedRGB >> 8) & 0xFF;
        int b = packedRGB & 0xFF;
        consumer.vertex(poseMat, (float) x, (float) y, (float) z).color(r, g, b, ARROW_ALPHA).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(normalMat, (float) nx, (float) nz, (float) ny).endVertex();
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull TrueDemonArrow arrow) {
        return arrow.getColor() > 0 ? TRUE_MAGIC_TIPPED_ARROW : TRUE_MAGIC_ARROW;
    }

    @Override
    public boolean shouldRender(@NotNull TrueDemonArrow arrow, @NotNull Frustum frustum, double x, double y, double z) {
        return super.shouldRender(arrow, frustum, x, y, z);
    }

    private record TrailPoint(Vec3 pos, float age) {}

    private static final class TrueDemonArrowRenderType extends RenderType {
        private static final RenderStateShard.TransparencyStateShard ADDITIVE_ALPHA =
                new RenderStateShard.TransparencyStateShard(
                        "additive_alpha",
                        () -> {
                            RenderSystem.enableBlend();
                            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                        },
                        () -> {
                            RenderSystem.disableBlend();
                            RenderSystem.defaultBlendFunc();
                        }
                );

        private static final Function<ResourceLocation, RenderType> TRUE_DEMON_ARROW_LIGHT = Util.memoize((ResourceLocation texture) -> RenderType.create("true_demon_arrow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true, RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(ADDITIVE_ALPHA)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                .createCompositeState(true)
        ));

        private static RenderType trueDemonArrowLight(ResourceLocation location) {
            return TRUE_DEMON_ARROW_LIGHT.apply(location);
        }

        private TrueDemonArrowRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupTask, Runnable clearTask) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupTask, clearTask);
        }
    }
}
