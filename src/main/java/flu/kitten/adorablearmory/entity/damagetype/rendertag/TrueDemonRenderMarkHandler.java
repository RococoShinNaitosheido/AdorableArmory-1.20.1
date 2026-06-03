package flu.kitten.adorablearmory.entity.damagetype.rendertag;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.render.dimensional.DimensionalSlashEffect;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.entity.damagetype.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT)
public class TrueDemonRenderMarkHandler {
    private static final int MARK_BASE_DURATION_TICKS = 200;

    @SubscribeEvent
    public static void renderLevelStageTag(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        float partialTick = event.getPartialTick();

        poseStack.pushPose();
        RenderSystem.disableDepthTest();
        try {
            for (var entity : mc.level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living)) continue;

                living.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
                    int remainingTicks = cap.getRemainingDuration();
                    if (!cap.hasEffect() || remainingTicks <= 0) return;

                    Vec3 center = interpolatedMarkCenter(living, partialTick);
                    float height = Math.max(0.6f, living.getBbHeight());
                    float length = Mth.clamp(height * 1.72f, 1.65f, 3.65f);
                    float width = Mth.clamp(height * 0.13f, 0.14f, 0.32f);
                    float fade = Mth.clamp(remainingTicks / 22.0f, 0.0f, 1.0f);
                    float markAge = Math.max(0.0f, MARK_BASE_DURATION_TICKS - remainingTicks) + partialTick;
                    long seed = living.getUUID().getMostSignificantBits() ^ living.getUUID().getLeastSignificantBits();

                    DimensionalSlashEffect.renderXMark(poseStack, buffer, mc.level, center, camera, markAge, length, width, fade, seed);
                });
            }
            buffer.endBatch(AdorableArmoryShaders.DIMENSIONAL_SLASH_ENTITY_PIERCE);
        } finally {
            RenderSystem.enableDepthTest();
            poseStack.popPose();
        }
    }

    private static Vec3 interpolatedMarkCenter(LivingEntity entity, float partialTick) {
        double x = Mth.lerp(partialTick, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTick, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.58;
        double z = Mth.lerp(partialTick, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }
}
