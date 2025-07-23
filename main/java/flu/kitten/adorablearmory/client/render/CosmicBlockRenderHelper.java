package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class CosmicBlockRenderHelper {

    private CosmicBlockRenderHelper() {}

    public static void renderBlockQuads(BlockState blockState, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        assert mc.level != null;
        float yaw = 0.0F;
        float pitch = 0.0F;
        float scale = AdorableArmoryShaders.inventoryRender ? 100 : 1;
        if (!AdorableArmoryShaders.inventoryRender && mc.player != null) {
            yaw = (float) (mc.player.getYRot() * 2.0F * Math.PI / 360.0);
            pitch = -(float) (mc.player.getXRot() * 2.0F * Math.PI / 360.0);
        }
        AdorableArmoryShaders.cosmicTime.set((System.currentTimeMillis() - AdorableArmoryShaders.renderTime) / 2000.0F);
        AdorableArmoryShaders.cosmicYaw.set(yaw);
        AdorableArmoryShaders.cosmicPitch.set(pitch);
        AdorableArmoryShaders.cosmicExternalScale.set(scale);
        AdorableArmoryShaders.cosmicOpacity.set(0.60F);
        for (int i = 0; i < 10; ++i) {
            var sprite = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(
                    AdorableArmory.path("item/cosmic_" + i)
            );
            AdorableArmoryShaders.COSMIC_UVS[i * 4] = sprite.getU0();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 1] = sprite.getV0();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 2] = sprite.getU1();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 3] = sprite.getV1();
        }
        AdorableArmoryShaders.cosmicUVs.setMatrix2x2Array(AdorableArmoryShaders.COSMIC_UVS, 10);
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.COSMIC_BLOCK_RENDER_TYPE);
        BakedModel model = mc.getBlockRenderer().getBlockModel(blockState);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            quads.addAll(model.getQuads(blockState, direction, mc.level.random));
        }
        mc.getItemRenderer().renderQuadList(poseStack, consumer, quads, stack, packedLight, packedOverlay);
    }
}
