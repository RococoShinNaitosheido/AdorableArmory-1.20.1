package flu.kitten.adorablearmory.client.render.dimensional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class DimensionalSlashFragment {
    private final Vec3 origin;
    private final Vec3 velocity;
    private final Vec3 axisA;
    private final Vec3 axisB;
    private final float size;
    private final int color;
    private final int lifetime;
    private int age;

    public DimensionalSlashFragment(Vec3 origin, Vec3 velocity, Vec3 axisA, Vec3 axisB, float size, int color, int lifetime) {
        this.origin = origin;
        this.velocity = velocity;
        this.axisA = safeNormalize(axisA, new Vec3(1.0, 0.0, 0.0));
        this.axisB = safeNormalize(axisB, new Vec3(0.0, 1.0, 0.0));
        this.size = Math.max(0.02f, size);
        this.color = color;
        this.lifetime = Math.max(1, lifetime);
    }

    public void tick() {
        age++;
    }

    public boolean isAlive() {
        return age < lifetime;
    }

    public void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick) {
        float life = (age + partialTick) / lifetime;
        if (life < 0.0f || life >= 1.0f) return;

        float alpha = (1.0f - life) * (1.0f - life) * 0.92f;
        float spin = (age + partialTick) * 0.22f;
        float scale = size * (0.65f + life * 0.75f);
        Vec3 center = origin.add(velocity.scale(age + partialTick)).subtract(camera);

        Vec3 a = rotateInPlane(axisA, axisB, spin).scale(scale);
        Vec3 b = rotateInPlane(axisB, axisA, -spin * 0.7f).scale(scale * 0.62f);

        Vec3 p0 = center.add(a);
        Vec3 p1 = center.subtract(a.scale(0.35)).add(b);
        Vec3 p2 = center.subtract(a.scale(0.55)).subtract(b.scale(0.75));

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float bl = (color & 0xFF) / 255.0f;

        Matrix4f matrix = poseStack.last().pose();
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.TRAIL_TUBE_ADDITIVE);
        consumer.vertex(matrix, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, bl, alpha).endVertex();
        consumer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, bl, alpha * 0.8f).endVertex();
        consumer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, bl, 0.0f).endVertex();
        consumer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, bl, 0.0f).endVertex();
    }

    private static Vec3 rotateInPlane(Vec3 a, Vec3 b, float radians) {
        float s = Mth.sin(radians);
        float c = Mth.cos(radians);
        return a.scale(c).add(b.scale(s));
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        if (value.lengthSqr() < 1.0e-8) return fallback;
        return value.normalize();
    }
}
