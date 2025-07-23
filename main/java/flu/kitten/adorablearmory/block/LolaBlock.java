package flu.kitten.adorablearmory.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.*;

public class LolaBlock extends Block implements EntityBlock {
    public LolaBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        return new LolaBlockEntity(blockPos,blockState);
    }

    @Override
    public void playerDestroy(@NotNull Level p_49827_, @NotNull Player p_49828_, @NotNull BlockPos p_49829_, @NotNull BlockState p_49830_, @Nullable BlockEntity p_49831_, @NotNull ItemStack p_49832_) {
        super.playerDestroy(p_49827_, p_49828_, p_49829_, p_49830_, p_49831_, p_49832_);
    }
}
