package flu.kitten.adorablearmory.client;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class BlackHoleEventRenderingStage {

    @SubscribeEvent
    public static void blackHoleRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRIPWIRE_BLOCKS) {
            BlackHoleLensClient.renderQueuedLensAfterOpaque();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            BlackHoleLateRenderQueue.renderAll(event);
        }
    }

    public BlackHoleEventRenderingStage() {}
}
