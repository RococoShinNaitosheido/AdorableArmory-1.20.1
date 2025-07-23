package flu.kitten.adorablearmory.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;
import net.minecraft.Util;

import java.util.function.BiFunction;

@OnlyIn(Dist.CLIENT)
public final class PulseRendererHelper {

    private static final RenderStateShard.ShaderStateShard RENDERER_ENTITY_TRANSLUCENT_SHADER = new RenderStateShard.ShaderStateShard(GameRenderer::getRendertypeEntityTranslucentShader);
    private static final RenderStateShard.CullStateShard NO_CULL = new RenderStateShard.CullStateShard(false);
    private static final RenderStateShard.LightmapStateShard LIGHTMAP = new RenderStateShard.LightmapStateShard(true);
    private static final RenderStateShard.OverlayStateShard OVERLAY = new RenderStateShard.OverlayStateShard(true);
    private static final RenderStateShard.DepthTestStateShard LEQUAL_DEPTH_TEST = new RenderStateShard.DepthTestStateShard("always", 519);
    private static final RenderStateShard.LayeringStateShard VIEW_OFFSET_Z_LAYERING = new RenderStateShard.LayeringStateShard("view_offset_z_layering", () -> {
        PoseStack posestack = RenderSystem.getModelViewStack();
        posestack.pushPose();
        posestack.scale(0.99975586F, 0.99975586F, 0.99975586F);
        RenderSystem.applyModelViewMatrix();
    }, () -> {
        PoseStack posestack = RenderSystem.getModelViewStack();
        posestack.popPose();
        RenderSystem.applyModelViewMatrix();
    });
    private static final RenderStateShard.WriteMaskStateShard COLOR_WRITE = new RenderStateShard.WriteMaskStateShard(true, true);
    private static final RenderStateShard.TransparencyStateShard TRANSLUCENT_TRANSPARENCY = new RenderStateShard.TransparencyStateShard("translucent_transparency", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    public static void renderPulseModel(PoseStack poseStack, VertexConsumer consumer, net.minecraft.client.resources.model.BakedModel model, ItemStack stack, int light, int overlay, net.minecraft.client.color.item.ItemColors itemColors, float alpha) {
        RandomSource rand = RandomSource.create();
        PoseStack.Pose pose = poseStack.last();
        boolean hasItem = !stack.isEmpty();
        for (Direction dir : Direction.values()) {
            rand.setSeed(42L);
            List<BakedQuad> quads = model.getQuads(null, dir, rand);
            renderQuadList(pose, consumer, quads, stack, light, overlay, hasItem, itemColors, alpha);
        }
        rand.setSeed(42L);
        List<BakedQuad> generalQuads = model.getQuads(null, null, rand);
        renderQuadList(pose, consumer, generalQuads, stack, light, overlay, hasItem, itemColors, alpha);
    }

    private static void renderQuadList(PoseStack.Pose pose, VertexConsumer consumer, List<BakedQuad> quads, ItemStack stack, int light, int overlay, boolean hasItem, net.minecraft.client.color.item.ItemColors itemColors, float alpha) {
        for (BakedQuad quad : quads) {
            int color = -1;
            if (hasItem && quad.isTinted() && itemColors != null) color = itemColors.getColor(stack, quad.getTintIndex());
            float r = (float)(color >> 16 & 255) / 255.0F;
            float g = (float)(color >> 8 & 255) / 255.0F;
            float b = (float)(color & 255) / 255.0F;
            consumer.putBulkData(pose, quad, r, g, b, alpha, light, overlay, true);
        }
    }

    private static final BiFunction<ResourceLocation, Boolean, RenderType> TRANSLUCENT = Util.memoize((location, affectsOutline) -> {
        RenderType.CompositeState pulse = RenderType.CompositeState.builder()
                .setShaderState(RENDERER_ENTITY_TRANSLUCENT_SHADER)
                .setTextureState(new RenderStateShard.TextureStateShard(location, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .setWriteMaskState(COLOR_WRITE)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                .createCompositeState(affectsOutline);
        return RenderType.create("translucent", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true, pulse);
    });

    public static RenderType pulseTranslucent(ResourceLocation texture) {
        return TRANSLUCENT.apply(texture,true);
    }
}
