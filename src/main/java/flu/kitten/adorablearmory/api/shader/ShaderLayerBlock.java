package flu.kitten.adorablearmory.api.shader;

import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Optional block-side hook for shader-layer block rendering; BlockItem implementations can use ShaderLayerItem instead.
 */
public interface ShaderLayerBlock {
    @Nullable
    ShaderLayerProperties getShaderLayer(BlockState state);
}
