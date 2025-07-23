package flu.kitten.adorablearmory.mixin;

import flu.kitten.adorablearmory.item.SparklingDreamIdolStar;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@SuppressWarnings({"ConstantConditions","unused"})
public final class AdorableArmoryMixins {

    @Mixin(ItemEntity.class)
    public interface ItemEntityAccessor {
        @Accessor("age") void setAge(int age);
    }

    @Mixin(LivingEntity.class)
    public static abstract class LivingEntityMixin {
        @Inject(method = "tick", at = @At("HEAD"))
        private void setTick(CallbackInfo tick) {
            LivingEntity livingEntity = (LivingEntity) (Object) this;
            ItemStack stack = livingEntity.getMainHandItem();
            if (stack.getItem() instanceof SparklingDreamIdolStar) {
                livingEntity.setHealth(9999);
                livingEntity.deathTime = 0;
                livingEntity.hurtTime = 0;
                livingEntity.heal(9999);
            }
        }

        @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
        private void setHurt(DamageSource p_21016_, float p_21017_, CallbackInfoReturnable<Boolean> callbackInfoReturnable) {
            LivingEntity livingEntity = (LivingEntity) (Object) this;
            ItemStack stack = livingEntity.getMainHandItem();
            if (stack.getItem() instanceof SparklingDreamIdolStar) {
                callbackInfoReturnable.setReturnValue(false);
            }
        }
    }

    @Mixin(Entity.class)
    public static abstract class MixinEntity {
        @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
        private void remove(CallbackInfo ci) {
            Entity entity = (Entity) (Object) this;
            if (entity instanceof ItemEntity) {
                ItemStack stack = ((ItemEntity) entity).getItem();
                if (stack.getItem() == AdorableArmoryRegister.SPARKLING_DREAM_IDOL_STAR.get()) {
                    ci.cancel();
                }
            }
        }
    }

    @Mixin(ItemEntity.class)
    public static abstract class MixinItemEntity {
        @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
        private void setHurt(DamageSource p_32013_, float p_32014_, CallbackInfoReturnable<Boolean> cir) {
            ItemEntity itemEntity = (ItemEntity) (Object) this;
            ItemStack itemStack = itemEntity.getItem();

            if (itemStack.getItem() instanceof SparklingDreamIdolStar)
            {
                cir.setReturnValue(false);
            }
        }

        @Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
        private void setConstructed1(EntityType<? extends ItemEntity> type, Level level, CallbackInfo ci) {
            ItemEntity itemEntity = (ItemEntity) (Object) this;
            setAgeIfSparklingDreamIdolStar(itemEntity);
        }

        @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
        private void setConstructed2(Level level, double x, double y, double z, ItemStack itemStack, CallbackInfo ci) {
            ItemEntity itemEntity = (ItemEntity) (Object) this;
            setAgeIfSparklingDreamIdolStar(itemEntity);
        }

        @Inject(method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;DDD)V", at = @At("TAIL"))
        private void setConstructed3(Level level, double x, double y, double z, ItemStack itemStack, double deltaX, double deltaY, double deltaZ, CallbackInfo ci) {
            ItemEntity itemEntity = (ItemEntity) (Object) this;
            setAgeIfSparklingDreamIdolStar(itemEntity);
        }

        @Inject(method = "<init>(Lnet/minecraft/world/entity/item/ItemEntity;)V", at = @At("TAIL"))
        private void setConstructed4(ItemEntity original, CallbackInfo ci) {
            ItemEntity itemEntity = (ItemEntity) (Object) this;
            setAgeIfSparklingDreamIdolStar(itemEntity);
        }

        private void setAgeIfSparklingDreamIdolStar(ItemEntity entity) {
            ItemStack itemStack = entity.getItem();
            if (itemStack.getItem() instanceof SparklingDreamIdolStar) {
                ((ItemEntityAccessor) entity).setAge(-32768);
            }
        }
    }
}
