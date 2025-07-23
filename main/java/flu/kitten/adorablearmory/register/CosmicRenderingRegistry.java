package flu.kitten.adorablearmory.register;

import flu.kitten.adorablearmory.client.CosmicRenderProperties;
import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public class CosmicRenderingRegistry {

    private static final Map<Item, CosmicRenderProperties> RENDER_ITEMS = new ConcurrentHashMap<>();

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

    public static CosmicRenderProperties getPropertiesForItem(Item item) {
        return RENDER_ITEMS.get(item);
    }
}
