package flu.kitten.adorablearmory.block;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class LolaItemBlock extends BlockItem {
    public LolaItemBlock(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return (Component.translatable("block.adorablearmory.lola"));
    }
}
