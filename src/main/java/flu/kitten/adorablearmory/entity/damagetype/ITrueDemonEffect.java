package flu.kitten.adorablearmory.entity.damagetype;

import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.AutoRegisterCapability;

@AutoRegisterCapability
public interface ITrueDemonEffect {
    boolean hasEffect();
    void setEffect(boolean hasEffect);
    int getRemainingDuration();
    void setRemainingDuration(int duration);
    void tick(LivingEntity entity);
    boolean blocksHealing();
    void setBlocksHealing(boolean blocksHealing);
    int getHealingBlockDuration();
    void setHealingBlockDuration(int duration);
}
