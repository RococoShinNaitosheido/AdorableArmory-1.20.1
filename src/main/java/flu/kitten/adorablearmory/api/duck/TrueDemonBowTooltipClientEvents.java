package flu.kitten.adorablearmory.api.duck;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.render.TrueDemonBowTooltipRenderer;
import flu.kitten.adorablearmory.item.TrueDemonArrowItem;
import flu.kitten.adorablearmory.item.tool.TrueDemonBowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrueDemonBowTooltipClientEvents {
    private TrueDemonBowTooltipClientEvents() {}

    @SubscribeEvent
    public static void tooltipPre(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        if (!(stack.getItem() instanceof TrueDemonBowItem || stack.getItem() instanceof TrueDemonArrowItem)) return;
        if (stack.hasCustomHoverName()) return;

        event.setCanceled(true);
        TrueDemonBowTooltipRenderer.render(event.getGraphics(), event.getFont(), event.getComponents(), event.getX(), event.getY(), event.getScreenWidth(), event.getScreenHeight(), event.getTooltipPositioner());
    }
}
