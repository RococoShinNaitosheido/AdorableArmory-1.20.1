package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class ShaderBlockRenderHelper {
    private static final float WORLD_BLOCK_COSMIC_SCALE = 0.35F;

    public static void renderCosmicBlock(BlockState blockState, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, ItemStack stack) {
        renderCosmicBlock(blockState, poseStack, buffers, packedLight, packedOverlay, stack, AdorableArmoryShaders.COSMIC_BLOCK_RENDER_TYPE);
    }

    public static void renderCosmicBlock(BlockState blockState, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, ItemStack stack, RenderType renderType) {
        Minecraft mc = Minecraft.getInstance();
        assert mc.level != null;
        if (!isCosmicShaderReady()) {
            return;
        }

        float yaw = 0.0F;
        float pitch = 0.0F;
        float scale = AdorableArmoryShaders.inventoryRender ? 100 : WORLD_BLOCK_COSMIC_SCALE;
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
            var sprite = mc.getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(AdorableArmory.path("item/cosmic_" + i));
            AdorableArmoryShaders.COSMIC_UVS[i * 4] = sprite.getU0();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 1] = sprite.getV0();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 2] = sprite.getU1();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 3] = sprite.getV1();
        }
        AdorableArmoryShaders.cosmicUVs.setMatrix2x2Array(AdorableArmoryShaders.COSMIC_UVS, 10);
        VertexConsumer consumer = buffers.getBuffer(renderType);
        BakedModel model = mc.getBlockRenderer().getBlockModel(blockState);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            quads.addAll(model.getQuads(blockState, direction, mc.level.random));
        }
        mc.getItemRenderer().renderQuadList(poseStack, consumer, quads, stack, packedLight, packedOverlay);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(renderType);
        }
    }

    public static void renderSkyStarryBlock(BlockState blockState, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay, ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        assert mc.level != null;
        if (AdorableArmoryShaders.starrySkyShader == null || AdorableArmoryShaders.portalTime == null || AdorableArmoryShaders.starScale == null || AdorableArmoryShaders.opacity == null) {
            return;
        }

        AdorableArmoryShaders.portalTime.set((System.currentTimeMillis() - AdorableArmoryShaders.renderTime) / 2000.0F);
        AdorableArmoryShaders.starScale.set(1.32f);
        AdorableArmoryShaders.opacity.set(0.80F);
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.SKY_ENTITY);
        BakedModel model = mc.getBlockRenderer().getBlockModel(blockState);
        List<BakedQuad> quads = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            quads.addAll(model.getQuads(blockState, direction, mc.level.random));
        }
        mc.getItemRenderer().renderQuadList(poseStack, consumer, quads, stack, packedLight, packedOverlay);
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(AdorableArmoryShaders.SKY_ENTITY);
        }
    }

    private static boolean isCosmicShaderReady() {
        return AdorableArmoryShaders.cosmicShader != null && AdorableArmoryShaders.cosmicTime != null && AdorableArmoryShaders.cosmicYaw != null && AdorableArmoryShaders.cosmicPitch != null && AdorableArmoryShaders.cosmicExternalScale != null && AdorableArmoryShaders.cosmicOpacity != null && AdorableArmoryShaders.cosmicUVs != null;
    }

    private ShaderBlockRenderHelper() {}
}
