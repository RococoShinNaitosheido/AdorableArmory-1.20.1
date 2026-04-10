package flu.kitten.adorablearmory.register;

import flu.kitten.adorablearmory.client.CosmicRenderProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

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

    public static CosmicRenderProperties getPropertiesForItem(Item item) {
        CosmicRenderProperties properties = RENDER_ITEMS.get(item);
        if (properties != null) return properties;
        if (item instanceof BlockItem blockItem) {
            return RENDER_BLOCKS.get(blockItem.getBlock());
        }
        return null;
    }

    public static CosmicRenderProperties getPropertiesForBlock(Block block) {
        return RENDER_BLOCKS.get(block);
    }
}
