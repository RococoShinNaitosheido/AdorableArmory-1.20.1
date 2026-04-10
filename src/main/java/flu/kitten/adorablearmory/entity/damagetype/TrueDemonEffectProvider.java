package flu.kitten.adorablearmory.entity.damagetype;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

public class TrueDemonEffectProvider implements ICapabilitySerializable<CompoundTag> {
    private final TrueDemonEffectImpl implementation;
    private final LazyOptional<ITrueDemonEffect> instance;
    private final LivingEntity owner;

    public TrueDemonEffectProvider(LivingEntity entity) {
        this.implementation = new TrueDemonEffectImpl(entity);
        this.instance = LazyOptional.of(() -> implementation);
        this.owner = entity;
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction direction) {
        if (capability == Capabilities.TRUE_DEMON_EFFECT) {
            return instance.cast();
        }
        return LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", 2);
        tag.putBoolean("hasEffect", implementation.hasEffect());
        tag.putInt("remainingDuration", implementation.getRemainingDuration());
        tag.putBoolean("blocksHealing", implementation.blocksHealing());
        tag.putInt("healingBlockDuration", implementation.getHealingBlockDuration());
        tag.putLong("lastGameTime", owner.level().getGameTime());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        boolean hasEffect = tag.getBoolean("hasEffect");
        int remainingDuration = tag.getInt("remainingDuration");
        boolean blocksHealing = tag.contains("blocksHealing") && tag.getBoolean("blocksHealing");
        int healingBlockDuration = tag.contains("healingBlockDuration") ? tag.getInt("healingBlockDuration") : 0;

        if (tag.contains("lastGameTime")) {
            long last = tag.getLong("lastGameTime");
            long now = owner.level().getGameTime();
            int offlineTicks = (int) Math.max(0, now - last);
            remainingDuration = Math.max(0, remainingDuration - offlineTicks);
            healingBlockDuration = Math.max(0, healingBlockDuration - offlineTicks);
        }

        implementation.setEffect(hasEffect && remainingDuration > 0);
        implementation.setRemainingDuration(remainingDuration);
        implementation.setBlocksHealing(blocksHealing && healingBlockDuration > 0);
        implementation.setHealingBlockDuration(healingBlockDuration);
    }

    public void invalidate() {
        instance.invalidate();
    }
}
