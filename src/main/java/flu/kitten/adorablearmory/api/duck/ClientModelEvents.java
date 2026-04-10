package flu.kitten.adorablearmory.api.duck;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModelEvents {

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(AdorableArmoryRegister.TRUE_DEMON_BOW.get(), new ResourceLocation("pulling"), (stack, level, living, seed) -> living != null && living.isUsingItem() && living.getUseItem() == stack ? 1.0f : 0.0f);
            ItemProperties.register(AdorableArmoryRegister.TRUE_DEMON_BOW.get(),
                    new ResourceLocation("pull"),
                    (stack, level, living, seed) -> {
                        if (living == null) return 0.0f;
                        if (living.getUseItem() != stack) return 0f;
                        float time = (stack.getUseDuration() - living.getUseItemRemainingTicks()) / 20f;
                        return Math.min(time, 1.0f);
                    }
            );
        });
    }

    private ClientModelEvents() {}
}
