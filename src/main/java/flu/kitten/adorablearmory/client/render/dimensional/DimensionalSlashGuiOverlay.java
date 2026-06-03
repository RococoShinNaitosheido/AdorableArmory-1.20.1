package flu.kitten.adorablearmory.client.render.dimensional;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class DimensionalSlashGuiOverlay {
    private DimensionalSlashGuiOverlay() {}

    @SubscribeEvent
    public static void register(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("dimensional_slash_break", (gui, graphics, partialTick, width, height) ->
                DimensionalSlashClientEffects.renderScreenOverlay(graphics, partialTick, width, height));
    }
}
