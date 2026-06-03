package flu.kitten.adorablearmory.client.render.dimensional;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import flu.kitten.adorablearmory.client.render.dimensional.config.DimensionalSlashTuning;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

public final class DimensionalSlashBloomRenderer {
    private static final int FULLSCREEN_BUFFER_SIZE = 256;
    private static final ReusableBufferBuilder FULLSCREEN_BUFFER = new ReusableBufferBuilder(FULLSCREEN_BUFFER_SIZE);

    private static ShaderInstance blurShader;
    private static Uniform blurTexelSize;
    private static Uniform blurDirection;
    private static Uniform blurRadius;
    private static Uniform blurIntensity;

    private static ShaderInstance compositeShader;
    private static Uniform compositeIntensity;
    private static Uniform compositeAlpha;

    private static TextureTarget sourceTarget;
    private static TextureTarget pingTarget;
    private static boolean frameActive;

    private DimensionalSlashBloomRenderer() {}

    public static void blurShaderLoaded(ShaderInstance shader) {
        blurShader = shader;
        blurTexelSize = shader.getUniform("TexelSize");
        blurDirection = shader.getUniform("Direction");
        blurRadius = shader.getUniform("Radius");
        blurIntensity = shader.getUniform("Intensity");
    }

    public static void compositeShaderLoaded(ShaderInstance shader) {
        compositeShader = shader;
        compositeIntensity = shader.getUniform("Intensity");
        compositeAlpha = shader.getUniform("Alpha");
    }

    static RenderType maskRenderType() {
        return MaskRenderTypes.BLOOM_MASK;
    }

    static boolean canRenderBloomMask() {
        return frameActive && sourceTarget != null && blurShader != null && compositeShader != null;
    }

    static void beginFrame(RenderTarget main) {
        frameActive = false;
        if (!DimensionalSlashTuning.WorldSlash.BLOOM_ENABLED || blurShader == null || compositeShader == null) return;

        ensureTargets(main.width, main.height);
        if (sourceTarget == null || pingTarget == null) return;

        sourceTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        sourceTarget.clear(Minecraft.ON_OSX);
        sourceTarget.copyDepthFrom(main);
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, main.width, main.height);
        frameActive = true;
    }

    static void finishAndComposite(RenderTarget main) {
        if (!frameActive || sourceTarget == null || pingTarget == null) return;

        int iterations = Math.max(1, DimensionalSlashTuning.WorldSlash.BLOOM_BLUR_ITERATIONS);
        for (int i = 0; i < iterations; i++) {
            blur(sourceTarget, pingTarget, 1.0f, 0.0f);
            blur(pingTarget, sourceTarget, 0.0f, 1.0f);
        }

        composite(main);
        frameActive = false;
    }

    static void clear() {
        frameActive = false;
        disposeTarget(sourceTarget);
        disposeTarget(pingTarget);
        sourceTarget = null;
        pingTarget = null;
    }

    private static void ensureTargets(int width, int height) {
        if (sourceTarget == null) {
            sourceTarget = new TextureTarget(width, height, true, Minecraft.ON_OSX);
        } else if (sourceTarget.width != width || sourceTarget.height != height) {
            sourceTarget.resize(width, height, Minecraft.ON_OSX);
        }

        if (pingTarget == null) {
            pingTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
        } else if (pingTarget.width != width || pingTarget.height != height) {
            pingTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void disposeTarget(TextureTarget target) {
        if (target != null) {
            target.destroyBuffers();
        }
    }

    private static void blur(RenderTarget src, RenderTarget dst, float x, float y) {
        dst.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        dst.clear(Minecraft.ON_OSX);
        dst.bindWrite(false);
        RenderSystem.viewport(0, 0, dst.width, dst.height);
        RenderSystem.disableBlend();
        drawFullscreen(dst.width, dst.height, blurShader, src.getColorTextureId(), () -> {
            if (blurTexelSize != null) blurTexelSize.set(1.0f / dst.width, 1.0f / dst.height);
            if (blurDirection != null) blurDirection.set(x, y);
            if (blurRadius != null) blurRadius.set(DimensionalSlashTuning.WorldSlash.BLOOM_BLUR_RADIUS * DimensionalSlashTuning.Quick.WORLD_BLOOM_RADIUS_SCALE);
            if (blurIntensity != null) blurIntensity.set(1.0f);
        });
    }

    private static void composite(RenderTarget main) {
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, main.width, main.height);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        drawFullscreen(main.width, main.height, compositeShader, sourceTarget.getColorTextureId(), () -> {
            if (compositeIntensity != null) compositeIntensity.set(DimensionalSlashTuning.WorldSlash.BLOOM_INTENSITY * DimensionalSlashTuning.Quick.WORLD_BLOOM_INTENSITY_SCALE);
            if (compositeAlpha != null) compositeAlpha.set(DimensionalSlashTuning.WorldSlash.BLOOM_COMPOSITE_ALPHA);
        });
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableBlend();
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, main.width, main.height);
    }

    private static void drawFullscreen(int width, int height, ShaderInstance shader, int texture, Runnable uniforms) {
        if (shader == null) return;

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.backupProjectionMatrix();

        Matrix4f projection = new Matrix4f().setOrtho(0.0f, (float) width, (float) height, 0.0f, 1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0.0f, 0.0f, -2000.0f);
        RenderSystem.applyModelViewMatrix();

        try {
            RenderSystem.setShader(() -> shader);
            shader.setSampler("DiffuseSampler", texture);
            uniforms.run();

            BufferBuilder builder = FULLSCREEN_BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.vertex(0.0f, (float) height, 0.0f).uv(0.0f, 0.0f).endVertex();
            builder.vertex((float) width, (float) height, 0.0f).uv(1.0f, 0.0f).endVertex();
            builder.vertex((float) width, 0.0f, 0.0f).uv(1.0f, 1.0f).endVertex();
            builder.vertex(0.0f, 0.0f, 0.0f).uv(0.0f, 1.0f).endVertex();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static final class MaskRenderTypes extends RenderStateShard {
        private static final RenderStateShard.OutputStateShard BLOOM_MASK_TARGET = new RenderStateShard.OutputStateShard("dimensional_slash_bloom_mask_target", () -> {
            if (sourceTarget != null) {
                sourceTarget.bindWrite(false);
            }
        }, () -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false));

        private static final RenderType BLOOM_MASK = RenderType.create("dimensional_slash_bloom_mask", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 65536, false, true, RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionColorShader))
                .setTransparencyState(ADDITIVE_TRANSPARENCY)
                .setCullState(NO_CULL)
                .setDepthTestState(LEQUAL_DEPTH_TEST)
                .setOutputState(BLOOM_MASK_TARGET)
                .setWriteMaskState(COLOR_WRITE)
                .createCompositeState(false)
        );

        private MaskRenderTypes(String name, Runnable setup, Runnable clear) {
            super(name, setup, clear);
        }
    }
}
