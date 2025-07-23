package flu.kitten.adorablearmory.client;

import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

@Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class AdorableArmoryDefault {
    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(AdorableArmoryRegister.SCARLET_LORA_ALYSIA.get(), ScarletLoraAlysia.createAttributes().build());
    }
}
