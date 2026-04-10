package flu.kitten.adorablearmory.tooltip;

import com.mojang.datafixers.util.Either;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.item.TrueDemonArrowItem;
import flu.kitten.adorablearmory.item.tool.TrueDemonBowItem;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class TooltipGatherHandler {

    private TooltipGatherHandler() {}

    @SubscribeEvent
    public static void gatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof TrueDemonBowItem || stack.getItem() instanceof TrueDemonArrowItem)) {
            return;
        }

        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();

        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> left = elements.get(i);

            if (left.left().isPresent()) {
                FormattedText text = left.left().get();

                if (text instanceof Component comp) {
                    if (comp.getString().startsWith("• ")) {
                        Component withoutBullet = Component.empty();
                        for (Component sib : comp.getSiblings()) {
                            withoutBullet = withoutBullet.copy().append(sib);
                        }
                        elements.set(i, Either.right(new BulletLineTooltipComponent(withoutBullet)));
                    }
                }
            }
        }
    }
}
