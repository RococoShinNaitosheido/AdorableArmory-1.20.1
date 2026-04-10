package flu.kitten.adorablearmory.api.client.model;

import com.mojang.blaze3d.vertex.VertexFormat;
import flu.kitten.adorablearmory.util.VertexUtils;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;

public interface IVertexConsumer {
    VertexFormat getVertexFormat();
    void setQuadTint(int tint);
    void setQuadOrientation(Direction orientation);
    void setApplyDiffuseLighting(boolean diffuse);
    void setTexture(TextureAtlasSprite texture);
    void put(int element, float... data);
    void put(Quad quad);

    default void put(BakedQuad quad) {
        VertexUtils.putQuad(this, quad);
    }
}
