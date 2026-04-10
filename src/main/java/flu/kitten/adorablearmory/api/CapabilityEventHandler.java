package flu.kitten.adorablearmory.api;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonEffectProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CapabilityEventHandler {
    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof LivingEntity entity) {
            TrueDemonEffectProvider provider = new TrueDemonEffectProvider(entity);
            event.addCapability(new ResourceLocation(AdorableArmory.MODID, "true_demon_effect"), provider);
            event.addListener(provider::invalidate);
        }
    }
}
