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
    protected void spawnDestroyParticles(@NotNull Level level, @NotNull Player player, @NotNull BlockPos blockPos, @NotNull BlockState blockState) {
        super.spawnDestroyParticles(level, player, blockPos, blockState);
    }

    @Override
    public void playerDestroy(@NotNull Level level, @NotNull Player player, @NotNull BlockPos blockPos, @NotNull BlockState blockState, @Nullable BlockEntity blockEntity, @NotNull ItemStack stack) {
        super.playerDestroy(level, player, blockPos, blockState, blockEntity, stack);
    }
}
