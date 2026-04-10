package flu.kitten.adorablearmory.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import flu.kitten.adorablearmory.util.RococoColor;
import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Matrix4f;

import java.util.*;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

@OnlyIn(Dist.CLIENT)
public class AlysiaBossBarRenderer {

    private static final ResourceLocation BOSS_BAR_LOCATION = new ResourceLocation(MODID, "textures/gui/boss_bar.png");
    private static final ResourceLocation DECORATIVE_OVERLAY = new ResourceLocation(MODID, "textures/gui/boss_bar_decorations.png");
    private static final int BAR_WIDTH = 226; //  new-226
    private static final int BAR_HEIGHT = 17; //   new-17
    private static final int TEXTURE_SIZE = 256;
    private static final int FRAME_U = 0;
    private static final int FRAME_V = 0;
    private static final int DECOR_HEIGHT = 39; // max
    private static final int DECOR_WIDTH = 230; // max
    private static final float CONTENT_BAR_WIDTH = 188;
    private static final float CONTENT_BAR_HEIGHT = 5;
    private static final float CONTENT_BAR_OFFSET_X = (BAR_WIDTH - CONTENT_BAR_WIDTH) / 2.0f;
    private static final float CONTENT_BAR_OFFSET_Y = (BAR_HEIGHT - CONTENT_BAR_HEIGHT) / 2.0f;
    private static final int HEALTH_BAR_INNER_U = (int) CONTENT_BAR_OFFSET_X;
    private static final float INSULIN_FADE_IN = 0.242f; // 无敌彩虹覆盖层的淡入 淡出时间/秒
    private static final float INSULIN_FADE_OUT = 0.368f;
    private final Minecraft minecraft;
    private final Map<UUID, BossBarInfo> bossBars = new HashMap<>();
    private static final float HEALTH_DECREASE_SPEED = 1.05f; // 主血条每秒能收缩的最大“归一化血量”
    private static final float RESIDUE_FOLLOW_SPEED = 0.05f; // 残影条“延迟出现的阴影条”在“开始追踪”之后 每秒收缩的基础速率
    private static final float RESIDUE_DELAY = 0.40f; // 当主血条“被扣血”之后 残影等待的时间/秒
    private static final float DECELERATION_FACTOR = 3.0f; // 在残影收缩时 基于“残影与主血条差距”再叠加一个 线性加速系数
    private boolean enableDecorativeOverlay = true;
    private float decorativeAlpha = 1.0f;
    private long lastFrameTime = -1;
    private final SplittableRandom random = new SplittableRandom();
    private boolean[] frameMask, decorMask;
    private int frameMaskW, frameMaskH, decorMaskW, decorMaskH;

    // 透明度 - ALPHA_BASE、ALPHA_SHADOW_BOOST、ALPHA_EDGE_POWER、ALPHA_MIN
    // 饱和度 - SAT_BASE、SAT_SHADOW_GAIN、JITTER_SAT
    // 明度 - VAL_BASE、VAL_LUMA_COMP、JITTER_VAL
    private static final float NOISE_DENSITY = 0.30f; // 0..1 fraction of pixels considered for noise
    private static final float NOISE_UV_SCALE = 64.0f; // higher = finer grain tied to texture UVs
    private static final float NOISE_FLOW_SPEED = 0.25f; // UV noise flow speed
    private static final float ALPHA_BASE = 0.09f; // base opacity
    private static final float ALPHA_MIN = 0.02f; // discard dots below this
    private static final float ALPHA_SHADOW_BOOST = 1.10f; // how much darker areas increase alpha
    private static final float ALPHA_EDGE_POWER = 2.0f;  // stronger falloff toward edges when > 1
    private static final float SAT_BASE = 0.70f;
    private static final float SAT_SHADOW_GAIN = 0.30f; // adds up to this much sat in dark regions
    private static final float VAL_BASE = 0.85f;
    private static final float VAL_LUMA_COMP = 0.25f; // subtract VAL_LUMA_COMP*(luma-0.5f)
    private static final float JITTER_SAT = 0.05f;
    private static final float JITTER_VAL = 0.05f;
    private float[] frameLuma;
    private float[] edgeWeight;

