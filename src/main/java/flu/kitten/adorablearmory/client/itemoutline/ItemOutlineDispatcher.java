package flu.kitten.adorablearmory.client.itemoutline;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.api.duck.IItemOutlineItem;
import flu.kitten.adorablearmory.client.render.ItemRenderCompatibilityContext;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public final class ItemOutlineDispatcher {

    public static void renderIfNeeded(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, BakedModel model) {
        ItemOutlineData data = getOutlineData(stack, context);
        if (data == null) {
            return;
        }

        if (model.isCustomRenderer()) {
            return;
        }

        if (!ItemRenderCompatibilityContext.tryStartOutlineCapture()) {
            return;
        }

        try {
            ItemOutlinePostProcessor.prepareCapture(context);
            ItemOutlinePostProcessor.renderItemMask(renderer, stack, context, poseStack, model, data);
        } finally {
            ItemRenderCompatibilityContext.finishOutlineCapture();
        }
    }

    private static @Nullable ItemOutlineData getOutlineData(ItemStack stack, ItemDisplayContext context) {
        if (stack.isEmpty()) {
            return null;
        }

        if (!(stack.getItem() instanceof IItemOutlineItem outlined)) {
            return null;
        }

        return outlined.getItemOutline(stack, context);
    }

    private ItemOutlineDispatcher() {}
}
