package flu.kitten.adorablearmory.api.shader;

import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * maskTextures are block-atlas texture locations used to decide where the shader layer is visible.
 */
public record ShaderLayerProperties(ShaderLayerType layerType, ShaderLayerModelTransform modelTransform, List<ResourceLocation> maskTextures) {
    public ShaderLayerProperties {
        Objects.requireNonNull(layerType, "layerType");
        Objects.requireNonNull(modelTransform, "modelTransform");
        Objects.requireNonNull(maskTextures, "maskTextures");
        maskTextures = List.copyOf(maskTextures);
        if (maskTextures.isEmpty()) {
            throw new IllegalArgumentException("Shader layer maskTextures cannot be empty.");
        }
    }

    public static ShaderLayerProperties cosmic(ShaderLayerModelTransform modelTransform, ResourceLocation... maskTextures) {
        return new ShaderLayerProperties(ShaderLayerType.COSMIC, modelTransform, Arrays.asList(maskTextures));
    }

    public static ShaderLayerProperties skyItem(ShaderLayerModelTransform modelTransform, ResourceLocation... maskTextures) {
        return new ShaderLayerProperties(ShaderLayerType.SKY_ITEM, modelTransform, Arrays.asList(maskTextures));
    }
}
