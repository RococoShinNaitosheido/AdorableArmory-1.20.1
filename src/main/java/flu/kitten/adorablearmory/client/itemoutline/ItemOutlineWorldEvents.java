package flu.kitten.adorablearmory.client.itemoutline;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.render.ItemShaderModCompat;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemOutlineWorldEvents {

    @SubscribeEvent
    public static void renderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        ItemOutlinePostProcessor.compositeWorldMaskIfActive();

        if (ItemShaderModCompat.isOculusEmbeddiumActive() && Minecraft.getInstance().screen == null) {
            ItemOutlinePostProcessor.compositeDeferredFirstPersonIfActive();
        }
    }

    private ItemOutlineWorldEvents() {}
}
