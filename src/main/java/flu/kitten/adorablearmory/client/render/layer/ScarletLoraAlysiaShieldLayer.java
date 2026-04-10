package flu.kitten.adorablearmory.client.render.layer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import flu.kitten.adorablearmory.client.model.ScarletLoraAlysiaModel;
import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

@OnlyIn(Dist.CLIENT)
public class ScarletLoraAlysiaShieldLayer extends EnergySwirlLayer<ScarletLoraAlysia, ScarletLoraAlysiaModel<ScarletLoraAlysia>> {

    private static final ResourceLocation SHIELD_TEXTURE = new ResourceLocation(MODID, "textures/entity/scarlet_lora_alysia_armor.png");
    private final ScarletLoraAlysiaModel<ScarletLoraAlysia> shieldModel;

    public ScarletLoraAlysiaShieldLayer(RenderLayerParent<ScarletLoraAlysia, ScarletLoraAlysiaModel<ScarletLoraAlysia>> parent, EntityModelSet modelSet) {
        super(parent);
        this.shieldModel = new ScarletLoraAlysiaModel<>(modelSet.bakeLayer(ScarletLoraAlysiaModel.LAYER_LOCATION));
    }

    @Override
    protected float xOffset(float value) {
        return 0;
    }

    @Override
    protected @NotNull ResourceLocation getTextureLocation() {
        return SHIELD_TEXTURE;
    }

    @Override
    protected @NotNull EntityModel<ScarletLoraAlysia> model() {
        return this.shieldModel;
    }

    @Override
    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, @NotNull ScarletLoraAlysia entity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        int timer = entity.getPhaseTimer();

        float alpha;
        if (timer <= 0) {
            alpha = 0f;
        } else if (timer <= 100) {
            alpha = timer / 100.0f;
        } else if (timer <= 255) {
            alpha = (255 - timer) / 155.0f;
        } else {
            alpha = 0f;
        }
        alpha = Mth.clamp(alpha, 0f, 1f);
        if (alpha <= 0f) return;

        Minecraft mc = Minecraft.getInstance();
        float time = (mc.level == null ? 0f : (mc.level.getGameTime() + partialTicks));
        float hue = (time * 0.02f + entity.getId() * 0.07f) % 1.0f;
        float[] rgb = hsvToRgb(hue, 0.5f, 0.9f);

        try {
            this.getParentModel().copyPropertiesTo(this.shieldModel);
        } catch (Throwable ignored) {}
        this.shieldModel.prepareMobModel(entity, limbSwing, limbSwingAmount, partialTicks);
        this.shieldModel.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        float speed = 0.0080f; // 滚动速度
        float raw = -time * speed;
        float u = Mth.frac(raw);
        float v = Mth.frac(raw);

        final float eps = 1e-3f;
        u = u * (1f - 2f * eps) + eps;
        v = v * (1f - 2f * eps) + eps;

        RenderType type = ShieldRenderType.entityShield(this.getTextureLocation(), u, v);
        VertexConsumer consumer = bufferSource.getBuffer(type);

        this.shieldModel.renderToBuffer(poseStack, consumer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, rgb[0], rgb[1], rgb[2], alpha);
    }

    private static float[] hsvToRgb(float h, float s, float v) {
        h = (h % 1.0f + 1.0f) % 1.0f;
        float hh = h * 6.0f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1 - s);
        float q = v * (1 - s * f);
        float t = v * (1 - s * (1 - f));
        float r, g, b;
        switch (i) {
            case 0 -> {
                r = v;
                g = t;
                b = p;
            }
            case 1 -> {
                r = q;
                g = v;
                b = p;
            }
            case 2 -> {
                r = p;
                g = v;
                b = t;
            }
            case 3 -> {
                r = p;
                g = q;
                b = v;
            }
            case 4 -> {
                r = t;
                g = p;
                b = v;
            }
            default -> {
                r = v;
                g = p;
                b = q;
            }
        }
        return new float[]{r, g, b};
    }

    private static class ShieldRenderType extends RenderStateShard {

        public ShieldRenderType(String string, Runnable r1, Runnable r2) {
            super(string, r1, r2);
        }

        public static RenderType entityShield(ResourceLocation location, float u, float v) {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENERGY_SWIRL_SHADER)
                    .setTextureState(new TextureStateShard(location, false, false))
                    .setTexturingState(new OffsetTexturingStateShard(u, v))
                    .setTransparencyState(LIGHTNING_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .createCompositeState(false);
            return RenderType.create("scarlet_shield", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, state);
        }
    }
}
