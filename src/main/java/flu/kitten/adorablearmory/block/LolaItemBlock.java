package flu.kitten.adorablearmory.block;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.api.shader.ShaderLayerItem;
import flu.kitten.adorablearmory.api.shader.ShaderLayerModelTransform;
import flu.kitten.adorablearmory.api.shader.ShaderLayerProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

public class LolaItemBlock extends BlockItem implements ShaderLayerItem {
    private static final ShaderLayerProperties SHADER_LAYER = ShaderLayerProperties.cosmic(ShaderLayerModelTransform.DEFAULT_BLOCK_ITEM, AdorableArmory.path("item/block_mask"));

    public LolaItemBlock(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return (Component.translatable("block.adorablearmory.lola"));
    }

    @Override
    public @NotNull ShaderLayerProperties getShaderLayer(ItemStack stack) {
        return SHADER_LAYER;
    }
}
