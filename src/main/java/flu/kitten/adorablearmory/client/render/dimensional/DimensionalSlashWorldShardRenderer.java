package flu.kitten.adorablearmory.client.render.dimensional;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import flu.kitten.adorablearmory.client.render.dimensional.config.DimensionalSlashTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL30;

import java.util.List;

public final class DimensionalSlashWorldShardRenderer {
    private static final int SHARD_BUFFER_SIZE = Math.max(32768, DimensionalSlashTuning.WorldSlash.WORLD_SHARD_COUNT * 96 * 48);
    private static final ReusableBufferBuilder SHARD_BUFFER = new ReusableBufferBuilder(SHARD_BUFFER_SIZE);

    private static ShaderInstance shader;
    private static Uniform screenSizeUniform;
    private static Uniform texelSizeUniform;
    private static Uniform refractionUniform;
    private static Uniform edgeUniform;
    private static Uniform mirrorUniform;
    private static Uniform timeUniform;
    private static TextureTarget sceneTarget;

    private DimensionalSlashWorldShardRenderer() {}

    public static void shaderLoaded(ShaderInstance instance) {
        shader = instance;
        screenSizeUniform = instance.getUniform("ScreenSize");
        texelSizeUniform = instance.getUniform("TexelSize");
        refractionUniform = instance.getUniform("RefractionStrength");
        edgeUniform = instance.getUniform("EdgeStrength");
        mirrorUniform = instance.getUniform("MirrorStrength");
        timeUniform = instance.getUniform("Time");
    }

    public static void render(PoseStack poseStack, List<DimensionalSlashEffect> effects, Vec3 camera, float partialTick, RenderTarget main) {
        if (shader == null || !DimensionalSlashTuning.WorldSlash.WORLD_SHARDS_ENABLED || effects.stream().noneMatch(DimensionalSlashEffect::hasWorldShards)) return;

        ensureSceneTarget(main.width, main.height);
        if (sceneTarget == null) return;

        blitColorOnly(main, sceneTarget);
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, main.width, main.height);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        RenderSystem.setShader(() -> shader);
        shader.setSampler("SceneTex", sceneTarget.getColorTextureId());
        if (screenSizeUniform != null) screenSizeUniform.set((float) main.width, (float) main.height);
        if (texelSizeUniform != null) texelSizeUniform.set(1.0f / Math.max(1.0f, main.width), 1.0f / Math.max(1.0f, main.height));
        if (refractionUniform != null) refractionUniform.set(DimensionalSlashTuning.WorldSlash.WORLD_SHARD_REFRACTION);
        if (edgeUniform != null) edgeUniform.set(DimensionalSlashTuning.WorldSlash.WORLD_SHARD_EDGE_HIGHLIGHT);
        if (mirrorUniform != null) mirrorUniform.set(DimensionalSlashTuning.WorldSlash.WORLD_SHARD_MIRROR_STRENGTH);
        if (timeUniform != null) timeUniform.set((Minecraft.getInstance().level == null ? 0.0f : Minecraft.getInstance().level.getGameTime()) + partialTick);

        BufferBuilder builder = SHARD_BUFFER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.NEW_ENTITY);

        for (DimensionalSlashEffect effect : effects) {
            effect.renderWorldShards(poseStack, builder, camera, partialTick);
        }
        ReusableBufferBuilder.drawWithShaderOrDiscard(builder);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, main.width, main.height);
    }

    public static void clear() {
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
    }

    private static void ensureSceneTarget(int width, int height) {
        if (sceneTarget == null) {
            sceneTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            sceneTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        } else if (sceneTarget.width != width || sceneTarget.height != height) {
            sceneTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void blitColorOnly(RenderTarget src, RenderTarget dst) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, src.width, src.height, 0, 0, dst.width, dst.height, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }
}
