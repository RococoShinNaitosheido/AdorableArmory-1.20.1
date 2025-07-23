package flu.kitten.adorablearmory.client.shader;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import flu.kitten.adorablearmory.api.client.shader.CCShaderInstance;
import flu.kitten.adorablearmory.api.client.shader.CCUniform;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;
import java.util.function.Function;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

@SuppressWarnings("unused")
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AdorableArmoryShaders {
    private static class RenderStateShardAccess extends RenderStateShard {
        private static final ResourceLocation END_SKY_LOCATION = new ResourceLocation(MODID,"textures/entity/in_sky.png");
        private static final ResourceLocation END_PORTAL_LOCATION = new ResourceLocation(MODID,"textures/entity/starry_sky.png");
        private static final RenderStateShard.DepthTestStateShard NO_DEPTH_TEST = RenderStateShard.NO_DEPTH_TEST;
        private static final RenderStateShard.DepthTestStateShard EQUAL_DEPTH_TEST = RenderStateShard.EQUAL_DEPTH_TEST; // ITEM
        private static final RenderStateShard.DepthTestStateShard LEQUAL_DEPTH_TEST = RenderStateShard.LEQUAL_DEPTH_TEST; // BLOCK
        private static final RenderStateShard.LightmapStateShard LIGHT_MAP = RenderStateShard.LIGHTMAP;
        private static final RenderStateShard.TransparencyStateShard TRANSLUCENT_TRANSPARENCY = RenderStateShard.TRANSLUCENT_TRANSPARENCY;
        private static final RenderStateShard.TextureStateShard BLOCK_SHEET_MIPPED = RenderStateShard.BLOCK_SHEET_MIPPED;
        private static final RenderStateShard.TextureStateShard BLOCK_SHEET = RenderStateShard.BLOCK_SHEET;
        private static final RenderStateShard.CullStateShard NO_CULL = RenderStateShard.NO_CULL;
        private static final RenderStateShard.CullStateShard CULL = RenderStateShard.CULL;
        private static final RenderStateShard.ShaderStateShard EYES_LIGHT = new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEyesShader);
        private static final RenderStateShard.ShaderStateShard RENDERTYPE_LIGHTNING_SHADER = new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeLightningShader);
        private static final RenderStateShard.WriteMaskStateShard COLOR_WRITE = RenderStateShard.COLOR_WRITE;
        private static final RenderStateShard.WriteMaskStateShard COLOR_DEPTH_WRITE = RenderStateShard.COLOR_DEPTH_WRITE;
        private static final RenderStateShard.LayeringStateShard VIEW_OFFSET_Z_LAYERING = RenderStateShard.VIEW_OFFSET_Z_LAYERING;
        private static final RenderStateShard.TransparencyStateShard ADDITIVE_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("additive_transparency", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });

        private static final RenderStateShard.TransparencyStateShard GUI_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("gui_transparency", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });

        private static final RenderStateShard.TransparencyStateShard LIGHTNING_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("lightning_transparency", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });

        private static final RenderStateShard.OutputStateShard WEATHER_TARGET = new RenderStateShard.OutputStateShard("weather_target", () -> {
            if (Minecraft.useShaderTransparency()) {
                Objects.requireNonNull(Minecraft.getInstance().levelRenderer.getWeatherTarget()).bindWrite(false);
            }

        }, () -> {
            if (Minecraft.useShaderTransparency()) {
                Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
            }

        });

        private RenderStateShardAccess(String pName, Runnable pSetupState, Runnable pClearState) {
            super(pName, pSetupState, pClearState);
        }
    }

    public static final float[] COSMIC_UVS = new float[40];
    public static boolean inventoryRender = false;
    public static int renderTime;
    public static float tick;
    public static float renderFrame;
    public static CCShaderInstance cosmicShader; // cosmic item render
    public static CCShaderInstance starrySkyShader; // entity render
    public static CCShaderInstance starrySkyShaderItem; // item render
    public static CCShaderInstance blackHoleShader;
    public static CCUniform cosmicTime;
    public static CCUniform portalTime;
    public static CCUniform portalTimeItem;
    public static CCUniform cosmicYaw;
    public static CCUniform cosmicPitch;
    public static CCUniform camYaw;
    public static CCUniform camPitch;
    public static CCUniform cosmicExternalScale;
    public static CCUniform cosmicOpacity;
    public static CCUniform cosmicUVs;
    public static CCUniform portalLayers;
    public static CCUniform portalLayersItem;
    public static CCUniform starScale;
    public static CCUniform starScaleItem;
    public static CCUniform rainbowSpeed;
    public static CCUniform rainbowSpeedItem;
    public static CCUniform rainbowScale;
    public static CCUniform rainbowScaleItem;
    public static CCUniform rainbowMix;
    public static CCUniform rainbowMixItem;
    public static CCUniform opacity;
    public static CCUniform ItemOpacity;
    public static final RenderType COSMIC_RENDER_TYPE = RenderType.create(MODID + ":cosmic", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 2097152, true, false, RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(() -> cosmicShader))
            .setDepthTestState(RenderStateShardAccess.EQUAL_DEPTH_TEST)
            .setLightmapState(RenderStateShardAccess.LIGHT_MAP)
            .setTransparencyState(RenderStateShardAccess.TRANSLUCENT_TRANSPARENCY)
            .setTextureState(RenderStateShardAccess.BLOCK_SHEET_MIPPED)
            .createCompositeState(true));

    public static final RenderType COSMIC_BLOCK_RENDER_TYPE = RenderType.create(MODID + ":cosmic_block", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 2097152, true, false, RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(() -> cosmicShader))
            .setDepthTestState(RenderStateShardAccess.LEQUAL_DEPTH_TEST)
            .setLightmapState(RenderStateShardAccess.LIGHT_MAP)
            .setTransparencyState(RenderStateShardAccess.TRANSLUCENT_TRANSPARENCY)
            .setTextureState(RenderStateShardAccess.BLOCK_SHEET_MIPPED)
            .createCompositeState(true));

    public static final RenderType COSMIC_ENTITY_RENDER_TYPE = RenderType.create(MODID + ":cosmic_entity", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 2097152, false, true, RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(() -> cosmicShader))
            .setDepthTestState(RenderStateShardAccess.LEQUAL_DEPTH_TEST)
            .setLightmapState(RenderStateShardAccess.LIGHT_MAP)
            .setTransparencyState(RenderStateShardAccess.TRANSLUCENT_TRANSPARENCY)
            .setTextureState(RenderStateShardAccess.BLOCK_SHEET_MIPPED)
            .setWriteMaskState(RenderStateShardAccess.COLOR_DEPTH_WRITE)
            .createCompositeState(false));

    public static final RenderType SKY_ENTITY = RenderType.create("sky_entity", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> starrySkyShader))
                    .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                            .add(RenderStateShardAccess.END_SKY_LOCATION,  false, false)
                            .add(RenderStateShardAccess.END_PORTAL_LOCATION, false, false)
                            .build())
                    .setTransparencyState(RenderStateShardAccess.ADDITIVE_TRANSPARENCY)
                    .setCullState(RenderStateShardAccess.NO_CULL)
                    .createCompositeState(false));

    public static final RenderType SKY_ITEM = RenderType.create("sky_item", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 2097152, true, false, RenderType.CompositeState.builder()
            .setShaderState(new RenderStateShard.ShaderStateShard(() -> starrySkyShaderItem))
            .setDepthTestState(RenderStateShardAccess.EQUAL_DEPTH_TEST)
            .setLightmapState(RenderStateShardAccess.LIGHT_MAP)
            .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                    .add(RenderStateShardAccess.END_SKY_LOCATION,  false, false)
                    .add(RenderStateShardAccess.END_PORTAL_LOCATION, false, false)
                    .build())
            .setTransparencyState(RenderStateShardAccess.ADDITIVE_TRANSPARENCY)
            .setWriteMaskState(RenderStateShardAccess.COLOR_DEPTH_WRITE)
            .createCompositeState(true));

    public static final RenderType SKY_ITEM_GUI = RenderType.create("sky_item_gui", DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 2097152, true, false, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> starrySkyShaderItem))
                    .setDepthTestState(RenderStateShardAccess.NO_DEPTH_TEST)
                    .setLightmapState(RenderStateShardAccess.LIGHT_MAP)
                    .setTextureState(RenderStateShard.MultiTextureStateShard.builder()
                            .add(RenderStateShardAccess.END_SKY_LOCATION, false, false)
                            .add(RenderStateShardAccess.END_PORTAL_LOCATION, false, false)
                            .build())
                    .setTransparencyState(RenderStateShardAccess.ADDITIVE_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShardAccess.COLOR_WRITE)
                    .setCullState(RenderStateShardAccess.NO_CULL)
                    .createCompositeState(true)
    );

    public static final Function<ResourceLocation, RenderType> EYES = Util.memoize((function) -> {
        RenderStateShard.TextureStateShard textureStateShard = new RenderStateShard.TextureStateShard(function, false, false);
        return RenderType.create("eyes_light", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true, RenderType.CompositeState.builder()
                .setShaderState(RenderStateShardAccess.EYES_LIGHT)
                .setTextureState(textureStateShard)
                .setTransparencyState(RenderStateShardAccess.ADDITIVE_TRANSPARENCY)
                .setWriteMaskState(RenderStateShardAccess.COLOR_WRITE)
                .createCompositeState(false));
    });

    public static final RenderType LIGHTNING = RenderType.create("lightning", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, true, RenderType.CompositeState.builder()
            .setShaderState(RenderStateShardAccess.RENDERTYPE_LIGHTNING_SHADER)
            .setWriteMaskState(RenderStateShardAccess.COLOR_DEPTH_WRITE)
            .setTransparencyState(RenderStateShardAccess.LIGHTNING_TRANSPARENCY)
            .setOutputState(RenderStateShardAccess.WEATHER_TARGET)
            .setWriteMaskState(RenderStateShardAccess.COLOR_WRITE)
            .createCompositeState(false));

    // Item
    public static void registerCosmicShaders(RegisterShadersEvent shader) {
        shader.registerShader(CCShaderInstance.create(shader.getResourceProvider(), new ResourceLocation(MODID, "cosmic"), DefaultVertexFormat.BLOCK), e -> {
            cosmicShader = (CCShaderInstance) e;
            cosmicTime = Objects.requireNonNull(cosmicShader.getUniform("time"));
            cosmicYaw = Objects.requireNonNull(cosmicShader.getUniform("yaw"));
            cosmicPitch = Objects.requireNonNull(cosmicShader.getUniform("pitch"));
            cosmicExternalScale = Objects.requireNonNull(cosmicShader.getUniform("externalScale"));
            cosmicOpacity = Objects.requireNonNull(cosmicShader.getUniform("opacity"));
            cosmicUVs = Objects.requireNonNull(cosmicShader.getUniform("cosmicuvs"));
            cosmicTime.set((float) renderTime + renderFrame);
            cosmicShader.onApply(() -> cosmicTime.set((float) renderTime + renderFrame));
        });
    }

    // Entity
    public static void registerStarrySkyShaders(RegisterShadersEvent shader) {
        shader.registerShader(CCShaderInstance.create(shader.getResourceProvider(), new ResourceLocation(MODID, "lola_allymia_starry_sky"), DefaultVertexFormat.NEW_ENTITY), e -> {
            starrySkyShader = (CCShaderInstance) e;
            portalTime = Objects.requireNonNull(starrySkyShader.getUniform("GameTime"));
            portalLayers = Objects.requireNonNull(starrySkyShader.getUniform("EndPortalLayers"));
            starScale = Objects.requireNonNull(starrySkyShader.getUniform("StarScale"));
            rainbowSpeed = Objects.requireNonNull(starrySkyShader.getUniform("RainbowSpeed"));
            rainbowScale = Objects.requireNonNull(starrySkyShader.getUniform("RainbowScale"));
            rainbowMix = Objects.requireNonNull(starrySkyShader.getUniform("RainbowMix"));
            opacity = Objects.requireNonNull(starrySkyShader.getUniform("Opacity"));
            rainbowSpeed.set(0.050F);
            rainbowScale.set(1.0F);
            rainbowMix.set(0.50F);
            opacity.set(1.0F);
            portalLayers.set(5); // Set optimal layer count for visibility
            float initTime = (renderTime + renderFrame) / 20.0F;
            portalTime.set(initTime);
            starrySkyShader.onApply(() -> {
                float time = (renderTime + renderFrame) / 20.0F;
                portalTime.set(time);
                portalLayers.set(5); // Ensure consistent layer count
            });
        });
    }

    // Item
    public static void registerStarrySkyItemShaders(RegisterShadersEvent shader) {
        shader.registerShader(CCShaderInstance.create(shader.getResourceProvider(), new ResourceLocation(MODID, "starry_sky_item"), DefaultVertexFormat.BLOCK), e -> {
            starrySkyShaderItem = (CCShaderInstance) e;
            portalTimeItem = Objects.requireNonNull(starrySkyShaderItem.getUniform("GameTime"));
            portalLayersItem = Objects.requireNonNull(starrySkyShaderItem.getUniform("EndPortalLayers"));
            starScaleItem = Objects.requireNonNull(starrySkyShaderItem.getUniform("StarScale"));
            rainbowSpeedItem = Objects.requireNonNull(starrySkyShaderItem.getUniform("RainbowSpeed"));
            rainbowScaleItem = Objects.requireNonNull(starrySkyShaderItem.getUniform("RainbowScale"));
            rainbowMixItem = Objects.requireNonNull(starrySkyShaderItem.getUniform("RainbowMix"));
            ItemOpacity = Objects.requireNonNull(starrySkyShaderItem.getUniform("Opacity"));
            camYaw = Objects.requireNonNull(starrySkyShaderItem.getUniform("CamYaw"));
            camPitch = Objects.requireNonNull(starrySkyShaderItem.getUniform("CamPitch"));
            rainbowSpeedItem.set(0.05F);
            rainbowScaleItem.set(1.0F);
            rainbowMixItem.set(0.5F);
            ItemOpacity.set(0.8F);
            portalLayersItem.set(15);
            starScaleItem.set(1.124F);
            camYaw.set(0.0F);
            camPitch.set(0.0F);
            float initItemTime = (renderTime + renderFrame) / 20.0F;
            portalTimeItem.set(initItemTime);
            starrySkyShaderItem.onApply(() -> {
                float itemTime = (renderTime + renderFrame) / 20.0F;
                portalTimeItem.set(itemTime);
                portalLayersItem.set(15);
            });
        });
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!Minecraft.getInstance().isPaused() && event.phase == TickEvent.Phase.END) {
            ++renderTime;
            tick += 1F;
            if (tick >= 720.0f) {
                tick = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        if (!Minecraft.getInstance().isPaused() && event.phase == TickEvent.Phase.START) {
            renderFrame = event.renderTickTime;
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void drawScreenPre(final ScreenEvent.Render.Pre e) {
        AdorableArmoryShaders.inventoryRender = true;
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void drawScreenPost(final ScreenEvent.Render.Post e) {
        AdorableArmoryShaders.inventoryRender = false;
    }
}