    public AlysiaBossBarRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    public void render(GuiGraphics guiGraphics, ScarletLoraAlysia boss) {
        if (boss == null) return;
        if (boss.isRemoved()) {
            bossBars.remove(boss.getUUID()); return;
        }

        long currentTime = System.nanoTime();
        if (lastFrameTime == -1) lastFrameTime = currentTime;

        UUID bossId = boss.getUUID();
        BossBarInfo info = bossBars.computeIfAbsent(bossId, k -> new BossBarInfo(boss.getCustomHealth() / boss.getCustomMaxHealth()));

        float deltaTime = (currentTime - lastFrameTime) / 1_000_000_000.0f;
        lastFrameTime = currentTime;

        deltaTime = Mth.clamp(deltaTime, 0.0f, 0.1f);

        float maxHealth = boss.getCustomMaxHealth();
        float currentHealth = boss.getCustomHealth();
        if (maxHealth > 0) {
            float currentHealthPercent = Mth.clamp(currentHealth / maxHealth, 0.0f, 1.0f);
            info.updateHealth(currentHealthPercent, deltaTime, boss.isInvulnerable());
        }

        int phaseIndex = boss.getCurrentPhase();
        int screenWidth = this.minecraft.getWindow().getGuiScaledWidth();
        int x = (screenWidth - BAR_WIDTH) / 2;
        int y = 12;

        renderEnhancedBossBar(guiGraphics, x, y, info, boss, phaseIndex);
        bossBars.entrySet().removeIf(entry -> !entry.getValue().isAlive());
    }

    private void renderEnhancedBossBar(GuiGraphics graphics, int x, int y, BossBarInfo info, ScarletLoraAlysia boss, int phaseColor) {
        graphics.pose().pushPose();
        int offset = 12;
        graphics.pose().translate(0, offset,0);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        drawBossName(graphics, x, y, boss.getName());
        renderFrame(graphics, x, y);

        if (info.displayedResidueHealth > info.displayedHealth) {
            renderResidueBar(graphics, x, y, info);
        }

        if (info.displayedHealth > 0.001f) {
            renderHealthBar(graphics, x, y, info, phaseColor);
        }

        renderNoiseLayer(graphics, x, y, info);

        drawHealthText(graphics, x, y, boss);

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();

        if (enableDecorativeOverlay) renderDecorationLayer(graphics, x, y, boss);

        renderAllBossBarNoiseLayer(graphics, x, y, boss);

        graphics.pose().popPose();
    }

