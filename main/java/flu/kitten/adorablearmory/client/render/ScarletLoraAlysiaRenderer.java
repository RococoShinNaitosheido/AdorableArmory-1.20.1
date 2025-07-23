package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.client.model.ScarletLoraAlysiaModel;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

public class ScarletLoraAlysiaRenderer extends MobRenderer<ScarletLoraAlysia, ScarletLoraAlysiaModel<ScarletLoraAlysia>> {

    private static final float MODEL_VISUAL_MULTI = 1; // entitySize
    private static final float BASE_STAR_DENSITY = 0.70F; // 星点密度
    private static final float MIN_STAR_SCALE = 0.0001F;
    private static final int SKY_LAYERS = 10;

    public ScarletLoraAlysiaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ScarletLoraAlysiaModel<>(ctx.bakeLayer(ScarletLoraAlysiaModel.LAYER_LOCATION)), 0.50F);
    }

    private static float computeVisualScale(ScarletLoraAlysia entity) {
        float entityScale = 1.0F;
        try {
            entityScale = entity.getScale();
        } catch (Throwable ignored) {}
        return entityScale * MODEL_VISUAL_MULTI;
    }

    @Override
    public void render(@NotNull ScarletLoraAlysia entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffers, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float time = (mc.level.getGameTime() + partialTicks) * 0.020F % 1000.0F;
        float visualScale = computeVisualScale(entity);
        float starScale = BASE_STAR_DENSITY / Math.max(visualScale, MIN_STAR_SCALE);

        AdorableArmoryShaders.portalTime.set(time);
        AdorableArmoryShaders.portalLayers.set(SKY_LAYERS);
        AdorableArmoryShaders.starScale.set(starScale);
        AdorableArmoryShaders.opacity.set(0.50F);
        AdorableArmoryShaders.rainbowMix.set(0.4F);

        VertexConsumer starConsumer = buffers.getBuffer(AdorableArmoryShaders.SKY_ENTITY);

        poseStack.pushPose();
        try {
            float bodyYawDeg = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
            this.setupRotations(entity, poseStack, this.getBob(entity, partialTicks), bodyYawDeg, partialTicks);

            poseStack.scale(-1.0F, -1.0F, 1.0F);
            poseStack.translate(0.0D, -1.501D, 0.0D);
            this.scale(entity, poseStack, partialTicks);

            poseStack.scale(1.001100F, 1.001100F, 1.001100F);

            float limbSwing = entity.walkAnimation.position(partialTicks);
            float limbSwingAmount = entity.walkAnimation.speed(partialTicks);
            float ageInTicks = entity.tickCount + partialTicks;
            float netHeadYaw = Mth.wrapDegrees(entity.getViewYRot(partialTicks) - bodyYawDeg);
            float headPitch = Mth.lerp(partialTicks, entity.xRotO, entity.getXRot());

            ScarletLoraAlysiaModel<ScarletLoraAlysia> model = this.getModel();
            model.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
            model.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
            model.renderToBuffer(poseStack, starConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    protected void scale(@NotNull ScarletLoraAlysia entity, @NotNull PoseStack poseStack, float partialTickTime) {
        float scale = computeVisualScale(entity);
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ScarletLoraAlysia loraAlysia) {
        return new ResourceLocation(MODID, "textures/entity/scarlet_lora_alysia.png");
    }
}
