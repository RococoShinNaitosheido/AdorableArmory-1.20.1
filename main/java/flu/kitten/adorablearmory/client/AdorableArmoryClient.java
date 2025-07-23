package flu.kitten.adorablearmory.client;

import flu.kitten.adorablearmory.client.model.CosmicModelLoader;
import flu.kitten.adorablearmory.client.model.HaloModelLoader;
import flu.kitten.adorablearmory.client.model.ScarletLoraAlysiaModel;
import flu.kitten.adorablearmory.client.render.AnemiaSpecialEffectRender;
import flu.kitten.adorablearmory.client.render.LolaBlockEntityRenderer;
import flu.kitten.adorablearmory.client.render.ScarletLoraAlysiaRenderer;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

@Mod.EventBusSubscriber(modid = MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AdorableArmoryClient {

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) {
        AdorableArmoryShaders.registerCosmicShaders(event);
        AdorableArmoryShaders.registerStarrySkyShaders(event);
        AdorableArmoryShaders.registerStarrySkyItemShaders(event);
    }

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("cosmic", CosmicModelLoader.INSTANCE);
        event.register("halo", HaloModelLoader.INSTANCE);
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(AdorableArmoryRegister.LOLA_BLOCK_ENTITY.get(), LolaBlockEntityRenderer::new);
        event.registerEntityRenderer(AdorableArmoryRegister.SCARLET_LORA_ALYSIA.get(), ScarletLoraAlysiaRenderer::new);
        event.registerEntityRenderer(AdorableArmoryRegister.ANEMIA_SPECIAL_EFFECT.get(), AnemiaSpecialEffectRender::new); // AnemiaSpecialEffect
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ScarletLoraAlysiaModel.LAYER_LOCATION, ScarletLoraAlysiaModel::createBodyLayer);
    }
}