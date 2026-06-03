package flu.kitten.adorablearmory.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import flu.kitten.adorablearmory.client.model.CosmicModelLoader;
import flu.kitten.adorablearmory.client.model.HaloModelLoader;
import flu.kitten.adorablearmory.client.model.ScarletLoraAlysiaModel;
import flu.kitten.adorablearmory.client.render.*;
import flu.kitten.adorablearmory.client.render.dimensional.DimensionalSlashBloomRenderer;
import flu.kitten.adorablearmory.client.render.dimensional.DimensionalSlashScreenShader;
import flu.kitten.adorablearmory.client.render.dimensional.DimensionalSlashWorldShardRenderer;
import flu.kitten.adorablearmory.client.render.particle.TrueDemonParticle;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.entity.effect.entityrender.TrueDemonBlackHoleRender;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.IOException;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

@SuppressWarnings("unused")
@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AdorableArmoryClient {

    private static ShaderInstance trueDemonParticleShader;
    private static final ResourceLocation particleLocation = new ResourceLocation(MODID, "true_demon_glow_particle");

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        AdorableArmoryShaders.registerCosmicShaders(event);
        AdorableArmoryShaders.registerCosmicParticleShaders(event);
        AdorableArmoryShaders.registerStarrySkyShaders(event);
        AdorableArmoryShaders.registerStarrySkyItemShaders(event);

        // location particle shader
        event.registerShader(new ShaderInstance(event.getResourceProvider(), particleLocation, DefaultVertexFormat.PARTICLE), shader -> AdorableArmoryClient.trueDemonParticleShader = shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(MODID, "black_hole_lens"), DefaultVertexFormat.POSITION_TEX), BlackHoleLensClient::shaderLoaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(MODID, "screen_glass_shard"), DefaultVertexFormat.POSITION_COLOR_TEX), DimensionalSlashScreenShader::shaderLoaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(MODID, "dimensional_slash_blur"), DefaultVertexFormat.POSITION_TEX), DimensionalSlashBloomRenderer::blurShaderLoaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(MODID, "dimensional_slash_composite"), DefaultVertexFormat.POSITION_TEX), DimensionalSlashBloomRenderer::compositeShaderLoaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(MODID, "dimensional_slash_screen_fx"), DefaultVertexFormat.POSITION_TEX), DimensionalSlashScreenShader::screenFxShaderLoaded);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(MODID, "dimensional_slash_world_shard"), DefaultVertexFormat.NEW_ENTITY), DimensionalSlashWorldShardRenderer::shaderLoaded);
    }

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("cosmic", CosmicModelLoader.INSTANCE);
        event.register("halo", HaloModelLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AdorableArmoryRegister.LOLA_BLOCK_ENTITY.get(), LolaBlockEntityRenderer::new);
        event.registerEntityRenderer(AdorableArmoryRegister.SCARLET_LORA_ALYSIA.get(), ScarletLoraAlysiaRenderer::new);
        event.registerEntityRenderer(AdorableArmoryRegister.ANEMIA_SPECIAL_EFFECT.get(), AnemiaSpecialEffectRender::new);
        event.registerEntityRenderer(AdorableArmoryRegister.TRUE_DEMON_ARROW_ENTITY.get(), TrueDemonArrowRender::new);
        event.registerEntityRenderer(AdorableArmoryRegister.TRUE_DEMON_CAVE.get(), TrueDemonBlackHoleRender::new);
    }

    // Particle Factories
    @SubscribeEvent
    public static void registerParticleFactories(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(AdorableArmoryRegister.TRUE_DEMON_PARTICLE.get(), TrueDemonParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerReload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(TrueDemonBlackHoleRender.ModelHolder.INSTANCE);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ScarletLoraAlysiaModel.LAYER_LOCATION, ScarletLoraAlysiaModel::createBodyLayer);
    }

    @Nullable
    public static ShaderInstance getTrueDemonParticleShader() {
        return trueDemonParticleShader;
    }
}
