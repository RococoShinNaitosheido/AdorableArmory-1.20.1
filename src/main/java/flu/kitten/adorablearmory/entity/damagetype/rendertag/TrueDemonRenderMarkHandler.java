package flu.kitten.adorablearmory.entity.damagetype.rendertag;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.entity.damagetype.Capabilities;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.function.Function;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT)
public class TrueDemonRenderMarkHandler {

    private static final ResourceLocation MARK_TEXTURE = new ResourceLocation(AdorableArmory.MODID, "textures/entity/true_demon_mark.png");

    @SubscribeEvent
    public static void renderLevelStageTag(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)) continue;

            living.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
                if (cap.hasEffect() && cap.getRemainingDuration() > 0) {
                    poseStack.pushPose();

                    double x = Mth.lerp(event.getPartialTick(), living.xOld, living.getX()) - event.getCamera().getPosition().x;
                    double y = Mth.lerp(event.getPartialTick(), living.yOld, living.getY()) - event.getCamera().getPosition().y;
                    double z = Mth.lerp(event.getPartialTick(), living.zOld, living.getZ()) - event.getCamera().getPosition().z;
                    poseStack.translate(x, y, z);

                    TrueDemonRenderMarkHandler.renderMark(living, poseStack, buffer, event.getPartialTick());

                    poseStack.popPose();
                }
            });
        }

        buffer.endBatch();
    }

    private static void renderMark(LivingEntity entity, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        poseStack.pushPose();

        double height = entity.getBbHeight();
        poseStack.translate(0, height / 2, 0);

        // Billboard
        Quaternionf rotation = Minecraft.getInstance().gameRenderer.getMainCamera().rotation();
        poseStack.mulPose(rotation);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double distSq = entity.distanceToSqr(cameraPos);
        float distance = (float) Math.sqrt(distSq);

        float baseScale = 1.0f; // 基础缩放倍率
        float startScaleDist = 15.0f; // 多少格以内保持原样

        float distScale = Math.max(1.0f, distance / startScaleDist);

        float animScale = 1.0f + Mth.sin((entity.tickCount + partialTick) * 0.08f) * 0.05f;

        float finalScale = baseScale * animScale * distScale;
        poseStack.scale(finalScale, finalScale, finalScale);

        float time = (entity.tickCount + partialTick) * 0.02f;
        int color = calculateGradientColor(time);

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float alpha = 1f;

        VertexConsumer vertexConsumer = bufferSource.getBuffer(trueDemonMarkType.TRUE_DEMON_MARK_TYPE.apply(MARK_TEXTURE));
        Matrix4f matrix4f = poseStack.last().pose();

        // 图大小
        float size = 2f;
        float half = size / 2.0f;

        addVertex(vertexConsumer, matrix4f, -half, -half, r, g, b, alpha, 0, 1);
        addVertex(vertexConsumer, matrix4f, half, -half, r, g, b, alpha, 1, 1);
        addVertex(vertexConsumer, matrix4f, half, half, r, g, b, alpha, 1, 0);
        addVertex(vertexConsumer, matrix4f, -half, half, r, g, b, alpha, 0, 0);

        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer builder, Matrix4f matrix, float x, float y, float r, float g, float b, float a, float u, float v) {
        builder.vertex(matrix, x, y, 0.0f).color(r, g, b, a).uv(u, v)/*.uv2(0xF000F0)*/.endVertex();
    }

    private static int calculateGradientColor(float time) {
        float t = (Mth.sin(time) + 1.0f) / 2.0f;

        int color = 0xff00ff;
        int color2 = 0xff0f77;
        int color3 = 0xcf086b;

        if (t < 0.5f) {
            return mixColors(color, color3, t * 2.0f);
        } else {
            return mixColors(color3, color2, (t - 0.5f) * 2.0f);
        }
    }

    private static int mixColors(int c1, int c2, float ratio) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = c1 & 0xFF;

        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = c2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * ratio);
        int g = (int) (g1 + (g2 - g1) * ratio);
        int b = (int) (b1 + (b2 - b1) * ratio);

        return (r << 16) | (g << 8) | b;
    }

    private static class trueDemonMarkType extends RenderStateShard {
        private static final Function<ResourceLocation, RenderType> TRUE_DEMON_MARK_TYPE = Util.memoize(texture ->
                net.minecraft.client.renderer.RenderType.create("true_demon_mark",
                        DefaultVertexFormat.POSITION_COLOR_TEX,
                        VertexFormat.Mode.QUADS,
                        256,
                        false,
                        true,
                        RenderType.CompositeState.builder()
                                .setShaderState(RenderStateShard.POSITION_COLOR_TEX_SHADER)
                                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                                .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                                .setCullState(RenderStateShard.NO_CULL)
                                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                                .setOverlayState(RenderStateShard.NO_OVERLAY)
                                .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                                .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                                .createCompositeState(false)
                )
        );

        public trueDemonMarkType(String string, Runnable runnable, Runnable runnable1) {
            super(string, runnable, runnable1);
        }
    }
}
