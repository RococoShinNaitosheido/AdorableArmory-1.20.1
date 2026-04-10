package flu.kitten.adorablearmory.entity.weapons.cap;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DemonArrowStuckProvider implements ICapabilityProvider, INBTSerializable<CompoundTag> {

    public static final ResourceLocation ID = new ResourceLocation(AdorableArmory.MODID, "demon_arrow_stuck");
    public static final Capability<IDemonArrowStuckCap> CAPABILITY = CapabilityManager.get(new CapabilityToken<>() {});
    private final DemonArrowStuckCapImpl backend = new DemonArrowStuckCapImpl();
    private final LazyOptional<IDemonArrowStuckCap> optional = LazyOptional.of(() -> backend);

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == CAPABILITY ? optional.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Count", backend.getCount());
        tag.putInt("DropCooling", backend.getDropCooling());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        backend.setCount(nbt.getInt("Count"));
        backend.setDropCooling(nbt.getInt("DropCooling"));
    }
}