    @SuppressWarnings("unused")
    private void renderAllBossBarNoiseLayer(GuiGraphics graphics, int x, int y, ScarletLoraAlysia boss) {
        maskLoaded();
        ensureLightingDataLoaded();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        var matrix = graphics.pose().last().pose();
        var buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int decorX = x + (BAR_WIDTH - DECOR_WIDTH) / 2;
        int decorY = y + (BAR_HEIGHT - DECOR_HEIGHT) / 2;

        int startX = Math.min(x, decorX);
        int startY = Math.min(y, decorY);
        int endX = Math.max(x + BAR_WIDTH, decorX + DECOR_WIDTH);
        int endY = Math.max(y + BAR_HEIGHT, decorY + DECOR_HEIGHT);

        int width  = endX - startX;
        int height = endY - startY;

        float time = (System.currentTimeMillis() % 5560L) / 5560.0f;

        for (int py = 0; py < height; py++) {
            for (int px = 0; px < width; px++) {

                float sx = startX + px;
                float sy = startY + py;
                float u = (sx - x) / (float) BAR_WIDTH;
                float v = (sy - y) / (float) BAR_HEIGHT;
                float n = hash(u * NOISE_UV_SCALE + time * NOISE_FLOW_SPEED, v * NOISE_UV_SCALE - time * NOISE_FLOW_SPEED);
                if (n < 1.0f - NOISE_DENSITY) continue;

                boolean inside = false;

                int fx = (int)(sx - x);
                int fy = (int)(sy - y);
                if (frameMask != null && fx >= 0 && fy >= 0 && fx < frameMaskW && fy < frameMaskH) {
                    inside = frameMask[fy * frameMaskW + fx];
                }

                int dx = (int)(sx - decorX);
                int dy = (int)(sy - decorY);
                if (!inside && enableDecorativeOverlay && decorMask != null && dx >= 0 && dy >= 0 && dx < decorMaskW && dy < decorMaskH) {
                    inside = decorMask[dy * decorMaskW + dx];
                }

                float barX0 = x + CONTENT_BAR_OFFSET_X;
                float barY0 = y + CONTENT_BAR_OFFSET_Y;
                float barX1 = barX0 + CONTENT_BAR_WIDTH;
                float barY1 = barY0 + CONTENT_BAR_HEIGHT;
                if (!inside && sx >= barX0 && sx < barX1 && sy >= barY0 && sy < barY1) {
                    inside = true;
                }

                if (!inside) continue;

                float luma = 0.5f;
                float edge = 1.0f;
                if (fx >= 0 && fy >= 0 && fx < frameMaskW && fy < frameMaskH) {
                    if (frameLuma != null) luma = frameLuma[fy * frameMaskW + fx];
                    if (edgeWeight != null) edge = edgeWeight[fy * frameMaskW + fx];
                }

                float alpha = ALPHA_BASE * (ALPHA_SHADOW_BOOST - luma);
                alpha *= (float) Math.pow(edge, ALPHA_EDGE_POWER);
                if (alpha < ALPHA_MIN) continue;

                float hue = (time + u * 0.30f + v * 0.10f) % 1.0f;
                float sat = SAT_BASE + SAT_SHADOW_GAIN * (1.0f - luma) + (random.nextFloat() - 0.5f) * JITTER_SAT;
                float val = VAL_BASE - VAL_LUMA_COMP * (luma - 0.5f) + (random.nextFloat() - 0.5f) * JITTER_VAL;

                sat = Math.max(0f, Math.min(1f, sat));
                val = Math.max(0f, Math.min(1f, val));

                float[] rgb = RococoColor.hsbToRgb(hue, sat, val);

                // 1×1 quad
                buffer.vertex(matrix, sx, sy + 1, 0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
                buffer.vertex(matrix, sx + 1, sy + 1, 0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
                buffer.vertex(matrix, sx + 1, sy, 0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
                buffer.vertex(matrix, sx, sy, 0).color(rgb[0], rgb[1], rgb[2], alpha).endVertex();
            }
        }

        BufferUploader.drawWithShader(buffer.end());
        RenderSystem.disableBlend();
    }

    private void renderNoiseLayer(GuiGraphics graphics, int x, int y, BossBarInfo info) {
        // Calculate the current width of the noise effect based on the main health bar's width.
        int noiseWidth = Math.round(CONTENT_BAR_WIDTH * info.displayedHealth);
        if (noiseWidth <= 0) return; // Don't render if the health bar is empty.

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float barStartX = x + CONTENT_BAR_OFFSET_X;
        float barStartY = y + CONTENT_BAR_OFFSET_Y;

        for (int py = 0; py < CONTENT_BAR_HEIGHT; py++) {
            for (int px = 0; px < noiseWidth; px++) {
                float noise = this.random.nextFloat();
                float alpha = this.random.nextFloat() * 0.32f; // Max 35% opacity

                float pixelX = barStartX + px;
                float pixelY = barStartY + py;

                // Draw a 1x1 quad pixel
                bufferBuilder.vertex(matrix, pixelX, pixelY + 1, 0).color(noise, noise, noise, alpha).endVertex();
                bufferBuilder.vertex(matrix, pixelX + 1, pixelY + 1, 0).color(noise, noise, noise, alpha).endVertex();
                bufferBuilder.vertex(matrix, pixelX + 1, pixelY, 0).color(noise, noise, noise, alpha).endVertex();
                bufferBuilder.vertex(matrix, pixelX, pixelY, 0).color(noise, noise, noise, alpha).endVertex();
            }
        }
        BufferUploader.drawWithShader(bufferBuilder.end());
        RenderSystem.disableBlend();
    }

    private void drawHealthText(GuiGraphics graphics, int x, int y, ScarletLoraAlysia boss) {
        String healthText = String.format(Locale.ROOT, "%.0f / %.0f", boss.getCustomHealth(), boss.getMaxHealth());
        Component healthComponent = Component.literal(healthText).withStyle(ChatFormatting.BOLD);

        int textWidth = this.minecraft.font.width(healthComponent);
        int textX = x + (BAR_WIDTH - textWidth) / 2;

        int textY = y + (BAR_HEIGHT - this.minecraft.font.lineHeight) / 2 + 1;
        graphics.drawString(this.minecraft.font, healthComponent, textX, textY, 0xFFFFFF, true);
    }

    private void renderResidueBar(GuiGraphics graphics, int x, int y, BossBarInfo info) {
        float residuePct = info.displayedResidueHealth;
        if (residuePct <= 0f) return;

        float barStartX = x + CONTENT_BAR_OFFSET_X;
        float barY = y + CONTENT_BAR_OFFSET_Y;
        float residueWidth = CONTENT_BAR_WIDTH * residuePct;

        float red = 1.0f;
        float green = 0.84f;
        float blue = 0.0f;
        float alpha = 1.0f;

        renderColoredQuad(graphics, barStartX, barY, residueWidth, red, green, blue, alpha);
    }

    @SuppressWarnings("unused")
    private void renderHealthBar(GuiGraphics graphics, int x, int y, BossBarInfo info, int phaseColor) {
        float mainPct = info.displayedHealth;
        if (mainPct <= 0f) return;

        float barStartX = x + CONTENT_BAR_OFFSET_X;
        float barY = y + CONTENT_BAR_OFFSET_Y;
        float width = CONTENT_BAR_WIDTH * mainPct;

        renderTexturedHealthBar(graphics, barStartX, barY, width);
        // 彩虹层
        if (info.insulinBlend > 0.01f) {
            renderRainbowLayer(graphics, barStartX, barY, width, info.insulinBlend * 0.6f);
        }
    }

    private void renderTexturedHealthBar(GuiGraphics graphics, float x, float y, float width) {
        if (width <= 0) return;
        RenderSystem.setShaderTexture(0, BOSS_BAR_LOCATION);

        PoseStack poseStack = graphics.pose();
        Matrix4f matrix = poseStack.last().pose();
        float u0 = HEALTH_BAR_INNER_U / (float) TEXTURE_SIZE;
        float u1 = (HEALTH_BAR_INNER_U + width) / (float) TEXTURE_SIZE;
        float v0 = 23 / (float) TEXTURE_SIZE;
        float v1 = (23 + CONTENT_BAR_HEIGHT) / (float) TEXTURE_SIZE;

        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder bufferbuilder = Tesselator.getInstance().getBuilder();
        bufferbuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferbuilder.vertex(matrix, x, y + CONTENT_BAR_HEIGHT, 0.0F).uv(u0, v1).endVertex();
        bufferbuilder.vertex(matrix, x + width, y + CONTENT_BAR_HEIGHT, 0.0F).uv(u1, v1).endVertex();
        bufferbuilder.vertex(matrix, x + width, y, 0.0F).uv(u1, v0).endVertex();
        bufferbuilder.vertex(matrix, x, y, 0.0F).uv(u0, v0).endVertex();
        BufferUploader.drawWithShader(bufferbuilder.end());
    }

    private void renderColoredQuad(GuiGraphics graphics, float x, float y, float width, float red, float green, float blue, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();

        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bufferBuilder.vertex(matrix, x, y + CONTENT_BAR_HEIGHT, 0.0f).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, x + width, y + CONTENT_BAR_HEIGHT, 0.0f).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, x + width, y, 0.0f).color(red, green, blue, alpha).endVertex();
        bufferBuilder.vertex(matrix, x, y, 0.0f).color(red, green, blue, alpha).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    private void renderRainbowLayer(GuiGraphics graphics, float x, float y, float width, float alpha) {
        if (width <= 0 || alpha <= 0) return;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f matrix = graphics.pose().last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        int segments = Math.max(16, (int)(width / 6)); // 6px 一段
        float time = (System.currentTimeMillis() % 8000L) / 8000.0f; // 缓慢循环
        float sat = 0.32f;
        float bri = 1.0f;

        for (int i = 0; i < segments; i++) {
            float t0 = (float)i / segments;
            float t1 = (float)(i + 1) / segments;
            float x0 = x + width * t0;
            float x1 = x + width * t1;

            float h0 = (time + t0) % 1.0f;
            float h1 = (time + t1) % 1.0f;

            float[] c0 = RococoColor.hsbToRgb(h0, sat, bri);
            float[] c1 = RococoColor.hsbToRgb(h1, sat, bri);

            builder.vertex(matrix, x0, y + CONTENT_BAR_HEIGHT, 0.0f).color(c0[0], c0[1], c0[2], alpha).endVertex();
            builder.vertex(matrix, x1, y + CONTENT_BAR_HEIGHT, 0.0f).color(c1[0], c1[1], c1[2], alpha).endVertex();
            builder.vertex(matrix, x1, y, 0.0f).color(c1[0], c1[1], c1[2], alpha).endVertex();
            builder.vertex(matrix, x0, y, 0.0f).color(c0[0], c0[1], c0[2], alpha).endVertex();
        }

        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
    }

    private void drawBossName(GuiGraphics graphics, int x, int y, Component name) {
        int nameWidth = minecraft.font.width(name);
        int nameX = x + (BAR_WIDTH - nameWidth) / 2;
        //int centerX = x + BAR_WIDTH / 2;

        int baselineY;
        if (enableDecorativeOverlay) {
            int decorTop = y + (BAR_HEIGHT - DECOR_HEIGHT) / 2;
            baselineY = decorTop - minecraft.font.lineHeight;
        } else {
            baselineY = y - minecraft.font.lineHeight;
        }

        Component color = RococoColor.createRainbowText(name,2);
        //RococoColor.renderWaveRainbowText(graphics, minecraft.font, color, centerX, baselineY, 2, true);
        graphics.drawString(minecraft.font, color, nameX, baselineY, 0xFFFFFF, true);
    }

    private void renderFrame(GuiGraphics graphics, int x, int y) {
        RenderSystem.setShaderTexture(0, BOSS_BAR_LOCATION);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(BOSS_BAR_LOCATION, x, y, FRAME_U, FRAME_V, BAR_WIDTH, BAR_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    private void renderDecorationLayer(GuiGraphics graphics, int x, int y, ScarletLoraAlysia boss) {
        if (!enableDecorativeOverlay) return;
        try {
            RenderSystem.enableBlend(); // ←
            RenderSystem.defaultBlendFunc();

            RenderSystem.setShaderTexture(0, DECORATIVE_OVERLAY);
            float alpha = decorativeAlpha;

            int phase = boss.getCurrentPhase();
            if (phase >= 3) alpha = Math.min(1.0f, alpha * 1.2f);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

            final int DECOR_WIDTH = 230;
            final int DECOR_HEIGHT = 39;
            int decorX = x + (BAR_WIDTH - DECOR_WIDTH) / 2;
            int decorY = y + (BAR_HEIGHT - DECOR_HEIGHT) / 2;

            graphics.blit(DECORATIVE_OVERLAY, decorX, decorY, 0, 0, DECOR_WIDTH, DECOR_HEIGHT, TEXTURE_SIZE, TEXTURE_SIZE);

        } catch (Exception e) {
            enableDecorativeOverlay = false;
        }
    }

    public void setDecorativeOverlayEnabled(boolean enabled) {
        this.enableDecorativeOverlay = enabled;
    }

    public void setDecorativeAlpha(float alpha) {
        this.decorativeAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
    }

    private static float easeOutQuart(float t) {
        return 1.0f - (float)Math.pow(1.0f - t, 4.0);
    }

    private static class BossBarInfo {
        float actualHealth;
        float displayedHealth;
        float displayedResidueHealth;
        float residueDelayTimer = 0.0f;
        float insulinBlend = 0.0f;
        long lastUpdateTime;

        BossBarInfo(float initialHealth) {
            this.actualHealth = Mth.clamp(initialHealth, 0.0f, 1.0f);
            this.displayedHealth = this.actualHealth;
            this.displayedResidueHealth = this.actualHealth;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        boolean isAlive() {
            return System.currentTimeMillis() - lastUpdateTime < 9124;
        }

        void updateHealth(float newHealth, float deltaTime, boolean isInvulnerable) {
            this.lastUpdateTime = System.currentTimeMillis();
            float target = isInvulnerable ? 1.0f : 0.0f;
            if (insulinBlend < target) {
                insulinBlend = Math.min(1.0f, insulinBlend + deltaTime / INSULIN_FADE_IN);
            } else {
                insulinBlend = Math.max(0.0f, insulinBlend - deltaTime / INSULIN_FADE_OUT);
            }

            newHealth = Mth.clamp(newHealth, 0.0f, 1.0f);

            if (newHealth < this.actualHealth) {
                this.residueDelayTimer = RESIDUE_DELAY;
            } else if (newHealth > this.actualHealth) {
                this.displayedHealth = newHealth;
                this.displayedResidueHealth = Math.max(this.displayedResidueHealth, newHealth);
            }

            this.actualHealth = newHealth;

            if (this.displayedHealth > this.actualHealth) {
                float decreaseAmount = HEALTH_DECREASE_SPEED * deltaTime;
                this.displayedHealth = Math.max(this.actualHealth, this.displayedHealth - decreaseAmount);
            } else if (this.displayedHealth < this.actualHealth) {
                this.displayedHealth = this.actualHealth;
            }

            if (this.residueDelayTimer > 0) {
                this.residueDelayTimer -= deltaTime;
            }

            if (this.residueDelayTimer <= 0 && this.displayedResidueHealth > this.displayedHealth) {
                float healthDifference = this.displayedResidueHealth - this.displayedHealth;

                float decelerationMultiplier = 1.0f + (healthDifference * DECELERATION_FACTOR);
                float decreaseAmount = RESIDUE_FOLLOW_SPEED * decelerationMultiplier * deltaTime;

                float progress = 1.0f - (healthDifference / Math.max(0.001f, this.displayedResidueHealth));
                float easedProgress = easeOutQuart(progress);
                decreaseAmount *= (1.0f + easedProgress);

                this.displayedResidueHealth = Math.max(this.displayedHealth, this.displayedResidueHealth - decreaseAmount);
            } else if (this.displayedResidueHealth < this.displayedHealth) {
                this.displayedResidueHealth = this.displayedHealth;
            }

            if (Math.abs(this.displayedHealth - this.actualHealth) < 0.001f) {
                this.displayedHealth = this.actualHealth;
            }
            if (Math.abs(this.displayedResidueHealth - this.displayedHealth) < 0.001f) {
                this.displayedResidueHealth = this.displayedHealth;
            }
        }
    }

    private static float hash(float u, float v) {
        int x = Float.floatToIntBits(u);
        int y = Float.floatToIntBits(v);
        int n = x * 374761393 + y * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        return ((n ^ (n >>> 16)) & 0x7fffffff) / (float) 0x7fffffff;
    }

    private void ensureLightingDataLoaded() {
        if (frameLuma == null && frameMaskW > 0 && frameMaskH > 0) {
            frameLuma = loadLuma(BOSS_BAR_LOCATION, FRAME_U, FRAME_V, frameMaskW, frameMaskH);
        }
        if (edgeWeight == null && frameMask != null) {
            edgeWeight = buildEdgeWeights(frameMask, frameMaskW, frameMaskH);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private float[] loadLuma(ResourceLocation tex, int u, int v, int w, int h) {
        try {
            var resource = minecraft.getResourceManager().getResource(tex);
            if (resource.isEmpty()) return null;
            try (var stream = resource.get().open(); var image = NativeImage.read(stream)) {
                int texW = image.getWidth();
                int texH = image.getHeight();
                int rw = Math.min(w, Math.max(0, texW - u));
                int rh = Math.min(h, Math.max(0, texH - v));
                float[] floats = new float[w * h];
                for (int yy = 0; yy < rh; yy++) {
                    for (int xx = 0; xx < rw; xx++) {
                        int packed = image.getPixelRGBA(u + xx, v + yy); // A-BGR
                        float b = ((packed >> 16) & 0xFF) / 255f;
                        float g = ((packed >> 8 ) & 0xFF) / 255f;
                        float r = ( packed & 0xFF) / 255f;
                        floats[yy * w + xx] = 0.2126f * r + 0.7152f * g + 0.0722f * b;
                    }
                }
                return floats;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private float[] buildEdgeWeights(boolean[] mask, int w, int h) {
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
                // 9 = fully surrounded
                ew[i] = Math.max(0f, (neighbors - 5) / 4f);
            }
        }
        return ew;
    }

    private void maskLoaded() {
        if (frameMask == null) {
            frameMaskW = BAR_WIDTH;
            frameMaskH = BAR_HEIGHT;
            frameMask = loadAlphaMask(BOSS_BAR_LOCATION, FRAME_U, FRAME_V, BAR_WIDTH, BAR_HEIGHT);
        }
        if (enableDecorativeOverlay && decorMask == null) {
            decorMaskW = DECOR_WIDTH;
            decorMaskH = DECOR_HEIGHT;
            decorMask = loadAlphaMask(DECORATIVE_OVERLAY, 0, 0, DECOR_WIDTH, DECOR_HEIGHT);
        }
    }

    private boolean[] loadAlphaMask(ResourceLocation tex, int u, int v, int w, int h) {
        try {
            var opt = minecraft.getResourceManager().getResource(tex);
            if (opt.isEmpty()) return null;
            try (var in = opt.get().open()) {
                var img = NativeImage.read(in);
                var mask = new boolean[w * h];
                for (int yy = 0; yy < h; yy++) {
                    for (int xx = 0; xx < w; xx++) {
                        int argb = img.getPixelRGBA(u + xx, v + yy);
                        int a = (argb >>> 24) & 0xFF;
                        mask[yy * w + xx] = a > 8; // treat near-zero alpha as transparent
                    }
                }
                img.close();
                return mask;
            }
        } catch (Exception ignored) {}
        return null;
    }
}
