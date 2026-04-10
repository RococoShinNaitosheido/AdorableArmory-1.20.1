package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GuiChromaPostOverlayHook {

    private GuiChromaPostOverlayHook() {}

    @SubscribeEvent
    public static void guiOverlays(RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("chroma_post", (gui, graphics, partialTick, w, h) -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (!RadialChromaEffect.wantActive) return;
            RenderSystem.disableScissor();
            RadialChromaEffect.renderPostInGuiStage(partialTick);
        });
    }
}
