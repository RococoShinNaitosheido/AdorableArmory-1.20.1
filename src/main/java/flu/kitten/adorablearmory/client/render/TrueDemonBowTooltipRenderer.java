package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector2ic;

import java.util.List;

public final class TrueDemonBowTooltipRenderer {
    private TrueDemonBowTooltipRenderer() {}

    private static final ResourceLocation TRUE_DEMON_BOW_TEXTURE = new ResourceLocation(AdorableArmory.MODID, "textures/gui/tooltip/true_demon_bow.png");
    private static final ResourceLocation EXTRA_DECORATION = new ResourceLocation(AdorableArmory.MODID, "textures/gui/tooltip/true_demon_bow_extra_decoration.png");
    private static final ResourceLocation EXTRA_DECORATION_2 = new ResourceLocation(AdorableArmory.MODID, "textures/gui/tooltip/true_demon_bow_extra_decoration_2.png");
    private static final int TEX_W = 74;
    private static final int TEX_H = 74;
    private static final int CORNER = 7;
    private static final int EXTRA_AFTER_FIRST_LINE = 2;
    private static final int EXTRA_RIGHT = 3; // Tooltip右侧拉伸像素
    // 额外装饰UV
    private static final int DECOR_W = 32;
    private static final int DECOR_H = 32;
    // 距离右下角内边距
    private static final int DECOR_PAD_R = 4;
    private static final int DECOR_PAD_B = 4;
    // 额外装饰UV-2
    private static final int DECOR_W_2 = 16;
    private static final int DECOR_H_2 = 16;
    private static final int DECOR_OVERFLOW_B = 0; // 向下超出背景边界的像素数
    private static final float NOISE_DENSITY = 0.30f; // 0-1噪点覆盖概率
    private static final float NOISE_UV_SCALE = 64.0f; // 越大颗粒越细
    private static final float NOISE_FLOW_SPEED = 0.25f; // “流动”速度
    private static final float ALPHA_BASE = 0.09f;
    private static final float ALPHA_MIN = 0.02f;
    private static final float ALPHA_SHADOW_BOOST = 1.10f;
    private static final float SAT_BASE = 0.70f;
    private static final float SAT_SHADOW_GAIN = 0.30f;
    private static final float VAL_BASE = 0.85f;
    private static final float VAL_LUMA_COMP = 0.25f;
    // 是否加色混合
    private static final boolean NOISE_ADDITIVE = true;
    // 时间周期
    private static final long NOISE_PERIOD_MS = 5560L;
    private static boolean noiseDataLoaded = false;
    // 背景
    private static boolean[] baseMask;
    private static float[] baseLuma;
    private static float[] baseEdge;
    // 装饰1
    private static boolean[] decor1Mask;
    private static float[] decor1Luma;
    private static float[] decor1Edge;
    private static int decor1W, decor1H;
    private static int cachedMapW = -1, cachedMapH = -1;
    private static int[] suMapX = null;
    private static int[] svMapY = null;
    private static final boolean USE_LEGACY_FLOAT_HASH = true; // 默认使用更快的 int 定点 hash 想保持旧外观可切回 legacy
    private static final int NOISE_FP = 256; // 定点精度(越大越细腻/越平滑流动)
    private static final int HASH_MASK = 0x7fffffff;
    private static final int NOISE_CUTOFF = (int) ((1.0f - NOISE_DENSITY) * (float) HASH_MASK);
    private static final boolean ENABLE_NOISE_LOD = false; // 超大 tooltip LOD
    private static final int LOD_AREA_2 = 120_000; // 面积超阈值 -> step=2
    private static final int LOD_AREA_3 = 240_000; // 面积超阈值 -> step=3
    // 装饰2
    private static boolean[] decor2Mask;
    private static float[] decor2Luma;
    private static float[] decor2Edge;
    private static int decor2W, decor2H;

