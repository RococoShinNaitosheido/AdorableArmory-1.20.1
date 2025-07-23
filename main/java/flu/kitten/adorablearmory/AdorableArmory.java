package flu.kitten.adorablearmory;

import com.mojang.logging.LogUtils;
import flu.kitten.adorablearmory.api.Handler;
import flu.kitten.adorablearmory.client.CosmicRenderProperties;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import flu.kitten.adorablearmory.register.CosmicRenderingRegistry;
import flu.kitten.adorablearmory.render.ItemEntityOutlineRenderer;
import flu.kitten.adorablearmory.render.RenderItemTooltip;
import flu.kitten.adorablearmory.util.TransformUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

@SuppressWarnings("unused")
@Mod(AdorableArmory.MODID)
public class AdorableArmory {

    private static final String PROTOCOL_VERSION = "1";
    public static final String MODID = "adorablearmory";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AdorableArmory() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        modBus.register(Config.class);

        AdorableArmoryRegister.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(Handler.class);
        MinecraftForge.EVENT_BUS.register(ItemEntityOutlineRenderer.class);
        MinecraftForge.EVENT_BUS.register(RenderItemTooltip.class);
    }

    public static ResourceLocation path(String path) {
        return new ResourceLocation(MODID, path);
    }

    private void commonSetup(final FMLCommonSetupEvent evt) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (Config.logDirtBlock) LOGGER.info("DIRT BLOCK → {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        LOGGER.info("{}{}", Config.magicIntro, Config.magicNumber);
        Config.items.forEach(item -> LOGGER.info("ITEM  → {}", ForgeRegistries.ITEMS.getKey(item)));
    }

    @SubscribeEvent
    public void clientSetup(FMLClientSetupEvent evt) {
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        evt.enqueueWork(() -> {
            // CosmicRegister
            CosmicRenderProperties item = new CosmicRenderProperties(TransformUtils.DEFAULT_TOOL, AdorableArmoryShaders.SKY_ITEM);
            CosmicRenderingRegistry.registerRenderItem(AdorableArmoryRegister.SOFT_LIGHT_END_LOVE.get(), item);

            CosmicRenderProperties item1 = new CosmicRenderProperties(TransformUtils.DEFAULT_ITEM, AdorableArmoryShaders.COSMIC_RENDER_TYPE);
            CosmicRenderingRegistry.registerRenderItem(AdorableArmoryRegister.SPARKLING_DREAM_IDOL_STAR.get(), item1);
        });
    }
}
