package flu.kitten.adorablearmory.entity.damagetype;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.lang.ref.WeakReference;

public class TrueDemonEffectImpl implements ITrueDemonEffect {

    private static final int continuousTime = 20;
    private static final float SAT_LOSS_PER_TICK = 0.12f; // 每tick直接扣的饱和度
    private static final int FOOD_LOSS_INTERVAL = 25;  // 饱和=0时 每隔多少tick扣1点饥饿
    private boolean hasEffect = false;
    private int remainingDuration = 0;
    private int regenBlockTicks = 0;
    private boolean blocksHealing = false;
    private int healingBlockDuration = 0;
    private int dotCooling = 0;
    private int foodLossCooling = 0;

    public TrueDemonEffectImpl(LivingEntity entity) {
        new WeakReference<>(entity);
    }

    @Override
    public void tick(LivingEntity entity) {
        if (healingBlockDuration > 0) {
            healingBlockDuration--;
            if (healingBlockDuration <= 0) {
                blocksHealing = false;
            }
        }

        if (hasEffect && remainingDuration > 0) {
            if (--dotCooling <= 0) {
                entity.hurt(TrueDemonDamageSource.causeTrueDemonDamage(entity.level(), entity), 1);
                dotCooling = continuousTime;
            }

            if (entity instanceof Player player && !player.isCreative() && !player.isSpectator()) {
                var food = player.getFoodData();

                float saturationLevel = food.getSaturationLevel();
                if (saturationLevel > 0) {
                    float newSat = Mth.clamp(saturationLevel - SAT_LOSS_PER_TICK, 0f, 25f);
                    food.setSaturation(newSat);

                    foodLossCooling = FOOD_LOSS_INTERVAL;
                } else {
                    if (--foodLossCooling <= 0) {
                        int level = food.getFoodLevel();
                        if (level > 0) {
                            food.setFoodLevel(level - 1);
                        }
                        foodLossCooling = FOOD_LOSS_INTERVAL;
                    }
                }
            }

            remainingDuration--;
            regenBlockTicks = Math.max(regenBlockTicks - 1, 0);

            if (remainingDuration <= 0) {
                hasEffect = false;
                regenBlockTicks = 0;
                dotCooling = 0;
                foodLossCooling = 0;
            }
        }
    }

    @Override
    public boolean hasEffect() {
        return hasEffect;
    }

    @Override
    public void setEffect(boolean hasEffect) {
        this.hasEffect = hasEffect;
        if (!hasEffect) {
            this.remainingDuration = 0;
            this.regenBlockTicks = 0;
            this.dotCooling = 0;
            this.foodLossCooling = 0;
        }
    }

    @Override
    public int getRemainingDuration() {
        return remainingDuration;
    }

    @Override
    public void setRemainingDuration(int duration) {
        this.remainingDuration = Math.max(duration, 0);
        if (duration > 0) {
            this.hasEffect = true;
            this.regenBlockTicks = duration;
            this.dotCooling = continuousTime;
            this.blocksHealing = true;
            this.healingBlockDuration = Math.max(this.healingBlockDuration, duration);
            this.foodLossCooling = FOOD_LOSS_INTERVAL;
        }
    }

    @Override
    public boolean blocksHealing() {
        return blocksHealing || hasEffect;
    }

    @Override
    public void setBlocksHealing(boolean blocksHealing) {
        this.blocksHealing = blocksHealing;
        if (!blocksHealing) {
            this.healingBlockDuration = 0;
        }
    }

    @Override
    public int getHealingBlockDuration() {
        return healingBlockDuration;
    }

    @Override
    public void setHealingBlockDuration(int duration) {
        this.healingBlockDuration = Math.max(duration, 0);
        this.blocksHealing = duration > 0 || this.blocksHealing;
    }
}
