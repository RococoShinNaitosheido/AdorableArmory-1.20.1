package flu.kitten.adorablearmory.mixin;

import flu.kitten.adorablearmory.api.duck.ITrueDemonExecutionTarget;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonDamageSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements ITrueDemonExecutionTarget {

    @Unique
    private boolean executionMark = false;

    @Override
    public void markForExecution() {
        this.executionMark = true;
    }

    @Override
    public boolean isMarkedForExecution() {
        return this.executionMark;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void tickHead(CallbackInfo ci) {
        if (this.executionMark) {
            LivingEntity target = (LivingEntity) (Object) this;
            if (target.getHealth() > 0) {
                target.setHealth(0.0F);
            }
            target.invulnerableTime = 0;
            if (!target.isRemoved() && !target.level().isClientSide) {
                if (!target.isDeadOrDying()) {
                    target.die(TrueDemonDamageSource.causeTrueDemonDamage(target.level(), null));
                }
                target.remove(Entity.RemovalReason.KILLED);
            }
        }
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void setHealth(float health, CallbackInfo ci) {
        if (this.executionMark && health > 0) {
            ci.cancel();
            ((LivingEntity) (Object) this).setHealth(0);
        }
    }

    @Inject(method = "isDeadOrDying", at = @At("HEAD"), cancellable = true)
    private void isDeadOrDying(CallbackInfoReturnable<Boolean> cir) {
        if (this.executionMark) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "checkTotemDeathProtection", at = @At("HEAD"), cancellable = true)
    private void checkTotem(DamageSource source, CallbackInfoReturnable<Boolean> cir) {
        if (this.executionMark) {
            cir.setReturnValue(false);
        }
    }
}
