package flu.kitten.adorablearmory.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.api.duck.IGlintColorProvider;
import flu.kitten.adorablearmory.client.render.ColoredGlintRenderTypes;
import flu.kitten.adorablearmory.client.render.ItemRenderCompatibilityContext;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class TrueDemonGlintMixin {

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("HEAD"))
    private void beginItemRender(ItemStack stack, ItemDisplayContext ctx, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        ItemRenderCompatibilityContext.beginItemRender();

        Integer glintColor = null;
        if (stack.hasFoil() && stack.getItem() instanceof IGlintColorProvider provider) {
            int argb = provider.getGlintColor(stack);
            if (argb != -1) {
                glintColor = argb;
            }
        }

        ItemRenderCompatibilityContext.setGlintColor(glintColor);
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("RETURN"))
    private void endItemRender(ItemStack stack, ItemDisplayContext ctx, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        ItemRenderCompatibilityContext.endItemRender();
    }

    @Redirect(method = "getFoilBuffer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer getFoilBuffer(MultiBufferSource instance, RenderType type) {
        Integer argb = ItemRenderCompatibilityContext.currentGlintColor();
        if (argb != null) {
            type = ColoredGlintRenderTypes.replaceIfGlint(type, argb);
        }
        return instance.getBuffer(type);
    }

    @Redirect(method = "getFoilBufferDirect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer getFoilBufferDirect(MultiBufferSource instance, RenderType type) {
        Integer argb = ItemRenderCompatibilityContext.currentGlintColor();
        if (argb != null) {
            type = ColoredGlintRenderTypes.replaceIfGlint(type, argb);
        }
        return instance.getBuffer(type);
    }
}
