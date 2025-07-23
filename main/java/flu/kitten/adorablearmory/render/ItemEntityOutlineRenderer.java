package flu.kitten.adorablearmory.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.item.SparklingDreamIdolStar;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.awt.*;

@SuppressWarnings("unused")
public class ItemEntityOutlineRenderer {

    @SubscribeEvent
    public void worldRenderLevelStage(RenderLevelStageEvent render) {

        if (render.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.getCameraEntity() == null) return;

        PoseStack poseStack = render.getPoseStack();
        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();
        Camera camera = mc.getEntityRenderDispatcher().camera;
        Vec3 cameraPos = camera.getPosition();
        Entity cameraEntity = mc.getCameraEntity();

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof ItemEntity itemEntity && itemEntity.getItem().getItem() instanceof SparklingDreamIdolStar) {
                double distanceToPlayer = entity.distanceToSqr(cameraEntity.getX(), cameraEntity.getY(), cameraEntity.getZ());
                if (distanceToPlayer <= 8.0) inRenderOutline(poseStack, bufferSource, itemEntity, cameraPos);
            }
        }
        bufferSource.endBatch();
    }

    private static void inRenderOutline(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, ItemEntity itemEntity, Vec3 cameraPos) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.lineWidth(8.0F);

        long currentTime = System.currentTimeMillis();
        float hue = (currentTime % 10000L) / 10000.0f;
        int rgb = Color.HSBtoRGB(hue, 1.0f, 1.0f);

        float red = (rgb >> 16 & 0xFF) / 255.0f;
        float green = (rgb >> 8 & 0xFF) / 255.0f;
        float blue = (rgb & 0xFF) / 255.0f;

        float alpha = (float) (0.5 + 0.5 * Math.sin(currentTime / 500.0));

        RenderSystem.setShaderColor(red, green, blue, alpha);

        poseStack.pushPose();
        poseStack.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);

        LevelRenderer.renderLineBox(
                poseStack,
                bufferSource.getBuffer(RenderType.LINES),
                itemEntity.getBoundingBox(),
                red, green, blue, alpha
        );
        poseStack.popPose();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.lineWidth(4.0F);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
    }
}
