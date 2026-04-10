package flu.kitten.adorablearmory.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

public final class BlackHoleLensClient {
    public static final class LensStyle {
        // 透镜
        public float strengthScale = 0.50f; // 透镜强度比例
        public float softeningScale = 0.8f; // 中心柔化比例
        public float softeningMinPx = 8.0f; // 最小柔化像素值-保证黑洞在屏幕上多小-中心至少8像素平滑区
        // 爱因斯坦环
        public float ringRadiusScale = 0.50f; // 环半径比例
        public float ringWidthScale = 0.05f; // 环宽度比例
        public float ringWidthMinPx = 2; // 最小环宽度
        public float ringStrengthScale = 0; // 环扭曲强度-径向扭曲力
        public float ringTwistScale = 0; // 环旋涡扭曲
        public float ringTwistMinPx = 0;
        // 色散
        public float dispersionScale = 0.035f; // 色散强度比例-RGB
        public float dispersionMinPx = 2; // 最小色散像素
        public float dispersionMaxPx = 10; // 最大色散像素

        public LensInstance bake(float centerXpx, float centerYpx, float radiusPx, float baseStrengthPx, float lensDepth01) {
            float soft = Math.max(softeningMinPx, radiusPx * softeningScale);
            float ringR = radiusPx * ringRadiusScale;
            float ringW = Math.max(ringWidthMinPx, radiusPx * ringWidthScale);
            float ringStrength = baseStrengthPx * ringStrengthScale;
            float ringTwist = Math.max(ringTwistMinPx, radiusPx * ringTwistScale);
            float dispersion = Math.min(dispersionMaxPx, Math.max(dispersionMinPx, radiusPx * dispersionScale));
            return new LensInstance(centerXpx, centerYpx, radiusPx, baseStrengthPx, soft, ringR, ringW, ringStrength, ringTwist, dispersion, lensDepth01);
        }
    }

    private static final ThreadLocal<Boolean> LATE_PASS = ThreadLocal.withInitial(() -> false);
    public record LensInstance(float centerXpx, float centerYpx, float radiusPx, float strengthPx, float softeningPx, float ringRadiusPx, float ringWidthPx, float ringStrengthPx, float ringTwistPx, float dispersionPx, float lensDepth01) {}
    public static LensStyle DEFAULT_STYLE = new LensStyle();
    public static ShaderInstance BLACK_HOLE_LENS_SHADER;
    private static Uniform U_SCREEN_SIZE;
    private static Uniform U_CENTER_PX;
    private static Uniform U_RADIUS_PX;
    private static Uniform U_STRENGTH_PX;
    private static Uniform U_SOFTENING_PX;
    private static Uniform U_RING_RADIUS_PX;
    private static Uniform U_RING_WIDTH_PX;
    private static Uniform U_RING_STRENGTH_PX;
    private static Uniform U_RING_TWIST_PX;
    private static Uniform U_DISPERSION_PX;
    private static Uniform U_LENS_DEPTH_01;
    private static TextureTarget pingTarget;
    private static TextureTarget pongTarget;
    private static final List<LensInstance> queued = new ArrayList<>();

    public static boolean isLatePass() {
        return LATE_PASS.get();
    }

    static void setLatePass(boolean v) {
        LATE_PASS.set(v);
    }

    public static void shaderLoaded(ShaderInstance shader) {
        BLACK_HOLE_LENS_SHADER = shader;
        U_SCREEN_SIZE = shader.getUniform("ScreenSize");
        U_CENTER_PX = shader.getUniform("CenterPx");
        U_RADIUS_PX = shader.getUniform("RadiusPx");
        U_STRENGTH_PX = shader.getUniform("StrengthPx");

        U_SOFTENING_PX = shader.getUniform("SofteningPx");
        U_RING_RADIUS_PX = shader.getUniform("RingRadiusPx");
        U_RING_WIDTH_PX = shader.getUniform("RingWidthPx");
        U_RING_STRENGTH_PX = shader.getUniform("RingStrengthPx");
        U_RING_TWIST_PX = shader.getUniform("RingTwistPx");
        U_DISPERSION_PX = shader.getUniform("DispersionPx");

        U_LENS_DEPTH_01 = shader.getUniform("LensDepth01");
    }

    @SuppressWarnings("unused")
    public static void queueLensAuto(float centerXpx, float centerYpx, float radiusPx, float lensDepth01) {
        float strengthPx = radiusPx * DEFAULT_STYLE.strengthScale;
        queued.add(DEFAULT_STYLE.bake(centerXpx, centerYpx, radiusPx, strengthPx, lensDepth01));
    }

    public static void queueLens(LensInstance inst) {
        queued.add(inst);
    }

