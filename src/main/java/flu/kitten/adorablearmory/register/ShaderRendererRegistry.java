package flu.kitten.adorablearmory.register;

import flu.kitten.adorablearmory.api.shader.ShaderLayerBlock;
import flu.kitten.adorablearmory.api.shader.ShaderLayerItem;
import flu.kitten.adorablearmory.api.shader.ShaderLayerModelTransform;
import flu.kitten.adorablearmory.api.shader.ShaderLayerProperties;
import flu.kitten.adorablearmory.api.shader.ShaderLayerType;
import flu.kitten.adorablearmory.client.CosmicRenderProperties;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.util.TransformUtils;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class ShaderRendererRegistry {

    private static final Map<Item, CosmicRenderProperties> RENDER_ITEMS = new ConcurrentHashMap<>();
    private static final Map<Block, CosmicRenderProperties> RENDER_BLOCKS = new ConcurrentHashMap<>();

    // Item registration
    public static void registerRenderItem(Item item, CosmicRenderProperties properties) {
        RENDER_ITEMS.put(item, properties);
    }

    public static void registerAll(Collection<Item> items, CosmicRenderProperties properties) {
        for (Item item : items) {
            RENDER_ITEMS.put(item, properties);
        }
    }

    public static void registerAll(Map<Item, CosmicRenderProperties> itemsWithStates) {
        RENDER_ITEMS.putAll(itemsWithStates);
    }

    // Block registration
    public static void registerRenderBlock(Block block, CosmicRenderProperties properties) {
        RENDER_BLOCKS.put(block, properties);
    }

    public static void registerAllBlocks(Collection<Block> blocks, CosmicRenderProperties properties) {
        for (Block block : blocks) {
            RENDER_BLOCKS.put(block, properties);
        }
    }

    public static void registerAllBlocks(Map<Block, CosmicRenderProperties> blocksWithStates) {
        RENDER_BLOCKS.putAll(blocksWithStates);
    }

    public static CosmicRenderProperties getPropertiesForStack(ItemStack stack) {
        if (!stack.isEmpty() && stack.getItem() instanceof ShaderLayerItem shaderLayerItem) {
            CosmicRenderProperties properties = resolve(shaderLayerItem.getShaderLayer(stack));
            if (properties != null) {
                return properties;
            }
        }

        return getPropertiesForItem(stack.getItem());
    }

    public static CosmicRenderProperties getPropertiesForItem(Item item) {
        if (item instanceof ShaderLayerItem shaderLayerItem) {
            CosmicRenderProperties properties = resolve(shaderLayerItem.getShaderLayer(new ItemStack(item)));
            if (properties != null) {
                return properties;
            }
        }

        CosmicRenderProperties properties = RENDER_ITEMS.get(item);
        if (properties != null) return properties;
        if (item instanceof BlockItem blockItem) {
            properties = getPropertiesForBlock(blockItem.getBlock());
            if (properties != null) {
                return properties;
            }
        }
        return null;
    }

    public static CosmicRenderProperties getPropertiesForBlock(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ShaderLayerBlock shaderLayerBlock) {
            CosmicRenderProperties properties = resolve(shaderLayerBlock.getShaderLayer(state));
            if (properties != null) {
                return properties;
            }
        }

        return getPropertiesForBlock(block);
    }

    public static CosmicRenderProperties getPropertiesForBlock(Block block) {
        CosmicRenderProperties properties = RENDER_BLOCKS.get(block);
        if (properties != null) {
            return properties;
        }

        Item item = block.asItem();
        if (item instanceof ShaderLayerItem shaderLayerItem) {
            return resolve(shaderLayerItem.getShaderLayer(new ItemStack(item)));
        }

        return null;
    }

    public static boolean hasCosmicLayer(BlockState state) {
        CosmicRenderProperties properties = getPropertiesForBlock(state);
        return properties != null && isCosmicRenderType(properties.renderType());
    }

    private static CosmicRenderProperties resolve(ShaderLayerProperties properties) {
        if (properties == null) {
            return null;
        }

        return new CosmicRenderProperties(resolveModelState(properties.modelTransform()), resolveRenderType(properties.layerType()));
    }

    private static ModelState resolveModelState(ShaderLayerModelTransform transform) {
        return switch (transform) {
            case DEFAULT_TOOL -> TransformUtils.DEFAULT_TOOL;
            case DEFAULT_BLOCK_ITEM -> TransformUtils.DEFAULT_BLOCK_ITEM;
            case DEFAULT_ITEM -> TransformUtils.DEFAULT_ITEM;
        };
    }

    private static RenderType resolveRenderType(ShaderLayerType layerType) {
        return switch (layerType) {
            case COSMIC -> AdorableArmoryShaders.COSMIC_RENDER_TYPE;
            case SKY_ITEM -> AdorableArmoryShaders.SKY_ITEM;
        };
    }

    private static boolean isCosmicRenderType(RenderType renderType) {
        return renderType == AdorableArmoryShaders.COSMIC_RENDER_TYPE || renderType == AdorableArmoryShaders.COSMIC_BLOCK_RENDER_TYPE || renderType == AdorableArmoryShaders.COSMIC_ITEM_AFTER_LEVEL_RENDER_TYPE || renderType == AdorableArmoryShaders.COSMIC_HAND_AFTER_LEVEL_RENDER_TYPE || renderType == AdorableArmoryShaders.COSMIC_BLOCK_AFTER_LEVEL_RENDER_TYPE;
    }
}
