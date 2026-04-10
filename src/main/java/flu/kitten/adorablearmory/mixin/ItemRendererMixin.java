package flu.kitten.adorablearmory.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.api.client.model.PerspectiveModel;
import flu.kitten.adorablearmory.client.model.CosmicBakeModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.ForgeHooksClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void renderCosmicItem(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, BakedModel model, CallbackInfo ci) {
        if (model instanceof CosmicBakeModel iItemRenderer) {
            ci.cancel();
            poseStack.pushPose();
            final CosmicBakeModel renderer = (CosmicBakeModel) ForgeHooksClient.handleCameraTransforms(poseStack, iItemRenderer, context, leftHand); // @ApiStatus.Internal 将来可能移除或行为会变
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            renderer.renderItem(stack, context, poseStack, buffers, packedLight, packedOverlay);
            poseStack.popPose();
        } else if (model instanceof PerspectiveModel iItemRenderer) {
            poseStack.pushPose();
            final PerspectiveModel renderer = (PerspectiveModel) iItemRenderer.applyTransform(context, poseStack, leftHand);
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            renderer.renderItem(stack, context, poseStack, buffers, packedLight, packedOverlay);
            poseStack.popPose();
        }
    }
}