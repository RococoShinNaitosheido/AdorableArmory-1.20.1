package flu.kitten.adorablearmory.client.compat.oculus.itemoutline;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.compat.oculus.ItemShaderModCompat;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ItemOutlineCompatEvents {

    @SubscribeEvent
    public static void renderGuiPre(RenderGuiEvent.Pre event) {
        if (!ItemShaderModCompat.isOculusEmbeddiumActive()) {
            return;
        }
        if (Minecraft.getInstance().screen != null) {
            return;
        }
        ItemOutlinePostProcessor.compositeDeferredFirstPersonIfActive();
    }

    @SubscribeEvent
    public static void screenRenderPre(ScreenEvent.Render.Pre event) {
        if (!ItemShaderModCompat.isOculusEmbeddiumActive()) {
            return;
        }
        if (Minecraft.getInstance().screen == null) {
            return;
        }
        ItemOutlinePostProcessor.compositeDeferredFirstPersonIfActive();
    }

    private ItemOutlineCompatEvents() {}
}