    public static void renderQueuedLensAfterOpaque() {
        if (queued.isEmpty()) return;
        if (BLACK_HOLE_LENS_SHADER == null) {
            queued.clear();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        RenderTarget main = mc.getMainRenderTarget();
        int w = main.width, h = main.height;

        ensurePingPongTargets(w, h);
        blitColorDepth(main, pingTarget);
        blitDepthOnly(main, pongTarget);

        queued.sort((a, b) -> Float.compare(b.lensDepth01(), a.lensDepth01()));

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();

        RenderSystem.backupProjectionMatrix();
        Matrix4f matrix4f = new Matrix4f().setOrtho(0.0f, (float) w, (float) h, 0.0f, 1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix(matrix4f, VertexSorting.ORTHOGRAPHIC_Z);

        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        mv.translate(0.0f, 0.0f, -2000.0f);
        RenderSystem.applyModelViewMatrix();

        try {
            RenderSystem.setShader(() -> BLACK_HOLE_LENS_SHADER);
            if (U_SCREEN_SIZE != null) U_SCREEN_SIZE.set((float) w, (float) h);

            for (LensInstance inst : queued) {
                float r = inst.radiusPx();

                int pad = (int) Math.ceil(0.95f * r + inst.dispersionPx() + 4.0f);

                int x0 = (int) Math.floor(clamp(inst.centerXpx() - r - pad, 0, w));
                int y0 = (int) Math.floor(clamp(inst.centerYpx() - r - pad, 0, h));
                int x1 = (int) Math.ceil (clamp(inst.centerXpx() + r + pad, 0, w));
                int y1 = (int) Math.ceil (clamp(inst.centerYpx() + r + pad, 0, h));

                if (x1 <= x0 || y1 <= y0) continue;

                blitColorOnlyRect(pingTarget, pongTarget, x0, y0, x1, y1);

                pongTarget.bindWrite(true);
                RenderSystem.viewport(0, 0, w, h);

                BLACK_HOLE_LENS_SHADER.setSampler("SceneTex", pingTarget.getColorTextureId());

                if (U_CENTER_PX != null) U_CENTER_PX.set(inst.centerXpx(), inst.centerYpx());
                if (U_RADIUS_PX != null) U_RADIUS_PX.set(inst.radiusPx());
                if (U_STRENGTH_PX != null) U_STRENGTH_PX.set(inst.strengthPx());

                if (U_SOFTENING_PX != null) U_SOFTENING_PX.set(inst.softeningPx());
                if (U_RING_RADIUS_PX != null) U_RING_RADIUS_PX.set(inst.ringRadiusPx());
                if (U_RING_WIDTH_PX != null) U_RING_WIDTH_PX.set(inst.ringWidthPx());
                if (U_RING_STRENGTH_PX != null) U_RING_STRENGTH_PX.set(inst.ringStrengthPx());
                if (U_RING_TWIST_PX != null) U_RING_TWIST_PX.set(inst.ringTwistPx());
                if (U_DISPERSION_PX != null) U_DISPERSION_PX.set(inst.dispersionPx());
                if (U_LENS_DEPTH_01 != null) U_LENS_DEPTH_01.set(inst.lensDepth01());

                drawScreenQuadForLens(w, h, inst);
                blitColorOnlyRect(pongTarget, pingTarget, x0, y0, x1, y1);
            }

            blitColorOnly(pingTarget, main);
            main.bindWrite(true);
            RenderSystem.viewport(0, 0, w, h);

        } finally {
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            queued.clear();
        }
    }

    private static void drawScreenQuadForLens(int w, int h, LensInstance inst) {
        float quadPad = 1.32f;
        float quadR = inst.radiusPx() + quadPad;

        float x0 = inst.centerXpx - quadR;
        float y0 = inst.centerYpx - quadR;
        float x1 = inst.centerXpx + quadR;
        float y1 = inst.centerYpx + quadR;

        float cx0 = clamp(x0, 0, w);
        float cy0 = clamp(y0, 0, h);
        float cx1 = clamp(x1, 0, w);
        float cy1 = clamp(y1, 0, h);

        float u0 = cx0 / (float) w;
        float v0 = cy0 / (float) h;
        float u1 = cx1 / (float) w;
        float v1 = cy1 / (float) h;

        Tesselator instance = Tesselator.getInstance();
        BufferBuilder builder = instance.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        builder.vertex(cx0, cy1, 0.0f).uv(u0, v1).endVertex();
        builder.vertex(cx1, cy1, 0.0f).uv(u1, v1).endVertex();
        builder.vertex(cx1, cy0, 0.0f).uv(u1, v0).endVertex();
        builder.vertex(cx0, cy0, 0.0f).uv(u0, v0).endVertex();

        BufferUploader.drawWithShader(builder.end());
    }

    private static void ensurePingPongTargets(int w, int h) {
        if (pingTarget == null || pongTarget == null) {
            pingTarget = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            pingTarget.setClearColor(0, 0, 0, 0);

            pongTarget = new TextureTarget(w, h, true, Minecraft.ON_OSX);
            pongTarget.setClearColor(0, 0, 0, 0);
        } else if (pingTarget.width != w || pingTarget.height != h) {
            pingTarget.resize(w, h, Minecraft.ON_OSX);
            pongTarget.resize(w, h, Minecraft.ON_OSX);
        }
    }

    private static void blitColorDepth(RenderTarget src, RenderTarget dst) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, src.width, src.height, 0, 0, dst.width, dst.height, GL30.GL_COLOR_BUFFER_BIT | GL30.GL_DEPTH_BUFFER_BIT, GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static void blitColorOnly(RenderTarget src, RenderTarget dst) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, src.width, src.height, 0, 0, dst.width, dst.height, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static void blitDepthOnly(RenderTarget src, RenderTarget dst) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, src.width, src.height, 0, 0, dst.width, dst.height, GL30.GL_DEPTH_BUFFER_BIT, GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static void blitColorOnlyRect(RenderTarget src, RenderTarget dst, int x0, int y0Top, int x1, int y1Top) {
        int h = src.height;
        int srcY0 = h - y1Top, srcY1 = h - y0Top;
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GL30.glBlitFramebuffer(x0, srcY0, x1, srcY1, x0, srcY0, x1, srcY1, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static float clamp(float v, float mn, float mx) {
        return Math.max(mn, Math.min(mx, v));
    }

    private BlackHoleLensClient() {}
}
