package flu.kitten.adorablearmory.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.client.render.ColoredGlintRenderTypes;
import flu.kitten.adorablearmory.client.render.ItemRenderCompatibilityContext;
import flu.kitten.adorablearmory.client.render.ItemShaderModCompat;
import flu.kitten.adorablearmory.client.render.NoOpVertexConsumer;
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

@Mixin(value = ItemRenderer.class, priority = 3000)
public abstract class TrueDemonGlintMixin {

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("HEAD"))
    private void beginItemRender(ItemStack stack, ItemDisplayContext ctx, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        ItemRenderCompatibilityContext.beginItemRender(ctx);
        ItemShaderModCompat.logCompatModeOnce();

        Integer glintColor = null;
        int resolved = ItemShaderModCompat.resolveGlintColor(stack, ctx);
        if (resolved != -1) {
            glintColor = resolved;
        }
        ItemRenderCompatibilityContext.setGlintColor(glintColor);
    }

    @Inject(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At("RETURN"))
    private void endItemRender(ItemStack stack, ItemDisplayContext ctx, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        ItemRenderCompatibilityContext.endItemRender();
    }

    @Redirect(method = "render(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;hasFoil()Z"))
    private boolean redirectHasFoilKeepOriginalPath(ItemStack instance, ItemStack stack, ItemDisplayContext ctx, boolean leftHand, PoseStack pose, MultiBufferSource buffers, int light, int overlay, BakedModel model) {
        return instance.hasFoil();
    }

    @Redirect(method = "getFoilBuffer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer getFoilBuffer(MultiBufferSource instance, RenderType type) {
        return redirectFoilBuffer(instance, type);
    }

    @Redirect(method = "getFoilBufferDirect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/MultiBufferSource;getBuffer(Lnet/minecraft/client/renderer/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer getFoilBufferDirect(MultiBufferSource instance, RenderType type) {
        return redirectFoilBuffer(instance, type);
    }

    private static VertexConsumer redirectFoilBuffer(MultiBufferSource instance, RenderType type) {
        Integer argb = ItemRenderCompatibilityContext.currentGlintColor();
        if (argb == null) {
            return instance.getBuffer(type);
        }

        if (!ColoredGlintRenderTypes.isGlintLike(type)) {
            return instance.getBuffer(type);
        }

        ItemDisplayContext context = ItemRenderCompatibilityContext.currentDisplayContext();

        if (ItemShaderModCompat.shouldUseSafeColoredGlintOverlay(context)) {
            ItemRenderCompatibilityContext.captureSuppressedGlintRenderType(type);
            return NoOpVertexConsumer.INSTANCE;
        }

        if (ItemShaderModCompat.shouldForceDirectColoredGlintCompat(context)) {
            RenderType forcedType = compatDirectTypeFromIncoming(type, argb);
            return instance.getBuffer(forcedType);
        }

        RenderType replaced = ColoredGlintRenderTypes.replaceIfGlint(type, argb);
        return instance.getBuffer(replaced);
    }

    private static RenderType compatDirectTypeFromIncoming(RenderType originalType, int argb) {
        if (originalType == RenderType.entityGlint() || originalType == RenderType.entityGlintDirect()) {
            return ColoredGlintRenderTypes.entityGlintDirect(argb);
        }
        return ColoredGlintRenderTypes.glintDirect(argb);
    }
}
