package flu.kitten.adorablearmory.mixin;

import flu.kitten.adorablearmory.client.render.CosmicParticleType;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TerrainParticle.class)
public abstract class TerrainParticleGetTypeMixin extends TextureSheetParticle {

    @Unique
    private boolean useCosmic;

    protected TerrainParticleGetTypeMixin(ClientLevel level, double x, double y, double z) {
        super(level, x, y, z);
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;)V", at = @At("TAIL"))
    private void captureStateA(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, BlockState state, CallbackInfo ci) {
        this.useCosmic = state != null && state.is(AdorableArmoryRegister.LOLA.get());
        if (this.useCosmic) {
            System.out.println("[DEBUG] Mixin Captured LOLA particle at: " + x + ", " + y + ", " + z);
        }
    }

    @Inject(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;DDDDDDLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V", at = @At("TAIL"))
    private void captureStateB(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, BlockState state, BlockPos pos, CallbackInfo ci) {
        this.useCosmic = state != null && state.is(AdorableArmoryRegister.LOLA.get());
        if (this.useCosmic) {
            System.out.println("[DEBUG] Mixin Captured LOLA particle at: " + x + ", " + y + ", " + z);
        }
    }

    @Inject(method = "getRenderType()Lnet/minecraft/client/particle/ParticleRenderType;", at = @At("HEAD"), cancellable = true)
    private void getRenderType(CallbackInfoReturnable<ParticleRenderType> cir) {
       if (this.useCosmic) {
           System.out.println("[DEBUG] Switching RenderType to COSMIC for particle!");
           cir.setReturnValue(CosmicParticleType.INSTANCE);
       }
    }
}
