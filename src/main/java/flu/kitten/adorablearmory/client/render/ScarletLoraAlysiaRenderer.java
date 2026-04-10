package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.client.model.ScarletLoraAlysiaModel;
import flu.kitten.adorablearmory.client.render.layer.ScarletLoraAlysiaShieldLayer;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

@OnlyIn(Dist.CLIENT)
public class ScarletLoraAlysiaRenderer extends MobRenderer<ScarletLoraAlysia, ScarletLoraAlysiaModel<ScarletLoraAlysia>> {

    private static final ResourceLocation NORMAL_TEXTURE = new ResourceLocation(MODID, "textures/entity/scarlet_lora_alysia.png");
    private static final ResourceLocation INVULNERABLE_TEXTURE = new ResourceLocation(MODID, "textures/entity/scarlet_lora_alysia_invulnerable.png");
    private static final float MODEL_VISUAL_MULTI = 1;
    private static final float BASE_STAR_DENSITY = 0.70f;
    private static final float MIN_STAR_SCALE = 0.001f;
    private static final int SKY_LAYERS = 10;

    public ScarletLoraAlysiaRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ScarletLoraAlysiaModel<>(ctx.bakeLayer(ScarletLoraAlysiaModel.LAYER_LOCATION)), 0.50F);
        this.addLayer(new ScarletLoraAlysiaShieldLayer(this, ctx.getModelSet()));
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer())); // Hand bow
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
        float timer = entity.getPhaseTimer();
        int defaultLight = packedLight;
        if (entity.isInvulnerable()) {
            final int FULL_BRIGHT = 15728880; // LightTexture.FULL_BRIGHT

            // 改大=更慢 改小=更快/tick
            final int FADE_IN_TICKS = 10; // 淡入时间
            final int HOLD_TICKS = 5; // 保持满亮
            final int FADE_OUT_TICKS = 10; // 淡出时间

            float t = computeTriPhase01(timer, FADE_IN_TICKS, HOLD_TICKS, FADE_OUT_TICKS);
            packedLight = leapPackedLight(defaultLight, FULL_BRIGHT, t);
        }

        super.render(entity, entityYaw, partialTicks, poseStack, buffers, packedLight);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float time = (mc.level.getGameTime() + partialTicks) * 0.015F % 1000.0F;
        float visualScale = computeVisualScale(entity);
        float starScale = BASE_STAR_DENSITY / Math.max(visualScale, MIN_STAR_SCALE);

        AdorableArmoryShaders.portalTime.set(time);
        AdorableArmoryShaders.portalLayers.set(SKY_LAYERS);
        AdorableArmoryShaders.starScale.set(starScale);
        AdorableArmoryShaders.opacity.set(0.32f);
        AdorableArmoryShaders.rainbowMix.set(0.5f);
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.SKY_ENTITY);

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
            float red = 1, green = 1, blue = 1, alpha = 1;
            if (entity.isInvulnerable()) {
                red = 0.9F;
                green = 0.95F;
                int invulnerabilityTimer = entity.getPhaseTimer();
                if (invulnerabilityTimer > 0) {
                    alpha = (Mth.sin(ageInTicks * 0.2F) + 1.0F) * 0.1F + 0.8F;
                }
            }
            model.renderToBuffer(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY, red, green, blue, alpha);
        } finally {
            poseStack.popPose();
        }
    }

    @Override
    protected void scale(@NotNull ScarletLoraAlysia entity, @NotNull PoseStack poseStack, float partialTickTime) {
        float scale = computeVisualScale(entity);
        if (entity.isInvulnerable()) {
            float time = entity.tickCount + partialTickTime;
            float pulse = 1.0F + Mth.sin(time * 0.1F) * 0.001F;
            scale *= pulse;
        }
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull ScarletLoraAlysia entity) {
        int timer = entity.getPhaseTimer();
        return timer > 0 && (timer > 80 || timer / 5 % 2 != 1) ? INVULNERABLE_TEXTURE : NORMAL_TEXTURE;
    }

    private static float computeTriPhase01(float time, int inTicks, int holdTicks, int outTicks) {
        if (time <= 0) return 0f;
        if (time < inTicks) return time / Math.max(1f, inTicks);
        time -= inTicks;
        if (time < holdTicks) return 1f;
        time -= holdTicks;
        if (time < outTicks) return 1f - (time / Math.max(1f, outTicks));
        return 0f;
    }

    private static int leapPackedLight(int fromPacked, int toPacked, float t) {
        int fromBlock =  fromPacked & 0xFFFF;
        int fromSky = (fromPacked >>> 16) & 0xFFFF;
        int toBlock =  toPacked & 0xFFFF;
        int toSky = (toPacked >>> 16) & 0xFFFF;
        int outBlock = Mth.clamp(Math.round(Mth.lerp(t, fromBlock, toBlock)), 0, 0xFFFF);
        int outSky = Mth.clamp(Math.round(Mth.lerp(t, fromSky,   toSky)),   0, 0xFFFF);
        return (outSky << 16) | outBlock;
    }
}
