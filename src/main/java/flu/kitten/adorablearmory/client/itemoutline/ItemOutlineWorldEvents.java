package flu.kitten.adorablearmory.client.itemoutline;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemOutlineWorldEvents {

    @SubscribeEvent
    public static void renderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            ItemOutlinePostProcessor.compositeWorldMaskIfActive();
        }
    }

    private ItemOutlineWorldEvents() {}
}
