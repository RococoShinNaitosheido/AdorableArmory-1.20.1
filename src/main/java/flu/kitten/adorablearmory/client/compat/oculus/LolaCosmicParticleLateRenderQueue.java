package flu.kitten.adorablearmory.client.compat.oculus;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class LolaCosmicParticleLateRenderQueue {
    private static final List<Quad> QUADS = new ArrayList<>();

    public static boolean isReady() {
        return AdorableArmoryShaders.cosmicParticleShader != null;
    }

    public static void enqueue(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float u0, float u1, float v0, float v1, float r, float g, float b, float a, int light) {
        QUADS.add(new Quad(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, u0, u1, v0, v1, r, g, b, a, light, new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix())));
    }

    public static void renderAfterLevel(PoseStack poseStack) {
        if (QUADS.isEmpty()) {
            return;
        }

        ShaderInstance shader = AdorableArmoryShaders.cosmicParticleShader;
        if (Minecraft.getInstance().level == null || shader == null || !AdorableArmoryShaders.uploadParticleUnity()) {
            QUADS.clear();
            return;
        }

        Quad first = QUADS.get(0);
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        modelViewStack.last().pose().set(first.modelView());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(first.projection()), VertexSorting.DISTANCE_TO_ORIGIN);

        try {
            LateShaderLayerState.prepareMainTargetPass();
            RenderSystem.enableDepthTest();
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthMask(false);
            Minecraft.getInstance().gameRenderer.lightTexture().turnOnLightLayer();
            RenderSystem.setShader(() -> shader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_BLOCKS);

            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
            for (Quad quad : QUADS) {
                quad.write(builder);
            }
            BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            Minecraft.getInstance().gameRenderer.lightTexture().turnOffLightLayer();
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            LateShaderLayerState.finishMainTargetPass();
            QUADS.clear();
        }
    }

    private record Quad(float x0, float y0, float z0, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float u0, float u1, float v0, float v1, float r, float g, float b, float a, int light, Matrix4f modelView, Matrix4f projection) {
        void write(BufferBuilder builder) {
            builder.vertex(x0, y0, z0).uv(u1, v1).color(r, g, b, a).uv2(light).endVertex();
            builder.vertex(x1, y1, z1).uv(u1, v0).color(r, g, b, a).uv2(light).endVertex();
            builder.vertex(x2, y2, z2).uv(u0, v0).color(r, g, b, a).uv2(light).endVertex();
            builder.vertex(x3, y3, z3).uv(u0, v1).color(r, g, b, a).uv2(light).endVertex();
        }
    }

    private LolaCosmicParticleLateRenderQueue() {}
}
