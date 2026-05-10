package flu.kitten.adorablearmory.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.client.compat.oculus.CosmicItemLateRenderQueue;
import flu.kitten.adorablearmory.client.compat.oculus.ItemShaderModCompat;
import flu.kitten.adorablearmory.client.compat.oculus.LolaCosmicBlockLateRenderQueue;
import flu.kitten.adorablearmory.client.compat.oculus.LolaCosmicParticleLateRenderQueue;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class, priority = 500)
public abstract class LolaCosmicAfterLevelMixin {

    @Inject(method = "renderLevel", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/GameRenderer;renderHand:Z", ordinal = 0))
    private void renderLolaCosmicAfterLevel(float partialTick, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        if (ItemShaderModCompat.isOculusShaderPackActive()) {
            LolaCosmicBlockLateRenderQueue.renderAfterLevel();
            CosmicItemLateRenderQueue.renderBeforeHand();
            LolaCosmicParticleLateRenderQueue.renderAfterLevel(poseStack);
        } else {
            LolaCosmicParticleLateRenderQueue.renderAfterLevel(poseStack);
        }
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void renderLolaCosmicAfterShaderpackFinal(float partialTick, long finishTimeNano, PoseStack poseStack, CallbackInfo ci) {
        if (ItemShaderModCompat.isOculusShaderPackActive()) {
            CosmicItemLateRenderQueue.renderAfterHand();
        }
    }
}
