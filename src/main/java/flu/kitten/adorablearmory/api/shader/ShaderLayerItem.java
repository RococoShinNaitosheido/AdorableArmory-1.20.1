package flu.kitten.adorablearmory.api.shader;

import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Implement this on any Item to opt into Adorable Armory shader-layer item rendering without central registration.
 */
public interface ShaderLayerItem {
    @Nullable
    ShaderLayerProperties getShaderLayer(ItemStack stack);
}
