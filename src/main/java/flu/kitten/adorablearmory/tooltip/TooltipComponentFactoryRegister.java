package flu.kitten.adorablearmory.tooltip;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TooltipComponentFactoryRegister {

    private TooltipComponentFactoryRegister() {}

    @SubscribeEvent
    public static void registerTooltipComponentFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(BulletLineTooltipComponent.class, BulletLineClientTooltipComponent::new);
    }
}
