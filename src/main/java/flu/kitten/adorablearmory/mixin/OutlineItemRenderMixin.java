package flu.kitten.adorablearmory.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.client.compat.oculus.itemoutline.ItemOutlineDispatcher;
import flu.kitten.adorablearmory.client.compat.oculus.itemoutline.ItemOutlinePostProcessor;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class OutlineItemRenderMixin {
    private static final String TARGET = "Lnet/minecraft/client/renderer/entity/ItemRenderer;renderModelLists(Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemStack;IILcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V";

    @Inject(method = "render", at = @At(value = "INVOKE", target = TARGET))
    private void renderOutlineMask(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, BakedModel model, CallbackInfo ci) {
        ItemOutlineDispatcher.renderIfNeeded((ItemRenderer) (Object) this, stack, context, poseStack, model);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = TARGET, shift = At.Shift.AFTER))
    private void compositeOutlineMask(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, BakedModel model, CallbackInfo ci) {
        if (ItemOutlinePostProcessor.shouldDeferComposite(context)) {
            return;
        }
        ItemOutlinePostProcessor.compositeItemMaskIfActive(context);
    }
}
