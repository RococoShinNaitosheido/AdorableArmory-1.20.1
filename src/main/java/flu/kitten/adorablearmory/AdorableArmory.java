package flu.kitten.adorablearmory;

import com.mojang.logging.LogUtils;
import flu.kitten.adorablearmory.api.BossBarRenderEvent;
import flu.kitten.adorablearmory.commands.BarrierCommand;
import flu.kitten.adorablearmory.commands.EnhancedTeleportCommand;
import flu.kitten.adorablearmory.network.NetworkHandler;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
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

@Mod(AdorableArmory.MODID)
@SuppressWarnings("unused")
public class AdorableArmory {

    private static final String PROTOCOL_VERSION = "1";
    public static final String MODID = "adorablearmory"; // Mod ID
    public static final Logger LOGGER = LogUtils.getLogger();

    public AdorableArmory() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::commonSetup);
        modBus.addListener(this::clientSetup);
        modBus.register(CommonConfig.class);
        AdorableArmoryRegister.register(modBus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC); // Register config
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static ResourceLocation path(String path) {
        return new ResourceLocation(MODID, path);
    }

    @SubscribeEvent
    public void registerCommands(RegisterCommandsEvent event) {
        LOGGER.info("Registering Enhanced Teleport Command...");
        EnhancedTeleportCommand.register(event.getDispatcher());
        event.getDispatcher().register(BarrierCommand.register());
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("HELLO FROM COMMON SETUP");
        if (CommonConfig.logDirtBlock) LOGGER.info("DIRT BLOCK → {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        LOGGER.info("{}{}", CommonConfig.magicIntro, CommonConfig.magicNumber);
        CommonConfig.items.forEach(item -> LOGGER.info("ITEM  → {}", ForgeRegistries.ITEMS.getKey(item)));
        event.enqueueWork(NetworkHandler::init); // init register
    }

    @SubscribeEvent
    public void clientSetup(FMLClientSetupEvent event) {
        LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        event.enqueueWork(() -> {
            // Register the boss bar render event handler
            MinecraftForge.EVENT_BUS.register(BossBarRenderEvent.class);
            AdorableArmory.LOGGER.info("Registered custom boss bar renderer");
        });
    }
}
