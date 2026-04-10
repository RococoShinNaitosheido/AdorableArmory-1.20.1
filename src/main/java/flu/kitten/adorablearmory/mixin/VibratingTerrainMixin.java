package flu.kitten.adorablearmory.mixin;

import flu.kitten.adorablearmory.client.render.barrier.BarrierAreaUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class VibratingTerrainMixin {

    // setBlock(BlockPos, BlockState, int) -> boolean
    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", at = @At("HEAD"), cancellable = true)
    private void setBlock(BlockPos blockPos, BlockState state, int flags, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide && BarrierAreaUtil.inAnyBarrier(level, blockPos)) {
            BlockState blockState = level.getBlockState(blockPos);
            level.sendBlockUpdated(blockPos, blockState, state, 3);
            cir.setReturnValue(false);
        }
    }

    // setBlockAndUpdate(BlockPos, BlockState) -> boolean
    @Inject(method = "setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private void setBlockAndUpdate(BlockPos blockPos, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        Level level = (Level) (Object) this;
        if (!level.isClientSide && BarrierAreaUtil.inAnyBarrier(level, blockPos)) {
            BlockState blockState = level.getBlockState(blockPos);
            level.sendBlockUpdated(blockPos, blockState, state, 3);
            cir.setReturnValue(false);
        }
    }
}