    public static void render(GuiGraphics graphics, Font font, List<ClientTooltipComponent> components, int mouseX, int mouseY, int screenW, int screenH, ClientTooltipPositioner positioner) {
        if (components == null || components.isEmpty()) return;

        int contentW = 0;
        int contentH = components.size() == 1 ? -EXTRA_AFTER_FIRST_LINE : 0;

        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            contentW = Math.max(contentW, component.getWidth(font));
            contentH += component.getHeight();
            if (i == 0) contentH += EXTRA_AFTER_FIRST_LINE;
        }

        Vector2ic pos = positioner.positionTooltip(screenW, screenH, mouseX, mouseY, contentW, contentH);
        int x = pos.x();
        int y = pos.y();

        int customPaddingX = 6;
        int customPaddingY = 6;

        int bgX = x - TooltipRenderUtil.PADDING_LEFT;
        int bgY = y - TooltipRenderUtil.PADDING_TOP;

        int textStartX = bgX + CORNER; // 内边距
        int lineY = bgY + CORNER; // 顶部内边距

        int bgW = contentW + TooltipRenderUtil.PADDING_LEFT + TooltipRenderUtil.PADDING_RIGHT + customPaddingX + EXTRA_RIGHT;
        int bgH = contentH + TooltipRenderUtil.PADDING_TOP + TooltipRenderUtil.PADDING_BOTTOM + customPaddingY;

        // 装饰坐标
        int decor1X = bgX + bgW - DECOR_W - DECOR_PAD_R;
        int decor1Y = bgY + bgH - DECOR_H - DECOR_PAD_B;

        int decor2X = bgX + (bgW - DECOR_W_2) / 2;
        int decor2Y = bgY + bgH - DECOR_H_2 + DECOR_OVERFLOW_B;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        RenderSystem.setShaderTexture(0, TRUE_DEMON_BOW_TEXTURE);
        blitNineSliceScaled(graphics, bgX, bgY, bgW, bgH, CORNER, TEX_W, TEX_H);

        // 装饰1
        RenderSystem.setShaderTexture(0, EXTRA_DECORATION);
        graphics.blit(EXTRA_DECORATION, decor1X, decor1Y, 0, 0, DECOR_W, DECOR_H, DECOR_W, DECOR_H);

        // 装饰2
        RenderSystem.setShaderTexture(0, EXTRA_DECORATION_2);
        graphics.blit(EXTRA_DECORATION_2, decor2X, decor2Y, 0, 0, DECOR_W_2, DECOR_H_2, DECOR_W_2, DECOR_H_2);

        renderAnimatedNoiseLayer(graphics, bgX, bgY, bgW, bgH, decor1X, decor1Y, decor2X, decor2Y);

