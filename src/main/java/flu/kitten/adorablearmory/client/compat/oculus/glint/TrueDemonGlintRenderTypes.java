package flu.kitten.adorablearmory.client.compat.oculus.glint;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.function.Supplier;

public final class TrueDemonGlintRenderTypes {

    private static RenderType ITEM_GLINT_DIRECT;

    public static RenderType itemGlintDirect() {
        if (ITEM_GLINT_DIRECT == null) {
            ITEM_GLINT_DIRECT = createGlintType("true_demon_glint_direct", GameRenderer::getRendertypeGlintDirectShader, ItemRenderer.ENCHANTED_GLINT_ITEM);
        }
        return ITEM_GLINT_DIRECT;
    }

    private static RenderType createGlintType(String name, Supplier<?> shaderSupplier, ResourceLocation glintTexture) {
        RenderStateShard.ShaderStateShard shader = new RenderStateShard.ShaderStateShard((Supplier) shaderSupplier);
        RenderStateShard.TextureStateShard texture = new RenderStateShard.TextureStateShard(glintTexture, true, false);
        RenderStateShard.TransparencyStateShard glintTransparency = new RenderStateShard.TransparencyStateShard("glint_transparency", () -> {
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE);
        }, () -> {
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
        });

        RenderStateShard.DepthTestStateShard depthTest = new RenderStateShard.DepthTestStateShard("equal", GL11.GL_EQUAL);
        RenderStateShard.WriteMaskStateShard writeMask = new RenderStateShard.WriteMaskStateShard(true, false);
        RenderStateShard.CullStateShard cull = new RenderStateShard.CullStateShard(false);
        RenderStateShard.OverlayStateShard overlay = new RenderStateShard.OverlayStateShard(false);
        RenderStateShard.LightmapStateShard lightmap = new RenderStateShard.LightmapStateShard(false);
        RenderStateShard.TexturingStateShard glintTexturing = new RenderStateShard.TexturingStateShard("glint_texturing", () -> setupGlintTexturing(8.0F), RenderSystem::resetTextureMatrix);

        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(shader)
                .setTextureState(texture)
                .setTransparencyState(glintTransparency)
                .setDepthTestState(depthTest)
                .setWriteMaskState(writeMask)
                .setCullState(cull)
                .setOverlayState(overlay)
                .setLightmapState(lightmap)
                .setTexturingState(glintTexturing)
                .createCompositeState(false);
        return RenderType.create(name, DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, state);
    }

    private static void setupGlintTexturing(float scale) {
        long millis = (long) ((double) Util.getMillis() * Minecraft.getInstance().options.glintSpeed().get() * 8.0D);
        float u = (float) (millis % 110000L) / 110000.0F;
        float v = (float) (millis % 30000L) / 30000.0F;
        Matrix4f matrix = new Matrix4f().translation(-u, v, 0.0F);
        matrix.rotateZ(0.17453292F).scale(scale);
        RenderSystem.setTextureMatrix(matrix);
    }

    private TrueDemonGlintRenderTypes() {}
}
