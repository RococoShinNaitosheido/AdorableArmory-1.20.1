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
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class DimensionalSlashScreenShader {
    private static final float CENTER_X = 0.5f;
    private static final float CENTER_Y = 0.5f;
    private static final int MIN_SHARDS = 40;
    private static final int MAX_SHARDS = 200;
    private static final int SHARD_BUFFER_SIZE = 262144;
    private static final int FULLSCREEN_BUFFER_SIZE = 256;
    private static final int SCREEN_FX_FINAL_OVERLAY = 1;
    private static final ReusableBufferBuilder GLOW_BUFFER = new ReusableBufferBuilder(SHARD_BUFFER_SIZE);
    private static final ReusableBufferBuilder SHARD_BUFFER = new ReusableBufferBuilder(SHARD_BUFFER_SIZE);
    private static final ReusableBufferBuilder CRACK_BUFFER = new ReusableBufferBuilder(SHARD_BUFFER_SIZE);
    private static final ReusableBufferBuilder SCREEN_FX_BUFFER = new ReusableBufferBuilder(FULLSCREEN_BUFFER_SIZE);

    private static ShaderInstance shardShader;
    private static Uniform screenSizeUniform;
    private static Uniform texelSizeUniform;
    private static Uniform progressUniform;
    private static Uniform chromaUniform;
    private static Uniform edgeUniform;
    private static Uniform mirrorUniform;
    private static ShaderInstance screenFxShader;
    private static Uniform fxTexelSizeUniform;
    private static Uniform fxProgressUniform;
    private static Uniform fxTimeUniform;
    private static Uniform fxChromaStrengthUniform;
    private static Uniform fxChromaPullUniform;
    private static Uniform fxUomStrengthUniform;
    private static Uniform fxUomRateUniform;
    private static Uniform fxUomEdgeWeightUniform;
    private static Uniform fxUomContrastUniform;
    private static Uniform fxUomContractionUniform;
    private static Uniform fxUomRadialBlurUniform;
    private static Uniform fxUomBlurThresholdUniform;
    private static Uniform fxUomBeamIntensityUniform;
    private static Uniform fxUomThresholdStrengthUniform;
    private static Uniform fxUomCenterFlashUniform;
    private static Uniform fxUomInvertStrobeUniform;
    private static Uniform fxUomMonoStrobeUniform;
    private static Uniform fxUomWhiteFlashUniform;
    private static Uniform fxUomVignetteStrengthUniform;
    private static Uniform fxEdgeStartUniform;
    private static Uniform fxEdgeEndUniform;

    private static TextureTarget frozenTarget;
    private static TextureTarget postTarget;
    private static boolean pendingCapture;
    private static boolean active;
    private static long pendingSeed;
    private static List<BreakLine> pendingBreakLines = List.of(BreakLine.horizontal());
    private static List<BreakLine> activeBreakLines = List.of(BreakLine.horizontal());
    private static int ageTicks;
    private static int ticksLeft;
    private static int targetWidth = -1;
    private static int targetHeight = -1;
    private static final List<GlassShard> shards = new ArrayList<>();

    private DimensionalSlashScreenShader() {}

    public static void shaderLoaded(ShaderInstance shader) {
        shardShader = shader;
        screenSizeUniform = shader.getUniform("ScreenSize");
        texelSizeUniform = shader.getUniform("TexelSize");
        progressUniform = shader.getUniform("Progress");
        chromaUniform = shader.getUniform("ChromaStrength");
        edgeUniform = shader.getUniform("EdgeVisibility");
        mirrorUniform = shader.getUniform("MirrorStrength");
    }

    public static void screenFxShaderLoaded(ShaderInstance shader) {
        screenFxShader = shader;
        fxTexelSizeUniform = shader.getUniform("TexelSize");
        fxProgressUniform = shader.getUniform("Progress");
        fxTimeUniform = shader.getUniform("Time");
        fxChromaStrengthUniform = shader.getUniform("ChromaStrength");
        fxChromaPullUniform = shader.getUniform("ChromaPull");
        fxUomStrengthUniform = shader.getUniform("UomStrength");
        fxUomRateUniform = shader.getUniform("UomRate");
        fxUomEdgeWeightUniform = shader.getUniform("UomEdgeWeight");
        fxUomContrastUniform = shader.getUniform("UomContrast");
        fxUomContractionUniform = shader.getUniform("UomContraction");
        fxUomRadialBlurUniform = shader.getUniform("UomRadialBlur");
        fxUomBlurThresholdUniform = shader.getUniform("UomBlurThreshold");
        fxUomBeamIntensityUniform = shader.getUniform("UomBeamIntensity");
        fxUomThresholdStrengthUniform = shader.getUniform("UomThresholdStrength");
        fxUomCenterFlashUniform = shader.getUniform("UomCenterFlash");
        fxUomInvertStrobeUniform = shader.getUniform("UomInvertStrobe");
        fxUomMonoStrobeUniform = shader.getUniform("UomMonoStrobe");
        fxUomWhiteFlashUniform = shader.getUniform("UomWhiteFlash");
        fxUomVignetteStrengthUniform = shader.getUniform("UomVignetteStrength");
        fxEdgeStartUniform = shader.getUniform("EdgeStart");
        fxEdgeEndUniform = shader.getUniform("EdgeEnd");
    }

    static void trigger(long seed) {
        pendingSeed = seed;
        pendingCapture = true;
        pendingBreakLines = List.of(BreakLine.horizontal());
        activeBreakLines = List.of(BreakLine.horizontal());
        active = false;
        ageTicks = 0;
        ticksLeft = DimensionalSlashTuning.ScreenBreak.SHARD_MAX_LIFETIME_TICKS;
        shards.clear();
    }

    static void configurePendingBreakLine(float x0, float y0, float x1, float y1) {
        configurePendingBreakLines(List.of(new ScreenBreakLine(x0, y0, x1, y1)));
    }

    static void configurePendingBreakLines(List<ScreenBreakLine> lines) {
        if (!pendingCapture) return;
        if (lines == null || lines.isEmpty()) return;

        List<BreakLine> projected = new ArrayList<>(Math.min(lines.size(), DimensionalSlashTuning.WorldSlash.MAX_SLASHES));
        for (ScreenBreakLine line : lines) {
            if (projected.size() >= DimensionalSlashTuning.WorldSlash.MAX_SLASHES) break;
            BreakLine breakLine = BreakLine.from(line.x0, line.y0, line.x1, line.y1);
            if (breakLine != null) {
                projected.add(breakLine);
            }
        }

        pendingBreakLines = projected.isEmpty() ? List.of(BreakLine.horizontal()) : List.copyOf(projected);
    }

    static void capturePending(RenderTarget main) {
        if (pendingCapture && shardShader != null) {
            captureMainTarget(main, pendingSeed);
        }
    }

    static void tick() {
        if (pendingCapture && !active) return;
        if (!active) return;

        ageTicks++;
        ticksLeft--;
        for (GlassShard shard : shards) {
            shard.tick(ageTicks);
        }

        if (ticksLeft <= 0 || allShardsDone()) {
            clear();
        }
    }

    static void clear() {
        pendingCapture = false;
        active = false;
        pendingBreakLines = List.of(BreakLine.horizontal());
        activeBreakLines = List.of(BreakLine.horizontal());
        ageTicks = 0;
        ticksLeft = 0;
        shards.clear();
        disposeFrozenTarget();
        disposePostTarget();
    }

    private static boolean allShardsDone() {
        if (targetWidth <= 0 || targetHeight <= 0 || ageTicks < DimensionalSlashTuning.ScreenBreak.DURATION_TICKS) {
            return false;
        }

        for (GlassShard shard : shards) {
            if (!shard.isDone(ageTicks, targetWidth, targetHeight)) {
                return false;
            }
        }
        return true;
    }

    static void renderPost(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || shardShader == null) {
            if (mc.level == null) clear();
            return;
        }

        RenderTarget main = mc.getMainRenderTarget();
        capturePending(main);

        if (!active || frozenTarget == null || shards.isEmpty()) return;

        float renderAge = ageTicks + partialTick;
        renderShards(main, renderAge);
        renderScreenFx(main, renderAge, SCREEN_FX_FINAL_OVERLAY);
    }

    private static void captureMainTarget(RenderTarget main, long seed) {
        ensureFrozenTarget(main.width, main.height);
        if (frozenTarget == null) return;

        blitColorOnly(main, frozenTarget);
        main.bindWrite(true);
        RenderSystem.viewport(0, 0, main.width, main.height);

        targetWidth = main.width;
        targetHeight = main.height;
        activeBreakLines = pendingBreakLines.isEmpty() ? List.of(BreakLine.horizontal()) : List.copyOf(pendingBreakLines);
        generateShards(seed, main.width, main.height);
        pendingCapture = false;
        active = true;
        ageTicks = 0;
        ticksLeft = DimensionalSlashTuning.ScreenBreak.SHARD_MAX_LIFETIME_TICKS;
    }

    private static void ensureFrozenTarget(int width, int height) {
        if (frozenTarget == null) {
            frozenTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            frozenTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        } else if (frozenTarget.width != width || frozenTarget.height != height) {
            frozenTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void disposeFrozenTarget() {
        if (frozenTarget != null) {
            frozenTarget.destroyBuffers();
            frozenTarget = null;
            targetWidth = -1;
            targetHeight = -1;
        }
    }

    private static void ensurePostTarget(int width, int height) {
        if (postTarget == null) {
            postTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            postTarget.setClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        } else if (postTarget.width != width || postTarget.height != height) {
            postTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private static void disposePostTarget() {
        if (postTarget != null) {
            postTarget.destroyBuffers();
            postTarget = null;
        }
    }

    private static void blitColorOnly(RenderTarget src, RenderTarget dst) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, src.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dst.frameBufferId);
        GL30.glBlitFramebuffer(0, 0, src.width, src.height, 0, 0, dst.width, dst.height, GL30.GL_COLOR_BUFFER_BIT, GL30.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static void generateShards(long seed, int width, int height) {
        shards.clear();
        Random random = new Random(seed ^ 0xD15EA5E5C4EEL);
        int count = Mth.clamp(Math.round(DimensionalSlashTuning.ScreenBreak.SHARD_CELLS * DimensionalSlashTuning.Quick.GLASS_SHARD_COUNT_SCALE), MIN_SHARDS, MAX_SHARDS);
        float aspect = width / (float) Math.max(height, 1);

        List<Point> sites = new ArrayList<>(count);
        addBreakLineSites(sites, count, random, activeBreakLines, width, height);
        for (int i = sites.size(); i < count; i++) {
            sites.add(randomSite(i, count, random, activeBreakLines, width, height));
        }

        for (int i = 0; i < sites.size(); i++) {
            List<Point> polygon = initialScreenPolygon();
            Point site = sites.get(i);
            Point siteAspect = toAspect(site, aspect);

            for (int j = 0; j < sites.size() && !polygon.isEmpty(); j++) {
                if (i == j) continue;
                Point otherAspect = toAspect(sites.get(j), aspect);
                float nx = otherAspect.x - siteAspect.x;
                float ny = otherAspect.y - siteAspect.y;
                float rhs = otherAspect.x * otherAspect.x + otherAspect.y * otherAspect.y - siteAspect.x * siteAspect.x - siteAspect.y * siteAspect.y;
                float a = 2.0f * nx * aspect;
                float b = 2.0f * ny;
                float c = -rhs;
                polygon = clipPolygon(polygon, a, b, c);
            }

            if (polygon.size() < 3 || Math.abs(area(polygon)) < 0.00004f) continue;
            shards.add(new GlassShard(polygon, random, width, height, activeBreakLines));
        }

        shards.sort(Comparator.comparingDouble(shard -> shard.depth));
    }

    private static void addBreakLineSites(List<Point> sites, int count, Random random, List<BreakLine> breakLines, int width, int height) {
        List<BreakLine> safeLines = breakLines == null || breakLines.isEmpty() ? List.of(BreakLine.horizontal()) : breakLines;
        int lineCount = Math.max(1, safeLines.size());
        int lineSiteBudget = Math.min(count * 7 / 10, lineCount * 20);
        int pairsPerLine = Mth.clamp(lineSiteBudget / Math.max(2, lineCount * 2), 3, 10);
        float minDimension = Math.max(1.0f, Math.min(width, height));

        for (BreakLine breakLine : safeLines) {
            LineMetrics line = breakLine.metrics(width, height);
            float offset = minDimension * 0.014f;
            float alongSpan = Math.max(line.length * 0.94f, minDimension * 0.50f);
            for (int i = 0; i < pairsPerLine && sites.size() + 1 < count; i++) {
                float t = pairsPerLine == 1 ? 0.5f : i / (float) (pairsPerLine - 1);
                float along = (t - 0.5f) * alongSpan;
                float alongJitter = (random.nextFloat() - 0.5f) * minDimension * 0.012f;
                float sideJitter = (random.nextFloat() - 0.5f) * minDimension * 0.0025f;
                addScreenSite(sites, line.cx + line.ux * (along + alongJitter) + line.nx * (offset + sideJitter), line.cy + line.uy * (along + alongJitter) + line.ny * (offset + sideJitter), width, height);
                addScreenSite(sites, line.cx + line.ux * (along - alongJitter) - line.nx * (offset - sideJitter), line.cy + line.uy * (along - alongJitter) - line.ny * (offset - sideJitter), width, height);
            }
        }
    }

    private static void addScreenSite(List<Point> sites, float x, float y, int width, int height) {
        sites.add(new Point(
                Mth.clamp(x / Math.max(width, 1), -0.12f, 1.12f),
                Mth.clamp(y / Math.max(height, 1), -0.12f, 1.12f)
        ));
    }

    private static Point randomSite(int index, int count, Random random, List<BreakLine> breakLines, int width, int height) {
        boolean slashBand = index < count * 0.86f;
        LineMetrics line = selectBreakLine(index, random, breakLines, width, height, slashBand);
        float along;
        float cross;

        if (slashBand) {
            float centerSpread = random.nextFloat() < 0.58f
                    ? (float) Math.pow(random.nextFloat(), 0.72f) * (random.nextBoolean() ? -1.0f : 1.0f)
                    : random.nextFloat() * 2.0f - 1.0f;
            float alongSpan = Math.max(line.length * 0.58f, Math.min(width, height) * 0.42f);
            along = centerSpread * alongSpan;
            cross = (random.nextFloat() + random.nextFloat() - 1.0f) * Math.min(width, height) * 0.046f;
        } else {
            float side = random.nextBoolean() ? -1.0f : 1.0f;
            along = (random.nextFloat() * 2.0f - 1.0f) * Math.max(line.length * 0.72f, Math.max(width, height) * 0.40f);
            cross = side * (0.14f + (float) Math.pow(random.nextFloat(), 0.72f) * 0.50f) * Math.min(width, height);
            cross += (random.nextFloat() - 0.5f) * Math.min(width, height) * 0.065f;
        }

        float x = (line.cx + line.ux * along + line.nx * cross) / Math.max(width, 1);
        float y = (line.cy + line.uy * along + line.ny * cross) / Math.max(height, 1);
        x += (random.nextFloat() - 0.5f) * 0.026f;
        y += (random.nextFloat() - 0.5f) * 0.026f;
        return new Point(Mth.clamp(x, -0.10f, 1.10f), Mth.clamp(y, -0.10f, 1.10f));
    }

    private static LineMetrics selectBreakLine(int index, Random random, List<BreakLine> breakLines, int width, int height, boolean cycle) {
        List<BreakLine> safeLines = breakLines == null || breakLines.isEmpty() ? List.of(BreakLine.horizontal()) : breakLines;
        if (cycle) {
            return safeLines.get(index % safeLines.size()).metrics(width, height);
        }

        float total = 0.0f;
        for (BreakLine line : safeLines) {
            total += Math.max(1.0f, line.metrics(width, height).length);
        }

        float pick = random.nextFloat() * Math.max(total, 1.0f);
        for (BreakLine line : safeLines) {
            LineMetrics metrics = line.metrics(width, height);
            pick -= Math.max(1.0f, metrics.length);
            if (pick <= 0.0f) {
                return metrics;
            }
        }
        return safeLines.get(safeLines.size() - 1).metrics(width, height);
    }

    private static LineMetrics nearestBreakLine(float x, float y, List<BreakLine> breakLines, int width, int height) {
        List<BreakLine> safeLines = breakLines == null || breakLines.isEmpty() ? List.of(BreakLine.horizontal()) : breakLines;
        LineMetrics best = safeLines.get(0).metrics(width, height);
        float bestDistance = distanceToLineSegmentSq(x, y, best);

        for (int i = 1; i < safeLines.size(); i++) {
            LineMetrics candidate = safeLines.get(i).metrics(width, height);
            float distance = distanceToLineSegmentSq(x, y, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static float distanceToLineSegmentSq(float x, float y, LineMetrics line) {
        float dx = x - line.cx;
        float dy = y - line.cy;
        float along = dx * line.ux + dy * line.uy;
        float cross = dx * line.nx + dy * line.ny;
        float outside = Math.max(0.0f, Math.abs(along) - line.length * 0.5f);
        return outside * outside + cross * cross;
    }

    private static List<Point> initialScreenPolygon() {
        List<Point> polygon = new ArrayList<>(4);
        polygon.add(new Point(0.0f, 0.0f));
        polygon.add(new Point(1.0f, 0.0f));
        polygon.add(new Point(1.0f, 1.0f));
        polygon.add(new Point(0.0f, 1.0f));
        return polygon;
    }

    private static Point toAspect(Point point, float aspect) {
        return new Point(point.x * aspect, point.y);
    }

    private static List<Point> clipPolygon(List<Point> input, float a, float b, float c) {
        List<Point> output = new ArrayList<>(input.size() + 1);
        Point previous = input.get(input.size() - 1);
        float previousValue = a * previous.x + b * previous.y + c;
        boolean previousInside = previousValue <= 0.000001f;

        for (Point current : input) {
            float currentValue = a * current.x + b * current.y + c;
            boolean currentInside = currentValue <= 0.000001f;

            if (currentInside) {
                if (!previousInside) {
                    output.add(intersection(previous, current, previousValue, currentValue));
                }
                output.add(current);
            } else if (previousInside) {
                output.add(intersection(previous, current, previousValue, currentValue));
            }

            previous = current;
            previousValue = currentValue;
            previousInside = currentInside;
        }

        return output;
    }

    private static Point intersection(Point a, Point b, float av, float bv) {
        float t = av / (av - bv);
        t = Mth.clamp(t, 0.0f, 1.0f);
        return new Point(Mth.lerp(t, a.x, b.x), Mth.lerp(t, a.y, b.y));
    }

    private static float area(List<Point> polygon) {
        float sum = 0.0f;
        for (int i = 0; i < polygon.size(); i++) {
            Point a = polygon.get(i);
            Point b = polygon.get((i + 1) % polygon.size());
            sum += a.x * b.y - b.x * a.y;
        }
        return sum * 0.5f;
    }

    private static Point centroid(List<Point> polygon) {
        float area2 = 0.0f;
        float cx = 0.0f;
        float cy = 0.0f;

        for (int i = 0; i < polygon.size(); i++) {
            Point a = polygon.get(i);
            Point b = polygon.get((i + 1) % polygon.size());
            float cross = a.x * b.y - b.x * a.y;
            area2 += cross;
            cx += (a.x + b.x) * cross;
            cy += (a.y + b.y) * cross;
        }

        if (Math.abs(area2) < 0.000001f) {
            for (Point point : polygon) {
                cx += point.x;
                cy += point.y;
            }
            float inv = 1.0f / polygon.size();
            return new Point(cx * inv, cy * inv);
        }

        float inv = 1.0f / (3.0f * area2);
        return new Point(cx * inv, cy * inv);
    }

    private static void renderShards(RenderTarget main, float renderAge) {
        int width = main.width;
        int height = main.height;
        if (width <= 0 || height <= 0 || width != targetWidth || height != targetHeight) {
            clear();
            return;
        }
        ensurePostTarget(width, height);
        if (postTarget == null) return;
        blitColorOnly(main, postTarget);
        main.bindWrite(true);
        RenderSystem.viewport(0, 0, width, height);

        RenderSystem.disableScissor();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
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
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder glowBuilder = GLOW_BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            shards.sort(Comparator.comparingDouble(GlassShard::drawOrder));
            for (GlassShard shard : shards) {
                shard.renderGlow(glowBuilder, renderAge, width, height);
            }
            ReusableBufferBuilder.drawWithShaderOrDiscard(glowBuilder);

            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.setShader(() -> shardShader);
            shardShader.setSampler("SceneTex", frozenTarget.getColorTextureId());
            shardShader.setSampler("LiveSceneTex", postTarget.getColorTextureId());
            if (screenSizeUniform != null) screenSizeUniform.set((float) width, (float) height);
            if (texelSizeUniform != null) texelSizeUniform.set(1.0f / width, 1.0f / height);
            if (progressUniform != null) progressUniform.set(Mth.clamp(renderAge / DimensionalSlashTuning.ScreenBreak.DURATION_TICKS, 0.0f, 1.0f));
            if (chromaUniform != null) chromaUniform.set(DimensionalSlashTuning.ScreenBreak.REFRACTION_STRENGTH);
            if (edgeUniform != null) edgeUniform.set(DimensionalSlashTuning.ScreenBreak.EDGE_VISIBILITY);
            if (mirrorUniform != null) mirrorUniform.set(DimensionalSlashTuning.ScreenBreak.SHARD_MIRROR_STRENGTH);

            BufferBuilder builder = SHARD_BUFFER.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR_TEX);

            for (GlassShard shard : shards) {
                shard.render(builder, renderAge, width, height);
            }

            ReusableBufferBuilder.drawWithShaderOrDiscard(builder);

            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder crackBuilder = CRACK_BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (GlassShard shard : shards) {
                shard.renderCrackLight(crackBuilder, renderAge, width, height);
            }
            ReusableBufferBuilder.drawWithShaderOrDiscard(crackBuilder);

            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            main.bindWrite(true);
            RenderSystem.viewport(0, 0, width, height);
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();

            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private static void renderScreenFx(RenderTarget main, float renderAge, int mode) {
        if (screenFxShader == null) return;

        boolean finalOverlay = mode == SCREEN_FX_FINAL_OVERLAY;
        float uom = (1.0f - smooth(Mth.clamp(renderAge / Math.max(1.0f, DimensionalSlashTuning.ScreenBreak.UOM_DURATION_TICKS), 0.0f, 1.0f))) * DimensionalSlashTuning.Quick.UOM_SCREEN_FX_SCALE;
        float chroma = (1.0f - smooth(Mth.clamp(renderAge / Math.max(1.0f, DimensionalSlashTuning.ScreenBreak.RADIAL_CHROMA_DURATION_TICKS), 0.0f, 1.0f))) * DimensionalSlashTuning.Quick.RADIAL_CHROMA_SCALE;
        if (finalOverlay) {
            uom *= 0.78f;
            chroma *= 0.86f;
        }
        if (uom <= 0.001f && chroma <= 0.001f) return;

        ensurePostTarget(main.width, main.height);
        if (postTarget == null) return;

        blitColorOnly(main, postTarget);
        main.bindWrite(false);
        RenderSystem.viewport(0, 0, main.width, main.height);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        RenderSystem.backupProjectionMatrix();
        Matrix4f projection = new Matrix4f().setOrtho(0.0f, (float) main.width, (float) main.height, 0.0f, 1000.0f, 3000.0f);
        RenderSystem.setProjectionMatrix(projection, VertexSorting.ORTHOGRAPHIC_Z);

        var modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        modelView.translate(0.0f, 0.0f, -2000.0f);
        RenderSystem.applyModelViewMatrix();

        try {
            RenderSystem.setShader(() -> screenFxShader);
            screenFxShader.setSampler("DiffuseSampler", postTarget.getColorTextureId());
            if (fxTexelSizeUniform != null) fxTexelSizeUniform.set(1.0f / main.width, 1.0f / main.height);
            if (fxProgressUniform != null) fxProgressUniform.set(Mth.clamp(renderAge / DimensionalSlashTuning.ScreenBreak.DURATION_TICKS, 0.0f, 1.0f));
            if (fxTimeUniform != null) fxTimeUniform.set(renderAge / 20.0f);
            if (fxChromaStrengthUniform != null) fxChromaStrengthUniform.set(DimensionalSlashTuning.ScreenBreak.RADIAL_CHROMA_STRENGTH * chroma);
            if (fxChromaPullUniform != null) fxChromaPullUniform.set(DimensionalSlashTuning.ScreenBreak.RADIAL_CHROMA_PULL * chroma);
            if (fxUomStrengthUniform != null) fxUomStrengthUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_STRENGTH * uom);
            if (fxUomRateUniform != null) fxUomRateUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_RATE);
            if (fxUomEdgeWeightUniform != null) fxUomEdgeWeightUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_EDGE_WEIGHT);
            if (fxUomContrastUniform != null) fxUomContrastUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_CONTRAST * (finalOverlay ? 0.38f : 1.0f));
            if (fxUomContractionUniform != null) fxUomContractionUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_CONTRACTION * uom * (finalOverlay ? 0.52f : 1.0f));
            if (fxUomRadialBlurUniform != null) fxUomRadialBlurUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_RADIAL_BLUR * uom * (finalOverlay ? 0.56f : 1.0f));
            if (fxUomBlurThresholdUniform != null) fxUomBlurThresholdUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_BLUR_THRESHOLD);
            if (fxUomBeamIntensityUniform != null) fxUomBeamIntensityUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_BEAM_INTENSITY * uom * (finalOverlay ? 0.24f : 1.0f));
            if (fxUomThresholdStrengthUniform != null) fxUomThresholdStrengthUniform.set(finalOverlay ? 0.0f : DimensionalSlashTuning.ScreenBreak.UOM_THRESHOLD_STRENGTH * uom);
            if (fxUomCenterFlashUniform != null) fxUomCenterFlashUniform.set(DimensionalSlashTuning.ScreenBreak.UOM_CENTER_FLASH * uom * (finalOverlay ? 0.10f : 1.0f));
            if (fxUomInvertStrobeUniform != null) fxUomInvertStrobeUniform.set(finalOverlay ? 0.0f : DimensionalSlashTuning.ScreenBreak.UOM_INVERT_STROBE * uom);
            if (fxUomMonoStrobeUniform != null) fxUomMonoStrobeUniform.set(finalOverlay ? 0.0f : DimensionalSlashTuning.ScreenBreak.UOM_MONO_STROBE * uom);
            if (fxUomWhiteFlashUniform != null) fxUomWhiteFlashUniform.set(finalOverlay ? 0.0f : DimensionalSlashTuning.ScreenBreak.UOM_WHITE_FLASH * uom);
            if (fxUomVignetteStrengthUniform != null) fxUomVignetteStrengthUniform.set(finalOverlay ? DimensionalSlashTuning.ScreenBreak.UOM_VIGNETTE_STRENGTH : 0.0f);
            if (fxEdgeStartUniform != null) fxEdgeStartUniform.set(DimensionalSlashTuning.ScreenBreak.RADIAL_CHROMA_EDGE_START);
            if (fxEdgeEndUniform != null) fxEdgeEndUniform.set(DimensionalSlashTuning.ScreenBreak.RADIAL_CHROMA_EDGE_END);

            BufferBuilder builder = SCREEN_FX_BUFFER.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            builder.vertex(0.0f, (float) main.height, 0.0f).uv(0.0f, 0.0f).endVertex();
            builder.vertex((float) main.width, (float) main.height, 0.0f).uv(1.0f, 0.0f).endVertex();
            builder.vertex((float) main.width, 0.0f, 0.0f).uv(1.0f, 1.0f).endVertex();
            builder.vertex(0.0f, 0.0f, 0.0f).uv(0.0f, 1.0f).endVertex();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            modelView.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            main.bindWrite(false);
            RenderSystem.viewport(0, 0, main.width, main.height);
        }
    }

    private static float smooth(float value) {
        value = Mth.clamp(value, 0.0f, 1.0f);
        return value * value * (3.0f - 2.0f * value);
    }

    private static float shardFade(float age) {
        return 1.0f - smooth((age - DimensionalSlashTuning.ScreenBreak.SHARD_FADE_START_TICKS) / Math.max(1.0f, DimensionalSlashTuning.ScreenBreak.SHARD_FADE_TICKS));
    }

    private static float stagedCrackCoverage(float age) {
        float launchStart = Math.max(1.0f, DimensionalSlashTuning.ScreenBreak.SHARD_LAUNCH_START_TICKS);
        float stage30End = 3.0f;
        float stage30HoldEnd = stage30End + 5.0f;
        float stage60End = stage30HoldEnd + 3.0f;
        float stage60HoldEnd = stage60End + 3.0f;
        if (age <= 0.0f) {
            return 0.0f;
        }
        if (age < stage30End) {
            return 0.30f * smooth(age / stage30End);
        }
        if (age < stage30HoldEnd) {
            return 0.30f;
        }
        if (age < stage60End) {
            return 0.30f + 0.30f * smooth((age - stage30HoldEnd) / Math.max(1.0f, stage60End - stage30HoldEnd));
        }
        if (age < stage60HoldEnd) {
            return 0.60f;
        }
        if (age < launchStart) {
            return 0.60f + 0.38f * smooth((age - stage60HoldEnd) / Math.max(1.0f, launchStart - stage60HoldEnd));
        }
        return 1.0f;
    }

    private record Point(float x, float y) {}

    static record ScreenBreakLine(float x0, float y0, float x1, float y1) {}

    private record BreakLine(Point start, Point end) {
        private static BreakLine horizontal() {
            return new BreakLine(new Point(0.12f, CENTER_Y), new Point(0.88f, CENTER_Y));
        }

        private static BreakLine from(float x0, float y0, float x1, float y1) {
            Point start = new Point(Mth.clamp(x0, -0.35f, 1.35f), Mth.clamp(y0, -0.35f, 1.35f));
            Point end = new Point(Mth.clamp(x1, -0.35f, 1.35f), Mth.clamp(y1, -0.35f, 1.35f));
            float dx = end.x - start.x;
            float dy = end.y - start.y;
            if (dx * dx + dy * dy < 0.035f * 0.035f) {
                return null;
            }
            return new BreakLine(start, end);
        }

        private LineMetrics metrics(int width, int height) {
            float ax = start.x * width;
            float ay = start.y * height;
            float bx = end.x * width;
            float by = end.y * height;
            float dx = bx - ax;
            float dy = by - ay;
            float length = Mth.sqrt(dx * dx + dy * dy);
            if (length < 1.0f) {
                return new LineMetrics(width * 0.5f, height * 0.5f, 1.0f, 0.0f, 0.0f, 1.0f, width * 0.76f);
            }
            float ux = dx / length;
            float uy = dy / length;
            return new LineMetrics((ax + bx) * 0.5f, (ay + by) * 0.5f, ux, uy, -uy, ux, length);
        }
    }

    private record LineMetrics(float cx, float cy, float ux, float uy, float nx, float ny, float length) {}

    private static final class GlassShard {
        private final List<Point> polygon;
        private final Point center;
        private final float baseX;
        private final float baseY;
        private final float velocityX;
        private final float velocityY;
        private final float velocityZ;
        private final float angularVelocityX;
        private final float angularVelocityY;
        private final float angularVelocityZ;
        private final float depth;
        private final float lift;
        private final float shade;
        private final float edge;
        private final float slashInfluence;
        private final float crackOrder;
        private final int revealTick;
        private final int launchTick;
        private float positionX;
        private float positionY;
        private float positionZ;
        private float rotationX;
        private float rotationY;
        private float rotationZ;
        private float scale = 1.0f;
        private float currentVelocityX;
        private float currentVelocityY;
        private float currentVelocityZ;
        private float currentAngularVelocityX;
        private float currentAngularVelocityY;
        private float currentAngularVelocityZ;
        private boolean launched;

        private GlassShard(List<Point> polygon, Random random, int width, int height, List<BreakLine> breakLines) {
            this.polygon = List.copyOf(polygon);
            this.center = centroid(polygon);
            this.baseX = center.x * width;
            this.baseY = center.y * height;
            this.positionX = baseX;
            this.positionY = baseY;

            LineMetrics line = nearestBreakLine(baseX, baseY, breakLines, width, height);
            float dx = baseX - line.cx;
            float dy = baseY - line.cy;
            float along = dx * line.ux + dy * line.uy;
            float cross = dx * line.nx + dy * line.ny;
            float horizontalOrder = Mth.clamp(Math.abs(along) / Math.max(line.length * 0.54f, Math.min(width, height) * 0.38f), 0.0f, 1.0f);
            float bandOrder = Mth.clamp(Math.abs(cross) / Math.max(Math.min(width, height) * 0.42f, 1.0f), 0.0f, 1.0f);
            float screenCenterX = width * CENTER_X;
            float screenCenterY = height * CENTER_Y;
            float radialDx = baseX - screenCenterX;
            float radialDy = baseY - screenCenterY;
            float maxRadialDx = Math.max(screenCenterX, width - screenCenterX);
            float maxRadialDy = Math.max(screenCenterY, height - screenCenterY);
            float maxRadialDistance = Math.max(1.0f, Mth.sqrt(maxRadialDx * maxRadialDx + maxRadialDy * maxRadialDy));
            float radialOrder = Mth.clamp(Mth.sqrt(radialDx * radialDx + radialDy * radialDy) / maxRadialDistance, 0.0f, 1.0f);

            float slashInfluence = 1.0f - bandOrder;
            this.slashInfluence = slashInfluence;
            this.crackOrder = radialOrder;
            float horizontalSign = Math.abs(along) > 0.001f ? Math.signum(along) : (random.nextBoolean() ? -1.0f : 1.0f);
            float verticalSign = Math.abs(cross) > Math.min(width, height) * 0.035f ? Math.signum(cross) : (random.nextBoolean() ? -1.0f : 1.0f);
            float pushAlong = horizontalSign * (0.24f + horizontalOrder * 0.52f) + (random.nextFloat() - 0.5f) * 0.26f;
            float pushNormal = verticalSign * (0.96f + slashInfluence * 0.42f) + (random.nextFloat() - 0.5f) * 0.18f;
            float pushX = line.ux * pushAlong + line.nx * pushNormal;
            float pushY = line.uy * pushAlong + line.ny * pushNormal;
            float pushLength = Math.max(0.001f, Mth.sqrt(pushX * pushX + pushY * pushY));
            float nx = pushX / pushLength;
            float ny = pushY / pushLength;
            float tangent = (random.nextFloat() - 0.5f) * (0.22f + slashInfluence * 0.24f);
            float tx = -ny;
            float ty = nx;

            float moveScale = DimensionalSlashTuning.Quick.GLASS_SHARD_EXPLOSION_SCALE;
            float speed = Math.min(width, height) * (0.0044f + random.nextFloat() * 0.0058f + slashInfluence * 0.0088f) * moveScale;
            this.velocityX = (nx + tx * tangent) * speed + horizontalSign * Math.min(width, height) * (0.0009f + horizontalOrder * 0.0012f) * moveScale;
            this.velocityY = (ny + ty * tangent) * speed - (0.12f + slashInfluence * 0.28f) * moveScale;
            this.velocityZ = (2.0f + random.nextFloat() * 4.2f + slashInfluence * 5.0f) * moveScale * DimensionalSlashTuning.ScreenBreak.SHARD_DEPTH_SPEED;
            float tumbleScale = DimensionalSlashTuning.ScreenBreak.SHARD_TUMBLE_SPEED * DimensionalSlashTuning.Quick.GLASS_SHARD_TUMBLE_SCALE;
            this.angularVelocityX = ((random.nextFloat() - 0.5f) * 0.180f + Math.copySign(0.045f, verticalSign)) * (0.65f + slashInfluence) * tumbleScale;
            this.angularVelocityY = ((random.nextFloat() - 0.5f) * 0.150f + Math.copySign(0.035f, horizontalSign)) * (0.72f + slashInfluence) * tumbleScale;
            this.angularVelocityZ = ((random.nextFloat() - 0.5f) * 0.070f + Math.copySign(0.020f, horizontalSign)) * (0.70f + slashInfluence * 0.72f) * tumbleScale;
            this.depth = random.nextFloat();
            this.lift = 0.34f + random.nextFloat() * 0.58f;
            this.shade = 0.04f + random.nextFloat() * 0.16f;
            this.edge = 0.70f + random.nextFloat() * 0.30f;
            this.rotationX = (random.nextFloat() - 0.5f) * 0.20f;
            this.rotationY = (random.nextFloat() - 0.5f) * 0.20f;
            this.rotationZ = (random.nextFloat() - 0.5f) * 0.12f;
            this.revealTick = Math.round(radialOrder * 2.0f + random.nextFloat() * 0.6f);
            int baseLaunch = Math.round(DimensionalSlashTuning.ScreenBreak.SHARD_LAUNCH_START_TICKS);
            this.launchTick = baseLaunch + Math.round(
                    radialOrder * DimensionalSlashTuning.ScreenBreak.SHARD_LAUNCH_SPREAD_TICKS
                            + random.nextFloat() * 0.8f
            );
        }

        private void tick(int age) {
            if (age < launchTick) return;

            if (!launched) {
                launched = true;
                currentVelocityX = velocityX;
                currentVelocityY = velocityY;
                currentVelocityZ = velocityZ;
                currentAngularVelocityX = angularVelocityX;
                currentAngularVelocityY = angularVelocityY;
                currentAngularVelocityZ = angularVelocityZ;
            }

            float fallAge = age - launchTick;
            float fall = smooth((fallAge - 5.0f) / 18.0f);

            positionX += currentVelocityX;
            positionY += currentVelocityY;
            positionZ += currentVelocityZ;
            rotationX += currentAngularVelocityX;
            rotationY += currentAngularVelocityY;
            rotationZ += currentAngularVelocityZ;
            scale = Math.min(1.30f, scale + 0.0030f * lift);

            float gravity = DimensionalSlashTuning.ScreenBreak.SHARD_GRAVITY
                    * DimensionalSlashTuning.Quick.GLASS_SHARD_GRAVITY_SCALE
                    * (0.82f + lift * 0.42f)
                    * fall;
            currentVelocityX *= Mth.lerp(fall, 0.986f, 0.972f);
            currentVelocityY = currentVelocityY * 0.996f + gravity;
            currentVelocityZ = currentVelocityZ * Mth.lerp(fall, 0.962f, 0.928f) - gravity * 0.0024f;
            currentAngularVelocityX *= 0.993f;
            currentAngularVelocityY *= 0.993f;
            currentAngularVelocityZ *= 0.990f;
        }

        private boolean isDone(int age, int width, int height) {
            return shardFade(age) <= 0.002f;
        }

        private double drawOrder() {
            return positionZ + depth * 8.0f;
        }

        private void render(BufferBuilder builder, float age, int width, int height) {
            float reveal = crackReveal(age) * breakOpenReveal(age);
            if (reveal <= 0.002f) return;

            float explode = smooth((age - launchTick) / 13.0f);
            float fade = shardFade(age);
            float partial = age - (int) age;
            float renderRotationX = rotationX + (launched ? currentAngularVelocityX * partial : 0.0f);
            float renderRotationY = rotationY + (launched ? currentAngularVelocityY * partial : 0.0f);
            float renderRotationZ = rotationZ + (launched ? currentAngularVelocityZ * partial : 0.0f);
            float facing = facing(renderRotationX, renderRotationY);
            float alpha = reveal * fade * Mth.lerp(explode, 0.12f, 0.42f) * (0.38f + facing * 0.32f);
            if (alpha <= 0.003f) return;

            float jitter = preLaunchJitter(age, reveal, explode);
            float jitterX = Mth.sin(age * 0.42f + depth * 17.0f) * jitter;
            float jitterY = Mth.cos(age * 0.37f + depth * 13.0f) * jitter;
            float renderX = positionX + (launched ? currentVelocityX * partial : 0.0f) + jitterX;
            float renderY = positionY + (launched ? currentVelocityY * partial : 0.0f) + jitterY;
            float renderZ = positionZ + (launched ? currentVelocityZ * partial : 0.0f);
            float renderScale = scale + explode * lift * 0.045f;
            float angleShade = (1.0f - facing) * 0.42f;
            float materialShade = Mth.clamp(shade + angleShade + Math.max(renderZ, 0.0f) * 0.00035f, 0.0f, 1.0f);
            float materialEdge = Mth.clamp(edge + (1.0f - facing) * 0.24f, 0.0f, 1.0f);
            float materialLift = Mth.clamp(lift * (0.76f + explode * 0.28f), 0.0f, 1.0f);

            float centerU = center.x;
            float centerV = 1.0f - center.y;
            float centerAlpha = alpha * Mth.lerp(explode, 0.48f, 0.64f);
            float pixelScale = Math.max(0.72f, Math.min(width, height) / 720.0f);
            float thickness = DimensionalSlashTuning.ScreenBreak.SHARD_THICKNESS_PIXELS
                    * DimensionalSlashTuning.Quick.GLASS_SHARD_THICKNESS_SCALE
                    * pixelScale
                    * (0.78f + lift * 0.44f)
                    * (0.82f + explode * 0.18f);
            float frontZ = thickness * 0.5f;
            float backZ = -frontZ;
            float backAlpha = alpha * 0.22f;
            float sideAlpha = alpha * Mth.lerp(explode, 0.32f, 0.58f);
            float sideShade = Mth.clamp(materialShade + 0.42f, 0.0f, 1.0f);
            float sideLift = Mth.clamp(materialLift + 0.18f, 0.0f, 1.0f);

            for (int i = 0; i < polygon.size(); i++) {
                Point a = polygon.get(i);
                Point b = polygon.get((i + 1) % polygon.size());
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, center, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, 0.08f, materialShade, materialLift, centerAlpha, centerU, centerV);
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, a, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, materialEdge, materialShade, materialLift, alpha, a.x, 1.0f - a.y);
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, b, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, materialEdge, materialShade, materialLift, alpha, b.x, 1.0f - b.y);

                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, center, backZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, 0.18f, sideShade, materialLift, backAlpha, centerU, centerV);
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, b, backZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, materialEdge, sideShade, materialLift, backAlpha, b.x, 1.0f - b.y);
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, a, backZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, materialEdge, sideShade, materialLift, backAlpha, a.x, 1.0f - a.y);

                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, a, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, 1.0f, sideShade, sideLift, sideAlpha, a.x, 1.0f - a.y);
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, a, backZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, 1.0f, sideShade, sideLift, sideAlpha, a.x, 1.0f - a.y);
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, b, backZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, 1.0f, sideShade, sideLift, sideAlpha, b.x, 1.0f - b.y);

                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, a, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, 1.0f, sideShade, sideLift, sideAlpha, a.x, 1.0f - a.y);
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, b, backZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, 1.0f, sideShade, sideLift, sideAlpha, b.x, 1.0f - b.y);
                addVertex(builder, renderX, renderY, renderZ, baseX, baseY, b, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height, 1.0f, sideShade, sideLift, sideAlpha, b.x, 1.0f - b.y);
            }
        }

        private boolean renderGlow(BufferBuilder builder, float age, int width, int height) {
            float reveal = crackReveal(age);
            if (reveal <= 0.002f) return false;

            float explode = smooth((age - launchTick) / 13.0f);
            float fade = shardFade(age);
            float partial = age - (int) age;
            float renderRotationX = rotationX + (launched ? currentAngularVelocityX * partial : 0.0f);
            float renderRotationY = rotationY + (launched ? currentAngularVelocityY * partial : 0.0f);
            float renderRotationZ = rotationZ + (launched ? currentAngularVelocityZ * partial : 0.0f);
            float facing = facing(renderRotationX, renderRotationY);
            float lineBoost = 0.72f + slashInfluence * 0.50f;
            float alpha = reveal * fade * Mth.lerp(explode, 0.26f, 0.92f) * (0.54f + facing * 0.46f) * DimensionalSlashTuning.ScreenBreak.SHARD_BLOOM_ALPHA * lineBoost;
            if (alpha <= 0.003f) return false;

            float jitter = preLaunchJitter(age, reveal, explode);
            float jitterX = Mth.sin(age * 0.42f + depth * 17.0f) * jitter;
            float jitterY = Mth.cos(age * 0.37f + depth * 13.0f) * jitter;
            float renderX = positionX + (launched ? currentVelocityX * partial : 0.0f) + jitterX;
            float renderY = positionY + (launched ? currentVelocityY * partial : 0.0f) + jitterY;
            float renderZ = positionZ + (launched ? currentVelocityZ * partial : 0.0f);
            float renderScale = scale + explode * lift * 0.045f;

            float pixelScale = Math.max(0.72f, Math.min(width, height) / 720.0f);
            float thickness = DimensionalSlashTuning.ScreenBreak.SHARD_THICKNESS_PIXELS
                    * DimensionalSlashTuning.Quick.GLASS_SHARD_THICKNESS_SCALE
                    * pixelScale
                    * (0.78f + lift * 0.44f)
                    * (0.82f + explode * 0.18f);
            float frontZ = thickness * 0.5f;
            float glowWidth = DimensionalSlashTuning.ScreenBreak.SHARD_BLOOM_PIXELS * pixelScale * (0.72f + lift * 0.48f) * (0.82f + explode * 0.18f);
            float blurTicks = launched ? Math.min(DimensionalSlashTuning.ScreenBreak.SHARD_MOTION_BLUR_TICKS, Math.max(0.0f, age - launchTick)) : 0.0f;
            boolean blurActive = blurTicks > 0.05f;
            float trailX = renderX;
            float trailY = renderY;
            float trailZ = renderZ;
            float trailRotationX = renderRotationX;
            float trailRotationY = renderRotationY;
            float trailRotationZ = renderRotationZ;
            float trailScale = renderScale;
            float trailFrontZ = frontZ;
            if (blurActive) {
                float trailAge = age - blurTicks;
                float trailReveal = crackReveal(trailAge);
                float trailExplode = smooth((trailAge - launchTick) / 13.0f);
                float trailJitter = preLaunchJitter(trailAge, trailReveal, trailExplode);
                trailX = positionX + currentVelocityX * (partial - blurTicks) + Mth.sin(trailAge * 0.42f + depth * 17.0f) * trailJitter;
                trailY = positionY + currentVelocityY * (partial - blurTicks) + Mth.cos(trailAge * 0.37f + depth * 13.0f) * trailJitter;
                trailZ = positionZ + currentVelocityZ * (partial - blurTicks);
                trailRotationX = renderRotationX - currentAngularVelocityX * blurTicks;
                trailRotationY = renderRotationY - currentAngularVelocityY * blurTicks;
                trailRotationZ = renderRotationZ - currentAngularVelocityZ * blurTicks;
                trailScale = Math.max(0.72f, renderScale - 0.0030f * lift * blurTicks);
                float trailThickness = DimensionalSlashTuning.ScreenBreak.SHARD_THICKNESS_PIXELS
                        * DimensionalSlashTuning.Quick.GLASS_SHARD_THICKNESS_SCALE
                        * pixelScale
                        * (0.78f + lift * 0.44f)
                        * (0.82f + trailExplode * 0.18f);
                trailFrontZ = trailThickness * 0.5f;
            }

            boolean emitted = false;
            for (int i = 0; i < polygon.size(); i++) {
                Point a = polygon.get(i);
                Point b = polygon.get((i + 1) % polygon.size());
                ProjectedPoint pa = projectPoint(renderX, renderY, renderZ, baseX, baseY, a, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height);
                ProjectedPoint pb = projectPoint(renderX, renderY, renderZ, baseX, baseY, b, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height);
                if (blurActive) {
                    ProjectedPoint ta = projectPoint(trailX, trailY, trailZ, baseX, baseY, a, trailFrontZ, trailRotationX, trailRotationY, trailRotationZ, trailScale, width, height);
                    ProjectedPoint tb = projectPoint(trailX, trailY, trailZ, baseX, baseY, b, trailFrontZ, trailRotationX, trailRotationY, trailRotationZ, trailScale, width, height);
                    float sweep = Math.max(screenDistance(pa, ta), screenDistance(pb, tb));
                    if (sweep > 0.25f) {
                        float motionAlpha = alpha
                                * DimensionalSlashTuning.ScreenBreak.SHARD_MOTION_BLUR_ALPHA
                                * Mth.clamp(sweep / Math.max(18.0f * pixelScale, 1.0f), 0.16f, 1.0f);
                        addGlowQuad(builder, ta, tb, pb, pa, 0.34f, 0.72f, 1.0f, motionAlpha * 0.22f);
                        addGlowQuad(builder, ta, tb, pb, pa, 1.0f, 0.55f, 1.0f, motionAlpha * 0.11f);
                    }
                }
                addGlowRibbon(builder, pa, pb, glowWidth * 1.45f, 0.42f, 0.78f, 1.0f, alpha * 0.16f);
                addGlowRibbon(builder, pa, pb, glowWidth * 0.58f, 1.0f, 0.72f, 1.0f, alpha * 0.14f);
                emitted = true;
            }
            return emitted;
        }

        private boolean renderCrackLight(BufferBuilder builder, float age, int width, int height) {
            float reveal = crackReveal(age);
            if (reveal <= 0.002f) return false;

            float explode = smooth((age - launchTick) / 13.0f);
            float fade = shardFade(age);
            float partial = age - (int) age;
            float renderRotationX = rotationX + (launched ? currentAngularVelocityX * partial : 0.0f);
            float renderRotationY = rotationY + (launched ? currentAngularVelocityY * partial : 0.0f);
            float renderRotationZ = rotationZ + (launched ? currentAngularVelocityZ * partial : 0.0f);
            float facing = facing(renderRotationX, renderRotationY);
            float crackOpen = 1.0f - smooth((age - launchTick + 2.0f) / 24.0f);
            float lineBoost = 0.70f + slashInfluence * 0.62f;
            float alpha = reveal
                    * fade
                    * Mth.lerp(explode, 0.42f, 0.88f)
                    * (0.64f + facing * 0.36f)
                    * DimensionalSlashTuning.ScreenBreak.SHARD_CRACK_LIGHT_ALPHA
                    * (0.44f + crackOpen * 0.56f)
                    * lineBoost;
            if (alpha <= 0.003f) return false;

            float jitter = preLaunchJitter(age, reveal, explode);
            float jitterX = Mth.sin(age * 0.42f + depth * 17.0f) * jitter;
            float jitterY = Mth.cos(age * 0.37f + depth * 13.0f) * jitter;
            float renderX = positionX + (launched ? currentVelocityX * partial : 0.0f) + jitterX;
            float renderY = positionY + (launched ? currentVelocityY * partial : 0.0f) + jitterY;
            float renderZ = positionZ + (launched ? currentVelocityZ * partial : 0.0f);
            float renderScale = scale + explode * lift * 0.045f;
            float pixelScale = Math.max(0.72f, Math.min(width, height) / 720.0f);
            float thickness = DimensionalSlashTuning.ScreenBreak.SHARD_THICKNESS_PIXELS
                    * DimensionalSlashTuning.Quick.GLASS_SHARD_THICKNESS_SCALE
                    * pixelScale
                    * (0.78f + lift * 0.44f)
                    * (0.82f + explode * 0.18f);
            float frontZ = thickness * 0.5f;
            float crackBeamLength = DimensionalSlashTuning.ScreenBreak.SHARD_CRACK_LIGHT_LENGTH_PIXELS
                    * pixelScale
                    * (0.70f + lift * 0.42f)
                    * (1.08f - explode * 0.34f);
            float crackBeamWidth = DimensionalSlashTuning.ScreenBreak.SHARD_CRACK_LIGHT_WIDTH_PIXELS
                    * pixelScale
                    * (0.82f + lift * 0.34f);
            float coreWidth = Math.max(1.6f * pixelScale, crackBeamWidth * 0.16f);

            ProjectedPoint pc = projectPoint(renderX, renderY, renderZ, baseX, baseY, center, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height);
            boolean emitted = false;
            for (int i = 0; i < polygon.size(); i++) {
                Point a = polygon.get(i);
                Point b = polygon.get((i + 1) % polygon.size());
                ProjectedPoint pa = projectPoint(renderX, renderY, renderZ, baseX, baseY, a, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height);
                ProjectedPoint pb = projectPoint(renderX, renderY, renderZ, baseX, baseY, b, frontZ, renderRotationX, renderRotationY, renderRotationZ, renderScale, width, height);
                if (screenDistance(pa, pb) <= 1.25f) continue;

                float midX = (pa.x + pb.x) * 0.5f;
                float midY = (pa.y + pb.y) * 0.5f;
                float dirX = midX - pc.x;
                float dirY = midY - pc.y;
                float dirLength = Mth.sqrt(dirX * dirX + dirY * dirY);
                if (dirLength <= 0.001f) {
                    float edgeX = pb.x - pa.x;
                    float edgeY = pb.y - pa.y;
                    float edgeLength = Mth.sqrt(edgeX * edgeX + edgeY * edgeY);
                    if (edgeLength > 0.001f) {
                        dirX = -edgeY / edgeLength;
                        dirY = edgeX / edgeLength;
                        dirLength = 1.0f;
                    }
                }
                if (dirLength <= 0.001f) continue;

                dirX /= dirLength;
                dirY /= dirLength;
                addCrackLightBeam(builder, pa, pb, dirX, dirY, crackBeamLength * 1.20f, crackBeamWidth * 1.70f, 0.34f, 0.70f, 1.0f, alpha * 0.18f);
                addCrackLightBeam(builder, pa, pb, dirX, dirY, crackBeamLength * 0.56f, crackBeamWidth * 0.68f, 0.92f, 0.98f, 1.0f, alpha * 0.34f);
                addGlowRibbon(builder, pa, pb, coreWidth, 0.98f, 1.0f, 1.0f, alpha * 0.74f);
                emitted = true;
            }
            return emitted;
        }

        private static void addVertex(BufferBuilder builder, float renderX, float renderY, float renderZ, float baseX, float baseY, Point point, float localZ, float rotationX, float rotationY, float rotationZ, float scale, int width, int height, float edge, float shade, float lift, float alpha, float u, float v) {
            ProjectedPoint projected = projectPoint(renderX, renderY, renderZ, baseX, baseY, point, localZ, rotationX, rotationY, rotationZ, scale, width, height);
            builder.vertex(projected.x, projected.y, projected.z)
                    .color(Mth.clamp(edge, 0.0f, 1.0f), Mth.clamp(shade, 0.0f, 1.0f), Mth.clamp(lift, 0.0f, 1.0f), Mth.clamp(alpha, 0.0f, 1.0f))
                    .uv(u, v)
                    .endVertex();
        }

        private float preLaunchJitter(float age, float reveal, float explode) {
            float settle = 1.0f - smooth((age - revealTick) / 8.0f);
            return (1.0f - explode) * reveal * settle * 0.22f;
        }

        private float crackReveal(float age) {
            float revealBand = 0.065f + (1.0f - slashInfluence) * 0.025f;
            return smooth((stagedCrackCoverage(age) - crackOrder) / revealBand);
        }

        private float breakOpenReveal(float age) {
            return smooth((age - launchTick + 1.0f) / 3.0f);
        }

        private static ProjectedPoint projectPoint(float renderX, float renderY, float renderZ, float baseX, float baseY, Point point, float localZ, float rotationX, float rotationY, float rotationZ, float scale, int width, int height) {
            float px = point.x * width;
            float py = point.y * height;
            float lx = (px - baseX) * scale;
            float ly = (py - baseY) * scale;
            float lz = localZ * scale;

            float cx = Mth.cos(rotationX);
            float sx = Mth.sin(rotationX);
            float y1 = ly * cx - lz * sx;
            float z1 = ly * sx + lz * cx;

            float cy = Mth.cos(rotationY);
            float sy = Mth.sin(rotationY);
            float x2 = lx * cy + z1 * sy;
            float z2 = -lx * sy + z1 * cy;

            float cz = Mth.cos(rotationZ);
            float sz = Mth.sin(rotationZ);
            float x3 = x2 * cz - y1 * sz;
            float y3 = x2 * sz + y1 * cz;
            float z3 = z2;

            float z = renderZ + z3;
            float focal = Math.max(240.0f, DimensionalSlashTuning.ScreenBreak.SHARD_PERSPECTIVE_FOCAL);
            float perspective = Mth.clamp(focal / Math.max(focal - z, focal * 0.24f), 0.42f, 2.65f);
            float x = renderX + x3 * perspective;
            float y = renderY + y3 * perspective;

            return new ProjectedPoint(x, y, Mth.clamp(z * 0.18f, -280.0f, 280.0f));
        }

        private static void addGlowRibbon(BufferBuilder builder, ProjectedPoint a, ProjectedPoint b, float width, float r, float g, float blue, float alpha) {
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float length = Mth.sqrt(dx * dx + dy * dy);
            if (length <= 0.001f) return;

            float sx = -dy / length * width * 0.5f;
            float sy = dx / length * width * 0.5f;
            float z = (a.z + b.z) * 0.5f;
            float safeAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
            builder.vertex(a.x + sx, a.y + sy, z).color(r, g, blue, safeAlpha).endVertex();
            builder.vertex(a.x - sx, a.y - sy, z).color(r, g, blue, safeAlpha).endVertex();
            builder.vertex(b.x - sx, b.y - sy, z).color(r, g, blue, safeAlpha).endVertex();
            builder.vertex(b.x + sx, b.y + sy, z).color(r, g, blue, safeAlpha).endVertex();
        }

        private static void addGlowQuad(BufferBuilder builder, ProjectedPoint a, ProjectedPoint b, ProjectedPoint c, ProjectedPoint d, float r, float g, float blue, float alpha) {
            float safeAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
            float z = (a.z + b.z + c.z + d.z) * 0.25f;
            builder.vertex(a.x, a.y, z).color(r, g, blue, safeAlpha).endVertex();
            builder.vertex(b.x, b.y, z).color(r, g, blue, safeAlpha).endVertex();
            builder.vertex(c.x, c.y, z).color(r, g, blue, safeAlpha).endVertex();
            builder.vertex(d.x, d.y, z).color(r, g, blue, safeAlpha).endVertex();
        }

        private static void addCrackLightBeam(BufferBuilder builder, ProjectedPoint a, ProjectedPoint b, float dirX, float dirY, float length, float endWidth, float r, float g, float blue, float alpha) {
            float sx = -dirY * endWidth * 0.5f;
            float sy = dirX * endWidth * 0.5f;
            float z = (a.z + b.z) * 0.5f;
            float safeAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
            float endAX = a.x + dirX * length - sx;
            float endAY = a.y + dirY * length - sy;
            float endBX = b.x + dirX * length + sx;
            float endBY = b.y + dirY * length + sy;

            builder.vertex(a.x, a.y, z).color(r, g, blue, safeAlpha).endVertex();
            builder.vertex(b.x, b.y, z).color(r, g, blue, safeAlpha).endVertex();
            builder.vertex(endBX, endBY, z).color(r, g, blue, 0.0f).endVertex();
            builder.vertex(endAX, endAY, z).color(r, g, blue, 0.0f).endVertex();
        }

        private static float screenDistance(ProjectedPoint a, ProjectedPoint b) {
            float dx = a.x - b.x;
            float dy = a.y - b.y;
            return Mth.sqrt(dx * dx + dy * dy);
        }

        private static float facing(float rotationX, float rotationY) {
            return Mth.clamp(Math.abs(Mth.cos(rotationX) * Mth.cos(rotationY)), 0.0f, 1.0f);
        }

        private static float smooth(float value) {
            value = Mth.clamp(value, 0.0f, 1.0f);
            return value * value * (3.0f - 2.0f * value);
        }

        private record ProjectedPoint(float x, float y, float z) {}
    }
}