        var matrix = graphics.pose().last().pose();
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            component.renderText(font, textStartX, lineY, matrix, graphics.bufferSource());
            lineY += component.getHeight() + (i == 0 ? EXTRA_AFTER_FIRST_LINE : 0);
        }

        lineY = bgY;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            component.renderImage(font, textStartX, lineY, graphics);
            lineY += component.getHeight() + (i == 0 ? EXTRA_AFTER_FIRST_LINE : 0);
        }

        graphics.pose().popPose();
        graphics.flush();
    }

    private static void blitNineSliceScaled(GuiGraphics graphics, int x, int y, int w, int h, int corner, int texW, int texH) {
        int cX = Math.min(corner, w / 2);
        int cY = Math.min(corner, h / 2);

        int midW = Math.max(0, w - cX * 2);
        int midH = Math.max(0, h - cY * 2);

        int srcMidW = Math.max(0, texW - corner * 2);
        int srcMidH = Math.max(0, texH - corner * 2);

        Matrix4f matrix4f = graphics.pose().last().pose();

        blitPartScaled(graphics, matrix4f, x, y, cX, cY, 0, 0, corner, corner, texW, texH);
        blitPartScaled(graphics, matrix4f, x + w - cX,  y, cX, cY, texW - corner, 0, corner, corner, texW, texH);
        blitPartScaled(graphics, matrix4f, x, y + h - cY,  cX, cY, 0, texH - corner, corner, corner, texW, texH);
        blitPartScaled(graphics, matrix4f, x + w - cX, y + h - cY,  cX, cY, texW - corner, texH - corner, corner, corner, texW, texH);

        if (midW > 0) {
            blitPartScaled(graphics, matrix4f, x + cX, y, midW, cY, corner, 0, srcMidW, corner, texW, texH);
            blitPartScaled(graphics, matrix4f, x + cX, y + h - cY,  midW, cY, corner, texH - corner, srcMidW, corner, texW, texH);
        }
        if (midH > 0) {
            blitPartScaled(graphics, matrix4f, x, y + cY, cX, midH, 0, corner, corner, srcMidH, texW, texH);
            blitPartScaled(graphics, matrix4f, x + w - cX,  y + cY,     cX, midH, texW - corner, corner, corner, srcMidH, texW, texH);
        }

        if (midW > 0 && midH > 0) {
            blitPartScaled(graphics, matrix4f, x + cX, y + cY, midW, midH, corner, corner, srcMidW, srcMidH, texW, texH);
        }
    }

    private static void blitPartScaled(GuiGraphics graphics, Matrix4f matrix4f, int x, int y, int w, int h, int u, int v, int uw, int vh, int texW, int texH) {
        if (w <= 0 || h <= 0 || uw <= 0 || vh <= 0) return;

        float u0 = u / (float) texW;
        float v0 = v / (float) texH;
        float u1 = (u + uw) / (float) texW;
        float v1 = (v + vh) / (float) texH;

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        builder.vertex(matrix4f, x, y + h, 0).uv(u0, v1).endVertex();
        builder.vertex(matrix4f, x + w, y + h, 0).uv(u1, v1).endVertex();
        builder.vertex(matrix4f, x + w, y, 0).uv(u1, v0).endVertex();
        builder.vertex(matrix4f, x, y, 0).uv(u0, v0).endVertex();

        BufferUploader.drawWithShader(builder.end());
    }

    private static void renderAnimatedNoiseLayer(GuiGraphics graphics, int bgX, int bgY, int bgW, int bgH, int decor1X, int decor1Y, int decor2X, int decor2Y) {
        ensureNoiseDataLoaded();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();

        if (NOISE_ADDITIVE) {
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        } else {
            RenderSystem.defaultBlendFunc();
        }

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float time = (System.currentTimeMillis() % NOISE_PERIOD_MS) / (float) NOISE_PERIOD_MS;

        int stepBg = computeNoiseStep(bgW, bgH);
        appendNoiseForNineSlice(builder, matrix, bgX, bgY, bgW, bgH, time, stepBg);
        appendNoiseForScaledTexture(builder, matrix, decor1X, decor1Y, DECOR_W, DECOR_H, decor1Mask, decor1Luma, decor1Edge, decor1W, decor1H, time, 1);
        appendNoiseForScaledTexture(builder, matrix, decor2X, decor2Y, DECOR_W_2, DECOR_H_2, decor2Mask, decor2Luma, decor2Edge, decor2W, decor2H, time, 1);

        BufferUploader.drawWithShader(builder.end());

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void appendNoiseForNineSlice(BufferBuilder builder, Matrix4f matrix4f, int x, int y, int w, int h, float time, int step) {
        if (baseMask == null || w <= 0 || h <= 0) return;

        ensureNineSliceMaps(w, h);
        if (suMapX == null || svMapY == null) return;

        final float invW = 1.0f / (float) w;
        final float invH = 1.0f / (float) h;

        final int uvStepX = Math.max(1, (int) (NOISE_UV_SCALE * (float) NOISE_FP / (float) w));
        final int uvStepY = Math.max(1, (int) (NOISE_UV_SCALE * (float) NOISE_FP / (float) h));
        final int flowFP = (int) (time * NOISE_FLOW_SPEED * (float) NOISE_FP);

        for (int py = 0; py < h; py += step) {
            int sv = svMapY[py];
            int rowBase = sv * TEX_W;

            float v = py * invH;
            int nyBase = py * uvStepY - flowFP;

            for (int px = 0; px < w; px += step) {
                int su = suMapX[px];
                int idx = rowBase + su;

                if (!baseMask[idx]) continue;

                if (USE_LEGACY_FLOAT_HASH) {
                    float u = px * invW;
                    float n = legacyHash(u * NOISE_UV_SCALE + time * NOISE_FLOW_SPEED,
                            v * NOISE_UV_SCALE - time * NOISE_FLOW_SPEED);
                    if (n < 1.0f - NOISE_DENSITY) continue;
                } else {
                    int nx = px * uvStepX + flowFP;
                    if (noisePass(nx, nyBase)) continue;
                }

                float luma = (baseLuma != null) ? baseLuma[idx] : 0.5f;
                float edge = (baseEdge != null) ? baseEdge[idx] : 1.0f;

                float alpha = ALPHA_BASE * (ALPHA_SHADOW_BOOST - luma);
                alpha *= edge * edge;
                if (alpha < ALPHA_MIN) continue;

                float u = px * invW;

                float hue = time + u * 0.30f + v * 0.10f;
                if (hue >= 1.0f) hue -= 1.0f;

                float sat = SAT_BASE + SAT_SHADOW_GAIN * (1.0f - luma);
                float val = VAL_BASE - VAL_LUMA_COMP * (luma - 0.5f);
                sat = Mth.clamp(sat, 0f, 1f);
                val = Mth.clamp(val, 0f, 1f);

                int rgb = Mth.hsvToRgb(hue, sat, val);
                float r = ((rgb >> 16) & 255) / 255f;
                float g = ((rgb >>  8) & 255) / 255f;
                float b = ( rgb & 255) / 255f;

                float sx = x + px;
                float sy = y + py;
                putPixelQuadSized(builder, matrix4f, sx, sy, step, r, g, b, alpha);
            }
        }
    }

    private static void appendNoiseForScaledTexture(BufferBuilder buf, Matrix4f matrix4f, int x, int y, int drawW, int drawH, boolean[] mask, float[] lumaArr, float[] edgeArr, int texW, int texH, float time, int step) {
        if (mask == null || texW <= 0 || texH <= 0 || drawW <= 0 || drawH <= 0) return;

        final float invW = 1.0f / (float) drawW;
        final float invH = 1.0f / (float) drawH;

        final int uvStepX = Math.max(1, (int) (NOISE_UV_SCALE * (float) NOISE_FP / (float) drawW));
        final int uvStepY = Math.max(1, (int) (NOISE_UV_SCALE * (float) NOISE_FP / (float) drawH));
        final int flowFP  = (int) (time * NOISE_FLOW_SPEED * (float) NOISE_FP);

        for (int py = 0; py < drawH; py += step) {
            float v = py * invH;
            int nyBase = py * uvStepY - flowFP;

            int sv = (int) (v * (float) texH);
            sv = Mth.clamp(sv, 0, texH - 1);
            int rowBase = sv * texW;

            for (int px = 0; px < drawW; px += step) {
                float u = px * invW;

                int su = (int) (u * (float) texW);
                su = Mth.clamp(su, 0, texW - 1);

                int idx = rowBase + su;
                if (!mask[idx]) continue;

                if (USE_LEGACY_FLOAT_HASH) {
                    float n = legacyHash(u * NOISE_UV_SCALE + time * NOISE_FLOW_SPEED,
                            v * NOISE_UV_SCALE - time * NOISE_FLOW_SPEED);
                    if (n < 1.0f - NOISE_DENSITY) continue;
                } else {
                    int nx = px * uvStepX + flowFP;
                    if (noisePass(nx, nyBase)) continue;
                }

                float luma = (lumaArr != null) ? lumaArr[idx] : 0.5f;
                float edge = (edgeArr != null) ? edgeArr[idx] : 1.0f;

                float alpha = ALPHA_BASE * (ALPHA_SHADOW_BOOST - luma);
                alpha *= edge * edge;
                if (alpha < ALPHA_MIN) continue;

                float hue = time + u * 0.30f + v * 0.10f;
                if (hue >= 1.0f) hue -= 1.0f;

                float sat = SAT_BASE + SAT_SHADOW_GAIN * (1.0f - luma);
                float val = VAL_BASE - VAL_LUMA_COMP * (luma - 0.5f);
                sat = Mth.clamp(sat, 0f, 1f);
                val = Mth.clamp(val, 0f, 1f);

                int rgb = Mth.hsvToRgb(hue, sat, val);
                float r = ((rgb >> 16) & 255) / 255f;
                float g = ((rgb >>  8) & 255) / 255f;
                float b = ( rgb        & 255) / 255f;

                float sx = x + px;
                float sy = y + py;
                putPixelQuadSized(buf, matrix4f, sx, sy, step, r, g, b, alpha);
            }
        }
    }

    private static void ensureNoiseDataLoaded() {
        if (noiseDataLoaded) return;

        baseMask = loadAlphaMask(TRUE_DEMON_BOW_TEXTURE, 0, 0, TEX_W, TEX_H);
        baseLuma = loadLuma(TRUE_DEMON_BOW_TEXTURE, 0, 0, TEX_W, TEX_H);
        baseEdge = (baseMask != null) ? buildEdgeWeights(baseMask, TEX_W, TEX_H) : null;

        int[] wh1 = getImageSize(EXTRA_DECORATION);
        decor1W = wh1[0]; decor1H = wh1[1];
        if (decor1W > 0 && decor1H > 0) {
            decor1Mask = loadAlphaMask(EXTRA_DECORATION, 0, 0, decor1W, decor1H);
            decor1Luma = loadLuma(EXTRA_DECORATION, 0, 0, decor1W, decor1H);
            decor1Edge = (decor1Mask != null) ? buildEdgeWeights(decor1Mask, decor1W, decor1H) : null;
        }

        int[] wh2 = getImageSize(EXTRA_DECORATION_2);
        decor2W = wh2[0]; decor2H = wh2[1];
        if (decor2W > 0 && decor2H > 0) {
            decor2Mask = loadAlphaMask(EXTRA_DECORATION_2, 0, 0, decor2W, decor2H);
            decor2Luma = loadLuma(EXTRA_DECORATION_2, 0, 0, decor2W, decor2H);
            decor2Edge = (decor2Mask != null) ? buildEdgeWeights(decor2Mask, decor2W, decor2H) : null;
        }

        noiseDataLoaded = true;
    }

    private static int[] getImageSize(ResourceLocation tex) {
        try {
            var mc = Minecraft.getInstance();
            var opt = mc.getResourceManager().getResource(tex);
            if (opt.isEmpty()) return new int[]{0, 0};
            try (var in = opt.get().open(); var img = NativeImage.read(in)) {
                return new int[]{img.getWidth(), img.getHeight()};
            }
        } catch (Exception ignored) {}
        return new int[]{0, 0};
    }

    private static boolean[] loadAlphaMask(ResourceLocation tex, int u, int v, int w, int h) {
        try {
            var mc = Minecraft.getInstance();
            var opt = mc.getResourceManager().getResource(tex);
            if (opt.isEmpty()) return null;

            try (var in = opt.get().open(); var img = NativeImage.read(in)) {
                int texW = img.getWidth();
                int texH = img.getHeight();
                int rw = Math.min(w, Math.max(0, texW - u));
                int rh = Math.min(h, Math.max(0, texH - v));

                boolean[] mask = new boolean[w * h];

                for (int yy = 0; yy < rh; yy++) {
                    for (int xx = 0; xx < rw; xx++) {
                        int argb = img.getPixelRGBA(u + xx, v + yy); // A-BGR
                        int a = (argb >>> 24) & 0xFF;
                        mask[yy * w + xx] = a > 8;
                    }
                }
                return mask;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static float[] loadLuma(ResourceLocation tex, int u, int v, int w, int h) {
        try {
            var mc = Minecraft.getInstance();
            var opt = mc.getResourceManager().getResource(tex);
            if (opt.isEmpty()) return null;

            try (var in = opt.get().open(); var img = NativeImage.read(in)) {
                int texW = img.getWidth();
                int texH = img.getHeight();
                int rw = Math.min(w, Math.max(0, texW - u));
                int rh = Math.min(h, Math.max(0, texH - v));

                float[] out = new float[w * h];

                for (int yy = 0; yy < rh; yy++) {
                    for (int xx = 0; xx < rw; xx++) {
                        int packed = img.getPixelRGBA(u + xx, v + yy); // A-BGR
                        float b = ((packed >> 16) & 0xFF) / 255f;
                        float g = ((packed >> 8)  & 0xFF) / 255f;
                        float r = ( packed & 0xFF) / 255f;
                        out[yy * w + xx] = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                    }
                }
                return out;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static float[] buildEdgeWeights(boolean[] mask, int w, int h) {
        float[] ew = new float[w * h];
        for (int y = 1; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                if (!mask[i]) { ew[i] = 0f; continue; }
                int neighbors = 0;
                for (int oy = -1; oy <= 1; oy++) {
                    for (int ox = -1; ox <= 1; ox++) {
                        if (mask[(y + oy) * w + (x + ox)]) neighbors++;
                    }
                }
                ew[i] = Math.max(0f, (neighbors - 5) / 4f);
            }
        }
        return ew;
    }

    private static int computeNoiseStep(int w, int h) {
        if (!ENABLE_NOISE_LOD) return 1;
        long area = (long) w * (long) h;
        if (area >= LOD_AREA_3) return 3;
        if (area >= LOD_AREA_2) return 2;
        return 1;
    }

    private static void ensureNineSliceMaps(int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (w == cachedMapW && h == cachedMapH && suMapX != null && svMapY != null) return;

        cachedMapW = w;
        cachedMapH = h;
        suMapX = new int[w];
        svMapY = new int[h];

        int srcMidW = Math.max(1, TEX_W - CORNER * 2);
        int srcMidH = Math.max(1, TEX_H - CORNER * 2);

        // X 映射
        for (int px = 0; px < w; px++) {
            int su;
            if (px < CORNER) {
                su = px;
            } else if (px >= w - CORNER) {
                su = TEX_W - (w - px);
            } else {
                su = CORNER + ((px - CORNER) % srcMidW);
            }
            if (su < 0) su = 0;
            suMapX[px] = su;
        }

        // Y 映射
        for (int py = 0; py < h; py++) {
            int sv;
            if (py < CORNER) {
                sv = py;
            } else if (py >= h - CORNER) {
                sv = TEX_H - (h - py);
            } else {
                sv = CORNER + ((py - CORNER) % srcMidH);
            }
            if (sv < 0) sv = 0;
            svMapY[py] = sv;
        }
    }

    private static int hash2i(int x, int y) {
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return n ^ (n >>> 16);
    }

    private static float legacyHash(float u, float v) {
        int x = Float.floatToIntBits(u);
        int y = Float.floatToIntBits(v);
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0x7fffffff) / (float) 0x7fffffff;
    }

    private static boolean noisePass(int nx, int ny) {
        int h = hash2i(nx, ny) & HASH_MASK;
        return h < NOISE_CUTOFF;
    }

    private static void putPixelQuadSized(BufferBuilder builder, Matrix4f matrix4f, float x, float y, int size, float r, float g, float b, float a) {
        float s = (float) size;
        builder.vertex(matrix4f, x, y + s, 0).color(r, g, b, a).endVertex();
        builder.vertex(matrix4f, x + s, y + s, 0).color(r, g, b, a).endVertex();
        builder.vertex(matrix4f, x + s, y, 0).color(r, g, b, a).endVertex();
        builder.vertex(matrix4f, x, y, 0).color(r, g, b, a).endVertex();
    }
}
