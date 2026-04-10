package flu.kitten.adorablearmory.entity.damagetype;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;

@SuppressWarnings("unused")
public class TrueDemonTypes {
    public static final ResourceKey<DamageType> TRUE_DEMON = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AdorableArmory.MODID, "true_demon"));

    public static class TrueDemonDamageUtil {
        public static boolean dealTrueDamage(LivingEntity target, float amount, @Nullable Entity source) {
            if (target == null) return false;
            if (target.level().isClientSide()) return false;
            if (amount <= 0) return false;
            return TrueDemonDamageSource.applyTrueDemonDamage(target, amount, source, target.level());
        }

        public static boolean dealPercentageTrueDamage(LivingEntity target, float percentage, @Nullable Entity source) {
            if (target == null) return false;
            float damage = target.getMaxHealth() * Math.max(0, Math.min(1.0f, percentage));
            return dealTrueDamage(target, damage, source);
        }

        public static boolean setHealthDirectly(LivingEntity target, float newHealth, @Nullable Entity source) {
            if (target == null) return false;
            if (target.level().isClientSide()) return false;

            float currentHealth = target.getHealth();
            float clampedNew = Math.max(0, Math.min(target.getMaxHealth(), newHealth));
            float damage = currentHealth - clampedNew;

            if (damage > 0) {
                return dealTrueDamage(target, damage, source);
            } else if (clampedNew > currentHealth) {
                target.setHealth(clampedNew);
                return true;
            }

            return false;
        }

        public static boolean trueDemonMechanismKill(LivingEntity target, @Nullable Entity source) {
            if (target == null) return false;
            if (target.level().isClientSide()) return false;
            AdorableArmory.LOGGER.info("[TrueDemon] phase1 trueDemonCodeKill called. target={}, source={}", target.getUUID(), source != null ? source.getUUID() : "null");
            boolean success = TrueDemonDamageSource.applyTrueDemonDamage(target, target.getMaxHealth() + target.getAbsorptionAmount(), source, target.level());
            if (!success || target.isAlive() || !target.isDeadOrDying()) {
                TrueDemonDamageSource.trueDemonCodeKill(target, source);
            }
            return true;
        }
    }
}
