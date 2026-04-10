package flu.kitten.adorablearmory.entity.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AfterimageRender {

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void afterimageRender(RenderLevelStageEvent event) {

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ScarletLoraAlysia boss && !boss.ghostTrails.isEmpty()) {
                renderGhostTrails(boss, event.getPoseStack(), event.getPartialTick());
            }
        }
    }

    private static void renderGhostTrails(ScarletLoraAlysia boss, PoseStack poseStack, float partialTick) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        EntityRenderer<? super ScarletLoraAlysia> renderer = dispatcher.getRenderer(boss);
        if (!(renderer instanceof LivingEntityRenderer<?, ?> livingRenderer)) return;

        var model = livingRenderer.getModel();
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cameraPos = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();

        long currentTime = boss.level().getGameTime();

        //float hueScroll = (currentTime + partialTick) * 0.02f;

        for (ScarletLoraAlysia.GhostTrail trail : boss.ghostTrails) {
            long age = currentTime - trail.creationTick();

            float life = (age + partialTick) / 15.0f;
            float alpha = 1.0f - Mth.clamp(life, 0.0f, 1.0f);
            if (alpha <= 0.01f) continue;

            poseStack.pushPose();

            Vec3 trailPos = trail.pos();
            poseStack.translate(trailPos.x - cameraPos.x, trailPos.y - cameraPos.y, trailPos.z - cameraPos.z);

            poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - trail.yRot()));
            poseStack.scale(-1.0f, -1.0f, 1.0f);
            poseStack.translate(0.0f, -1.501f, 0.0f);

            RenderType renderType = RenderType.entityTranslucent(renderer.getTextureLocation(boss));
            VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

            int overlay = LivingEntityRenderer.getOverlayCoords(boss, 0.0f);

            model.renderToBuffer(poseStack, vertexConsumer, 15728880, overlay, 1, 1, 1, alpha);

            poseStack.popPose();
        }

        bufferSource.endBatch();
    }
}
