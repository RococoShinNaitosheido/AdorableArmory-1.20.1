package flu.kitten.adorablearmory.block;

import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class LolaBlockEntity extends BlockEntity {
    public LolaBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(AdorableArmoryRegister.LOLA_BLOCK_ENTITY.get(), blockPos, blockState);
    }
}
