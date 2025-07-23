package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.entity.effect.AnemiaSpecialEffect;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.awt.*;

public class AnemiaSpecialEffectRender extends EntityRenderer<AnemiaSpecialEffect> {

    public AnemiaSpecialEffectRender(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(@NotNull AnemiaSpecialEffect entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        poseStack.pushPose();
        renderRainbowCube(poseStack, bufferSource, entity, partialTicks, packedLight);
        poseStack.translate(0.0F, 0.0F, 0.0F);
        poseStack.popPose();
    }

    private void renderRainbowCube(PoseStack poseStack, MultiBufferSource buffers, AnemiaSpecialEffect entity, float partialTicks, int packedLight) {
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.LIGHTNING);
        Matrix4f mat = poseStack.last().pose();
        AABB boundingBox = entity.getBoundingBox();

        float minX = (float) (boundingBox.minX - entity.getX());
        float minY = (float) (boundingBox.minY - entity.getY());
        float minZ = (float) (boundingBox.minZ - entity.getZ());
        float maxX = (float) (boundingBox.maxX - entity.getX());
        float maxY = (float) (boundingBox.maxY - entity.getY());
        float maxZ = (float) (boundingBox.maxZ - entity.getZ());

        float[][] vertices = {
                {minX, minY, minZ}, {maxX, minY, minZ}, {maxX, minY, maxZ}, {minX, minY, maxZ},
                {minX, maxY, minZ}, {maxX, maxY, minZ}, {maxX, maxY, maxZ}, {minX, maxY, maxZ}
        };

        int[][] faces = {{0, 1, 2, 3}, {7, 6, 5, 4}, {0, 4, 5, 1}, {1, 5, 6, 2}, {2, 6, 7, 3}, {3, 7, 4, 0}};

        float time = (entity.tickCount + partialTicks) * 0.005f;

        for (int[] face : faces) {
            float[] v1 = vertices[face[0]];
            float[] v2 = vertices[face[1]];
            float[] v3 = vertices[face[2]];
            float[] v4 = vertices[face[3]];

            int color = getRainbowColor(time);
            emitQuad(consumer, mat, v1, v2, v3, v4, color, color, color, color, packedLight);
        }
    }

    private static int getRainbowColor(float time) {
        float hue = (time % 1.0f);
        int color = Color.HSBtoRGB(hue, 0.32f, 0.64f);
        return (color & 0x00FFFFFF) | (200 << 24);
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

    @Override
    @SuppressWarnings("all")
    public @NotNull ResourceLocation getTextureLocation(@NotNull AnemiaSpecialEffect entity) {
        return null;
    }

    @Override
    public boolean shouldRender(@NotNull AnemiaSpecialEffect entity, @NotNull Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }
}
