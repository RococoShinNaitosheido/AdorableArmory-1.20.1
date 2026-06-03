package flu.kitten.adorablearmory.client.render.dimensional;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.client.render.dimensional.config.DimensionalSlashTuning;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DimensionalSlashWorldShard {
    private static final int MIN_POLYGON_POINTS = 4;
    private static final int MAX_POLYGON_POINTS = 7;

    private final Vec3 origin;
    private final Vec3 velocity;
    private final Vec3 axisA;
    private final Vec3 axisB;
    private final Vec3 spinAxis;
    private final List<LocalPoint> polygon;
    private final float size;
    private final float aspect;
    private final float angularA;
    private final float angularB;
    private final float phase;
    private final int lifetime;
    private int age;

    public DimensionalSlashWorldShard(Vec3 origin, Vec3 velocity, Vec3 axisA, Vec3 axisB, Vec3 spinAxis, float size, float aspect, float angularA, float angularB, float phase, long shapeSeed, int lifetime) {
        this.origin = origin;
        this.velocity = velocity;
        this.axisA = safeNormalize(axisA, new Vec3(1.0, 0.0, 0.0));
        this.axisB = safeNormalize(axisB, new Vec3(0.0, 1.0, 0.0));
        this.spinAxis = safeNormalize(spinAxis, new Vec3(0.0, 1.0, 0.0));
        this.polygon = buildPolygon(shapeSeed);
        this.size = Math.max(0.02f, size);
        this.aspect = Math.max(0.15f, aspect);
        this.angularA = angularA;
        this.angularB = angularB;
        this.phase = phase;
        this.lifetime = Math.max(1, lifetime);
    }

    public void tick() {
        age++;
    }

    public boolean isAlive() {
        return age < lifetime;
    }

    public boolean render(PoseStack poseStack, BufferBuilder builder, Vec3 camera, float partialTick) {
        float t = age + partialTick;
        float life = t / lifetime;
        if (life < 0.0f || life >= 1.0f) return false;

        float alpha = alphaFade(life) * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_ALPHA;
        if (alpha <= 0.0001f) return false;

        Vec3 gravity = new Vec3(0.0, -DimensionalSlashTuning.WorldSlash.WORLD_SHARD_GRAVITY * t * t, 0.0);
        Vec3 center = origin.add(velocity.scale(t)).add(gravity).subtract(camera);
        Vec3 a = rotateAround(axisA, spinAxis, t * angularA).scale(size * (0.65f + life * 0.42f));
        Vec3 b = rotateAround(axisB, spinAxis, t * angularB).scale(size * aspect * (0.55f + life * 0.32f));
        Vec3 normal = safeNormalize(a.cross(b), spinAxis);
        float thickness = DimensionalSlashTuning.WorldSlash.WORLD_SHARD_THICKNESS
                * (0.72f + phase * 0.46f)
                * (0.86f + life * 0.22f);
        Vec3 frontOffset = normal.scale(thickness * 0.5f);
        Vec3 backOffset = frontOffset.scale(-1.0);

        Matrix4f matrix = poseStack.last().pose();
        float shade = 0.28f + phase * 0.38f;
        float backShade = Mth.clamp(shade + 0.18f, 0.0f, 1.0f);
        float sideShade = Mth.clamp(shade + 0.42f, 0.0f, 1.0f);
        float faceAlpha = alpha * 0.96f;
        float backAlpha = alpha * 0.42f;
        float sideAlpha = alpha * 0.68f;
        Vec3 backNormal = normal.scale(-1.0);
        Vec3 faceCenterFront = center.add(frontOffset);
        Vec3 faceCenterBack = center.add(backOffset);

        for (int i = 0; i < polygon.size(); i++) {
            LocalPoint p0 = polygon.get(i);
            LocalPoint p1 = polygon.get((i + 1) % polygon.size());
            Vec3 front0 = vertexPosition(center, a, b, frontOffset, p0);
            Vec3 front1 = vertexPosition(center, a, b, frontOffset, p1);
            Vec3 back0 = vertexPosition(center, a, b, backOffset, p0);
            Vec3 back1 = vertexPosition(center, a, b, backOffset, p1);
            Vec3 sideNormal = safeNormalize(front1.subtract(front0).cross(normal), normal);

            addVertex(builder, matrix, faceCenterFront, 0.5f, 0.5f, 0.05f, shade, phase, faceAlpha, normal);
            addVertex(builder, matrix, front0, p0.u, p0.v, 0.82f, shade, phase, faceAlpha, normal);
            addVertex(builder, matrix, front1, p1.u, p1.v, 0.82f, shade, phase, faceAlpha, normal);

            addVertex(builder, matrix, faceCenterBack, 0.5f, 0.5f, 0.18f, backShade, phase, backAlpha, backNormal);
            addVertex(builder, matrix, back1, p1.u, p1.v, 0.72f, backShade, phase, backAlpha, backNormal);
            addVertex(builder, matrix, back0, p0.u, p0.v, 0.72f, backShade, phase, backAlpha, backNormal);

            addVertex(builder, matrix, front0, p0.u, p0.v, 1.0f, sideShade, phase, sideAlpha, sideNormal);
            addVertex(builder, matrix, back0, p0.u, p0.v, 1.0f, sideShade, phase, sideAlpha, sideNormal);
            addVertex(builder, matrix, back1, p1.u, p1.v, 1.0f, sideShade, phase, sideAlpha, sideNormal);

            addVertex(builder, matrix, front0, p0.u, p0.v, 1.0f, sideShade, phase, sideAlpha, sideNormal);
            addVertex(builder, matrix, back1, p1.u, p1.v, 1.0f, sideShade, phase, sideAlpha, sideNormal);
            addVertex(builder, matrix, front1, p1.u, p1.v, 1.0f, sideShade, phase, sideAlpha, sideNormal);
        }
        return true;
    }

    public boolean renderBloomMask(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, float partialTick) {
        float t = age + partialTick;
        float life = t / lifetime;
        if (life < 0.0f || life >= 1.0f) return false;

        float alpha = alphaFade(life) * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_BLOOM_ALPHA;
        if (alpha <= 0.0001f) return false;

        Vec3 gravity = new Vec3(0.0, -DimensionalSlashTuning.WorldSlash.WORLD_SHARD_GRAVITY * t * t, 0.0);
        Vec3 center = origin.add(velocity.scale(t)).add(gravity).subtract(camera);
        Vec3 a = rotateAround(axisA, spinAxis, t * angularA).scale(size * (0.65f + life * 0.42f));
        Vec3 b = rotateAround(axisB, spinAxis, t * angularB).scale(size * aspect * (0.55f + life * 0.32f));
        Vec3 normal = safeNormalize(a.cross(b), spinAxis);
        float thickness = DimensionalSlashTuning.WorldSlash.WORLD_SHARD_THICKNESS
                * (0.72f + phase * 0.46f)
                * (0.86f + life * 0.22f);
        Vec3 frontOffset = normal.scale(thickness * 0.5f);
        float trailT = Math.max(0.0f, t - DimensionalSlashTuning.WorldSlash.WORLD_SHARD_MOTION_BLUR_TICKS);
        boolean blurActive = t - trailT > 0.05f;
        float trailLife = trailT / lifetime;
        Vec3 trailGravity = new Vec3(0.0, -DimensionalSlashTuning.WorldSlash.WORLD_SHARD_GRAVITY * trailT * trailT, 0.0);
        Vec3 trailCenter = origin.add(velocity.scale(trailT)).add(trailGravity).subtract(camera);
        Vec3 trailA = rotateAround(axisA, spinAxis, trailT * angularA).scale(size * (0.65f + trailLife * 0.42f));
        Vec3 trailB = rotateAround(axisB, spinAxis, trailT * angularB).scale(size * aspect * (0.55f + trailLife * 0.32f));
        Vec3 trailNormal = safeNormalize(trailA.cross(trailB), spinAxis);
        float trailThickness = DimensionalSlashTuning.WorldSlash.WORLD_SHARD_THICKNESS
                * (0.72f + phase * 0.46f)
                * (0.86f + trailLife * 0.22f);
        Vec3 trailFrontOffset = trailNormal.scale(trailThickness * 0.5f);

        Matrix4f matrix = poseStack.last().pose();
        float width = Math.max(0.006f, size * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_BLOOM_WIDTH * (0.82f + phase * 0.38f));
        boolean emitted = false;
        for (int i = 0; i < polygon.size(); i++) {
            LocalPoint p0 = polygon.get(i);
            LocalPoint p1 = polygon.get((i + 1) % polygon.size());
            Vec3 front0 = vertexPosition(center, a, b, frontOffset, p0);
            Vec3 front1 = vertexPosition(center, a, b, frontOffset, p1);
            Vec3 edgeDir = safeNormalize(front1.subtract(front0), a);
            Vec3 edgeSide = safeNormalize(normal.cross(edgeDir), b);
            Vec3 edgeMid = front0.add(front1).scale(0.5);
            Vec3 beamDir = edgeMid.subtract(center);
            beamDir = beamDir.subtract(normal.scale(beamDir.dot(normal)));
            beamDir = safeNormalize(beamDir, edgeSide);
            Vec3 beamSide = safeNormalize(beamDir.cross(normal), edgeDir);
            float leakAlpha = alpha
                    * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_CRACK_LIGHT_ALPHA
                    * (0.58f + (1.0f - life) * 0.42f);
            float leakLength = Math.max(0.012f, size * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_CRACK_LIGHT_LENGTH * (0.74f + phase * 0.52f));
            float leakWidth = Math.max(width * 0.72f, size * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_CRACK_LIGHT_WIDTH * (0.70f + phase * 0.45f));

            addBloomBeam(consumer, matrix, front0, front1, beamDir, beamSide, leakLength * 1.28f, leakWidth * 1.85f, 0.38f, 0.74f, 1.0f, leakAlpha * 0.22f);
            addBloomBeam(consumer, matrix, front0, front1, beamDir, beamSide, leakLength * 0.68f, leakWidth * 0.72f, 0.92f, 0.98f, 1.0f, leakAlpha * 0.40f);

            if (blurActive) {
                Vec3 trail0 = vertexPosition(trailCenter, trailA, trailB, trailFrontOffset, p0);
                Vec3 trail1 = vertexPosition(trailCenter, trailA, trailB, trailFrontOffset, p1);
                float sweep = (float) Math.max(front0.subtract(trail0).length(), front1.subtract(trail1).length());
                if (sweep > 0.002f) {
                    float motionAlpha = alpha
                            * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_MOTION_BLUR_ALPHA
                            * Mth.clamp(sweep / Math.max(size * 0.55f, 0.001f), 0.18f, 1.0f);
                    addBloomQuad(consumer, matrix, trail0, trail1, front1, front0, 0.32f, 0.74f, 1.0f, motionAlpha * 0.32f);
                    addBloomQuad(consumer, matrix, trail0, trail1, front1, front0, 1.0f, 0.58f, 1.0f, motionAlpha * 0.18f);
                }
            }
            addBloomRibbon(consumer, matrix, front0, front1, edgeSide, width * 1.75f, 0.45f, 0.82f, 1.0f, alpha * 0.34f);
            addBloomRibbon(consumer, matrix, front0, front1, edgeSide, width * 0.72f, 1.0f, 0.72f, 1.0f, alpha * 0.52f);
            emitted = true;
        }
        return emitted;
    }

    private static Vec3 vertexPosition(Vec3 center, Vec3 a, Vec3 b, Vec3 extrusion, LocalPoint point) {
        return center.add(a.scale(point.x)).add(b.scale(point.y)).add(extrusion);
    }

    private static void addVertex(BufferBuilder builder, Matrix4f matrix, Vec3 p, float u, float v, float edge, float shade, float phase, float alpha, Vec3 normal) {
        builder.vertex(matrix, (float) p.x, (float) p.y, (float) p.z)
                .color(edge, shade, phase, Mth.clamp(alpha, 0.0f, 1.0f))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0xF000F0)
                .normal((float) normal.x, (float) normal.y, (float) normal.z)
                .endVertex();
    }

    private static void addBloomRibbon(VertexConsumer consumer, Matrix4f matrix, Vec3 p0, Vec3 p1, Vec3 side, float width, float r, float g, float b, float alpha) {
        Vec3 half = side.scale(width * 0.5f);
        Vec3 q0 = p0.add(half);
        Vec3 q1 = p0.subtract(half);
        Vec3 q2 = p1.subtract(half);
        Vec3 q3 = p1.add(half);
        float safeAlpha = Mth.clamp(alpha, 0.0f, 1.0f);

        consumer.vertex(matrix, (float) q0.x, (float) q0.y, (float) q0.z).color(r, g, b, safeAlpha).endVertex();
        consumer.vertex(matrix, (float) q1.x, (float) q1.y, (float) q1.z).color(r, g, b, safeAlpha).endVertex();
        consumer.vertex(matrix, (float) q2.x, (float) q2.y, (float) q2.z).color(r, g, b, safeAlpha).endVertex();
        consumer.vertex(matrix, (float) q3.x, (float) q3.y, (float) q3.z).color(r, g, b, safeAlpha).endVertex();
    }

    private static void addBloomBeam(VertexConsumer consumer, Matrix4f matrix, Vec3 p0, Vec3 p1, Vec3 direction, Vec3 side, float length, float endWidth, float r, float g, float blue, float alpha) {
        float safeAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
        Vec3 tip = direction.scale(length);
        Vec3 spread = side.scale(endWidth * 0.5f);
        Vec3 q0 = p0;
        Vec3 q1 = p1;
        Vec3 q2 = p1.add(tip).add(spread);
        Vec3 q3 = p0.add(tip).subtract(spread);

        consumer.vertex(matrix, (float) q0.x, (float) q0.y, (float) q0.z).color(r, g, blue, safeAlpha).endVertex();
        consumer.vertex(matrix, (float) q1.x, (float) q1.y, (float) q1.z).color(r, g, blue, safeAlpha).endVertex();
        consumer.vertex(matrix, (float) q2.x, (float) q2.y, (float) q2.z).color(r, g, blue, 0.0f).endVertex();
        consumer.vertex(matrix, (float) q3.x, (float) q3.y, (float) q3.z).color(r, g, blue, 0.0f).endVertex();
    }

    private static void addBloomQuad(VertexConsumer consumer, Matrix4f matrix, Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, float r, float g, float blue, float alpha) {
        float safeAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
        consumer.vertex(matrix, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, blue, safeAlpha).endVertex();
        consumer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, blue, safeAlpha).endVertex();
        consumer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, blue, safeAlpha).endVertex();
        consumer.vertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z).color(r, g, blue, safeAlpha).endVertex();
    }

    private static List<LocalPoint> buildPolygon(long seed) {
        Random random = new Random(seed);
        int count = MIN_POLYGON_POINTS + random.nextInt(MAX_POLYGON_POINTS - MIN_POLYGON_POINTS + 1);
        float startAngle = random.nextFloat() * Mth.TWO_PI;
        float step = Mth.TWO_PI / count;
        List<LocalPoint> raw = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            float angle = startAngle + step * i + (random.nextFloat() - 0.5f) * step * 0.48f;
            float radius = 0.68f + random.nextFloat() * 0.46f;
            if ((i & 1) == 0) radius += random.nextFloat() * 0.16f;
            float x = Mth.cos(angle) * radius * (0.84f + random.nextFloat() * 0.26f);
            float y = Mth.sin(angle) * radius * (0.80f + random.nextFloat() * 0.32f);
            raw.add(new LocalPoint(x, y, 0.0f, 0.0f));
        }

        LocalPoint center = localCentroid(raw);
        List<LocalPoint> polygon = new ArrayList<>(count);
        for (LocalPoint point : raw) {
            float x = point.x - center.x;
            float y = point.y - center.y;
            float u = Mth.clamp(0.5f + x * 0.42f, 0.02f, 0.98f);
            float v = Mth.clamp(0.5f - y * 0.42f, 0.02f, 0.98f);
            polygon.add(new LocalPoint(x, y, u, v));
        }
        return List.copyOf(polygon);
    }

    private static LocalPoint localCentroid(List<LocalPoint> polygon) {
        float area2 = 0.0f;
        float cx = 0.0f;
        float cy = 0.0f;

        for (int i = 0; i < polygon.size(); i++) {
            LocalPoint a = polygon.get(i);
            LocalPoint b = polygon.get((i + 1) % polygon.size());
            float cross = a.x * b.y - b.x * a.y;
            area2 += cross;
            cx += (a.x + b.x) * cross;
            cy += (a.y + b.y) * cross;
        }

        if (Math.abs(area2) < 1.0e-6f) {
            for (LocalPoint point : polygon) {
                cx += point.x;
                cy += point.y;
            }
            float inv = 1.0f / polygon.size();
            return new LocalPoint(cx * inv, cy * inv, 0.0f, 0.0f);
        }

        float inv = 1.0f / (3.0f * area2);
        return new LocalPoint(cx * inv, cy * inv, 0.0f, 0.0f);
    }

    private static Vec3 rotateAround(Vec3 value, Vec3 axis, float angle) {
        float s = Mth.sin(angle);
        float c = Mth.cos(angle);
        Vec3 parallel = axis.scale(value.dot(axis));
        Vec3 perpendicular = value.subtract(parallel);
        Vec3 cross = axis.cross(perpendicular);
        return parallel.add(perpendicular.scale(c)).add(cross.scale(s));
    }

    private static float smooth(float value) {
        value = Mth.clamp(value, 0.0f, 1.0f);
        return value * value * (3.0f - 2.0f * value);
    }

    private static float alphaFade(float life) {
        life = Mth.clamp(life, 0.0f, 1.0f);
        float fadeStart = Mth.clamp(DimensionalSlashTuning.WorldSlash.WORLD_SHARD_ALPHA_FADE_START, 0.0f, 0.98f);
        if (life <= fadeStart) return 1.0f;

        float fade = (life - fadeStart) / (1.0f - fadeStart);
        return 1.0f - smooth(fade);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        if (value.lengthSqr() < 1.0e-8) return fallback;
        return value.normalize();
    }

    private record LocalPoint(float x, float y, float u, float v) {}
}
