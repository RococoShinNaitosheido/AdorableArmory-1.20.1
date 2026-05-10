package flu.kitten.adorablearmory.client.compat.oculus.glint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.api.duck.IGlintColorProvider;
import flu.kitten.adorablearmory.client.compat.oculus.ItemRenderCompatibilityContext;
import flu.kitten.adorablearmory.client.compat.oculus.ItemShaderModCompat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ColoredGlintOverlayDispatcher {
    public static void renderIfNeeded(ItemRenderer renderer, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, BakedModel model) {
        if (!ItemShaderModCompat.shouldUseSafeColoredGlintOverlay(context)) {
            return;
        }

        if (stack.isEmpty()) {
            return;
        }

        if (!(stack.getItem() instanceof IGlintColorProvider provider)) {
            return;
        }

        if (!stack.hasFoil()) {
            return;
        }

        if (model.isCustomRenderer()) {
            return;
        }

        int argb = provider.getGlintColor(stack);
        if (argb == -1) {
            return;
        }

        if (((argb >>> 24) & 0xFF) == 0) {
            return;
        }

        if ((argb & 0x00FFFFFF) == 0) {
            return;
        }

        RenderType overlayGlintType = resolveOverlayGlintType(context, argb);

        ItemRenderCompatibilityContext.beginManualGlintPass();
        try {
            VertexConsumer baseConsumer = bufferSource.getBuffer(overlayGlintType);
            renderer.renderModelLists(model, stack, packedLight, packedOverlay, poseStack, baseConsumer);
        } finally {
            ItemRenderCompatibilityContext.finishManualGlintPass();
        }
    }

    private static RenderType resolveOverlayGlintType(ItemDisplayContext context, int argb) {
        RenderType suppressed = ItemRenderCompatibilityContext.currentSuppressedGlintRenderType();
        if (suppressed != null && ColoredGlintRenderTypes.isGlintLike(suppressed)) {
            return ColoredGlintRenderTypes.replaceIfGlint(suppressed, argb);
        }

        return OculusSafeColoredGlintOverlayTypes.coloredItemGlint(argb);
    }

    private ColoredGlintOverlayDispatcher() {}
}
