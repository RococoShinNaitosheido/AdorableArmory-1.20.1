package flu.kitten.adorablearmory.client.render.dimensional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.client.render.dimensional.config.DimensionalSlashTuning;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public final class DimensionalSlashEffect {
    private static final float GOLDEN_ANGLE = 2.3999632f;
    private static final float[] ANCHOR_YAWS = { -0.18f, 1.46f, 2.82f };
    private static final float[] ANCHOR_PITCHES = { 0.08f, -0.20f, 0.18f };
    private static final Vec3[] ANCHOR_OFFSETS = {
            new Vec3(-0.08, 0.04, 0.06),
            new Vec3(0.12, -0.10, -0.04),
            new Vec3(-0.02, 0.12, 0.10)
    };
    private static final float[] ANCHOR_LENGTH_SCALES = { 1.08f, 0.98f, 0.92f };
    private static final float[] ANCHOR_WIDTHS = { 0.30f, 0.27f, 0.24f };

    private final Vec3 center;
    private final float radius;
    private final Random random;
    private final List<DimensionalSlashLine> lines;
    private final List<DimensionalSlashWorldShard> worldShards = new ArrayList<>();
    private final int finalBurstTick;
    private final int fadeStartTick;
    private int age;
    private boolean finalBurstSpawned;

    private DimensionalSlashEffect(Vec3 center, float radius, long seed, List<DimensionalSlashLine> lines) {
        this.center = center;
        this.radius = Math.max(1.0f, radius);
        this.random = new Random(seed ^ 0x8D15EA5EEDL);
        this.lines = lines;
        int lastRevealTick = lines.stream().mapToInt(line -> line.startTick() + line.revealTicks()).max().orElse(0);
        this.finalBurstTick = lastRevealTick + DimensionalSlashTuning.WorldSlash.FINAL_BURST_DELAY_TICKS;
        this.fadeStartTick = this.finalBurstTick;
    }

    public static DimensionalSlashEffect create(Vec3 center, int slashCount, float length, float radius, long seed) {
        int count = Mth.clamp(slashCount, 1, DimensionalSlashTuning.WorldSlash.MAX_SLASHES);
        float safeLength = Mth.clamp(length, DimensionalSlashTuning.WorldSlash.MIN_LENGTH, DimensionalSlashTuning.WorldSlash.MAX_LENGTH);
        float safeRadius = Mth.clamp(radius, DimensionalSlashTuning.WorldSlash.MIN_RADIUS, DimensionalSlashTuning.WorldSlash.MAX_RADIUS);
        List<DimensionalSlashLine> lines = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            SlashPlacement placement = slashPlacement(i, count, safeRadius);
            Vec3 lineCenter = center.add(placement.offset());
            Vec3 direction = placement.direction();
            Vec3 normal = placement.normal();
            float lineLength = safeLength * placement.lengthScale();
            float width = placement.width();
            int outer = fixedOuterColor(i);
            int core = (i % 3 == 0) ? DimensionalSlashTuning.WorldSlash.CORE_COLOR_ALT : DimensionalSlashTuning.WorldSlash.CORE_COLOR_PRIMARY;

            lines.add(new DimensionalSlashLine(lineCenter, direction, normal, lineLength, width, outer, core, placement.startTick(), DimensionalSlashTuning.WorldSlash.LINE_REVEAL_TICKS, DimensionalSlashTuning.WorldSlash.LINE_HOLD_TICKS));
        }

        return new DimensionalSlashEffect(center, safeRadius, seed, lines);
    }

    public static void renderXMark(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, Vec3 center, Vec3 camera, float markAge, float length, float width, float alpha, long seed) {
        if (alpha <= 0.001f) return;

        Vec3 normal = camera.subtract(center);
        if (normal.lengthSqr() < 1.0e-8) normal = new Vec3(0.0, 0.0, 1.0);
        normal = normal.normalize();

        Vec3 right = new Vec3(0.0, 1.0, 0.0).cross(normal);
        if (right.lengthSqr() < 1.0e-8) right = new Vec3(1.0, 0.0, 0.0);
        right = right.normalize();

        Vec3 up = normal.cross(right);
        if (up.lengthSqr() < 1.0e-8) up = new Vec3(0.0, 1.0, 0.0);
        up = up.normalize();

        Vec3 rising = right.add(up).normalize();
        Vec3 falling = right.subtract(up).normalize();
        float safeLength = Math.max(0.1f, length);
        float safeWidth = Math.max(0.02f, width);
        int holdTicks = Math.max(1, DimensionalSlashTuning.WorldSlash.LINE_HOLD_TICKS);
        List<DimensionalSlashLine> markLines = List.of(
                new DimensionalSlashLine(center, rising, normal, safeLength, safeWidth, DimensionalSlashTuning.WorldSlash.OUTER_COLOR_PRIMARY, DimensionalSlashTuning.WorldSlash.CORE_COLOR_PRIMARY, 0, 1, holdTicks),
                new DimensionalSlashLine(center, falling, normal, safeLength * 0.96f, safeWidth * 0.92f, DimensionalSlashTuning.WorldSlash.OUTER_COLOR_ALT_A, DimensionalSlashTuning.WorldSlash.CORE_COLOR_ALT, 0, 1, holdTicks)
        );

        DimensionalSlashEffect mark = new DimensionalSlashEffect(center, 1.0f, seed, markLines);
        float heldSlashAge = 1.0f + Math.min(Math.max(0.0f, markAge), holdTicks * 0.45f);
        float pulse = 0.94f + 0.06f * Mth.sin(markAge * DimensionalSlashTuning.WorldSlash.FLICKER_SPEED);
        float renderAlpha = Mth.clamp(alpha * pulse, 0.0f, 1.0f);
        for (DimensionalSlashLine line : markLines) {
            mark.renderLineBladeOnlyThroughEntities(poseStack, buffers, level, camera, line, heldSlashAge, renderAlpha);
        }
    }

    public boolean tick(ClientLevel level) {
        float effectAge = age;
        for (DimensionalSlashLine line : lines) {
            if (line.consumeCompletionEffect(effectAge)) {
                DimensionalSlashParticleBurst.spawnSlashShatter(level, line, random);
            }
        }

        if (!finalBurstSpawned && age >= finalBurstTick) {
            finalBurstSpawned = true;
            DimensionalSlashParticleBurst.spawnFinalBurst(level, center, radius + 3.0f, random);
            DimensionalSlashParticleBurst.spawnFinalWorldShards(center, radius + 3.0f, random, worldShards);
            DimensionalSlashClientEffects.triggerFinalScreenBreak(random.nextLong(), screenBreakLines());
        }

        Iterator<DimensionalSlashWorldShard> shardIterator = worldShards.iterator();
        while (shardIterator.hasNext()) {
            DimensionalSlashWorldShard shard = shardIterator.next();
            shard.tick();
            if (!shard.isAlive()) shardIterator.remove();
        }

        age++;
        return age <= fadeStartTick + DimensionalSlashTuning.WorldSlash.FINAL_FADE_TICKS || !worldShards.isEmpty();
    }

    public void render(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick) {
        renderBody(poseStack, buffers, camera, partialTick);
        renderWhiteCores(poseStack, buffers, camera, partialTick);
    }

    public void renderBody(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick) {
        float renderAge = age + partialTick;
        float alpha = globalAlpha(renderAge);
        if (alpha > 0.001f) {
            for (DimensionalSlashLine line : lines) {
                renderLineBody(poseStack, buffers, camera, line, renderAge, alpha);
            }
        }
    }

    public void renderWhiteCores(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick) {
        float renderAge = age + partialTick;
        float alpha = globalAlpha(renderAge);
        if (alpha <= 0.001f) return;

        for (DimensionalSlashLine line : lines) {
            renderLineWhiteCore(poseStack, buffers, camera, line, renderAge, alpha);
        }
    }

    public boolean hasWorldShards() {
        return !worldShards.isEmpty();
    }

    List<DimensionalSlashLine> screenBreakLines() {
        return List.copyOf(lines);
    }

    public boolean renderWorldShards(PoseStack poseStack, com.mojang.blaze3d.vertex.BufferBuilder builder, Vec3 camera, float partialTick) {
        boolean emitted = false;
        for (DimensionalSlashWorldShard shard : worldShards) {
            emitted |= shard.render(poseStack, builder, camera, partialTick);
        }
        return emitted;
    }

    public void renderBloomMask(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, float partialTick) {
        if (!DimensionalSlashBloomRenderer.canRenderBloomMask()) return;

        float renderAge = age + partialTick;
        float alpha = globalBloomAlpha(renderAge);
        if (alpha <= 0.001f) return;

        for (DimensionalSlashLine line : lines) {
            renderLineBloomMask(poseStack, buffers, camera, line, renderAge, alpha);
        }

        if (!worldShards.isEmpty()) {
            VertexConsumer shardMask = buffers.getBuffer(DimensionalSlashBloomRenderer.maskRenderType());
            for (DimensionalSlashWorldShard shard : worldShards) {
                shard.renderBloomMask(poseStack, shardMask, camera, partialTick);
            }
        }
    }

    private void renderLineBody(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, DimensionalSlashLine line, float renderAge, float globalAlpha) {
        if (!line.isStarted(renderAge)) return;

        float progress = line.revealProgress(renderAge);
        if (progress <= 0.001f) return;

        Vec3 start = line.currentStart(renderAge);
        Vec3 currentEnd = line.currentEnd(renderAge);
        Vec3 side = cameraFacingSide(start, currentEnd, camera, line.normal());
        float flicker = DimensionalSlashTuning.WorldSlash.FLICKER_BASE + DimensionalSlashTuning.WorldSlash.FLICKER_RANDOM * Mth.sin((renderAge + line.startTick()) * DimensionalSlashTuning.WorldSlash.FLICKER_SPEED);
        float lineAlpha = globalAlpha * flicker * DimensionalSlashTuning.Quick.WORLD_SLASH_ALPHA_SCALE;
        float auxiliaryAlpha = lineAuxiliaryAlpha(line, renderAge, progress);
        float detailReveal = postRevealDetailAlpha(progress);

        renderMainBlade(poseStack, buffers, camera, line, start, currentEnd, side, lineAlpha);
        if (auxiliaryAlpha > 0.001f && detailReveal > 0.001f) {
            float helperAlpha = lineAlpha * auxiliaryAlpha * detailReveal;
            renderNeedleExtensions(poseStack, buffers, camera, line, start, currentEnd, side, progress, helperAlpha);
            renderHairlineScratches(poseStack, buffers, camera, line, progress, helperAlpha);
        }

    }

    private void renderLineWhiteCore(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, DimensionalSlashLine line, float renderAge, float globalAlpha) {
        if (!line.isStarted(renderAge)) return;

        float progress = line.revealProgress(renderAge);
        if (progress <= 0.001f) return;

        Vec3 start = line.currentStart(renderAge);
        Vec3 currentEnd = line.currentEnd(renderAge);
        Vec3 side = cameraFacingSide(start, currentEnd, camera, line.normal());
        float flicker = DimensionalSlashTuning.WorldSlash.FLICKER_BASE + DimensionalSlashTuning.WorldSlash.FLICKER_RANDOM * Mth.sin((renderAge + line.startTick()) * DimensionalSlashTuning.WorldSlash.FLICKER_SPEED);
        float lineAlpha = globalAlpha * flicker * DimensionalSlashTuning.Quick.WORLD_SLASH_ALPHA_SCALE;
        renderWhiteCore(poseStack, buffers, camera, line, start, currentEnd, side, lineAlpha);
    }

    private void renderLineBladeOnlyThroughEntities(PoseStack poseStack, MultiBufferSource buffers, ClientLevel level, Vec3 camera, DimensionalSlashLine line, float renderAge, float globalAlpha) {
        if (!line.isStarted(renderAge)) return;

        float progress = line.revealProgress(renderAge);
        if (progress <= 0.001f) return;

        Vec3 start = line.currentStart(renderAge);
        Vec3 currentEnd = line.currentEnd(renderAge);
        Vec3 side = cameraFacingSide(start, currentEnd, camera, line.normal());
        float flicker = DimensionalSlashTuning.WorldSlash.FLICKER_BASE + DimensionalSlashTuning.WorldSlash.FLICKER_RANDOM * Mth.sin((renderAge + line.startTick()) * DimensionalSlashTuning.WorldSlash.FLICKER_SPEED);
        float lineAlpha = globalAlpha * flicker * DimensionalSlashTuning.Quick.WORLD_SLASH_ALPHA_SCALE;
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.DIMENSIONAL_SLASH_ENTITY_PIERCE);
        float width = worldLineWidth(line);

        drawFeatheredCutTaperedRibbonAgainstBlocks(poseStack, consumer, level, camera, start, currentEnd, side, width * DimensionalSlashTuning.WorldSlash.MAIN_OUTER_WIDTH, line.outerColor(), DimensionalSlashTuning.WorldSlash.MAIN_OUTER_ALPHA * lineAlpha, DimensionalSlashTuning.WorldSlash.MAIN_OUTER_ALPHA * lineAlpha, DimensionalSlashTuning.WorldSlash.MAIN_OUTER_SOFT_EDGE);
        drawFeatheredCutTaperedRibbonAgainstBlocks(poseStack, consumer, level, camera, start, currentEnd, side, width * DimensionalSlashTuning.WorldSlash.MAIN_CORE_WIDTH, line.coreColor(), DimensionalSlashTuning.WorldSlash.MAIN_CORE_ALPHA * lineAlpha, DimensionalSlashTuning.WorldSlash.MAIN_CORE_ALPHA * lineAlpha, DimensionalSlashTuning.WorldSlash.MAIN_CORE_SOFT_EDGE);
    }

    private void renderMainBlade(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, DimensionalSlashLine line, Vec3 start, Vec3 end, Vec3 side, float alpha) {
        VertexConsumer core = buffers.getBuffer(AdorableArmoryShaders.DIMENSIONAL_SLASH_CORE);
        float width = worldLineWidth(line);
        drawFeatheredCutTaperedRibbon(poseStack, core, camera, line, start, end, side, width * DimensionalSlashTuning.WorldSlash.MAIN_OUTER_WIDTH, line.outerColor(), DimensionalSlashTuning.WorldSlash.MAIN_OUTER_ALPHA * alpha, DimensionalSlashTuning.WorldSlash.MAIN_OUTER_ALPHA * alpha, DimensionalSlashTuning.WorldSlash.MAIN_OUTER_SOFT_EDGE);
    }

    private void renderLineBloomMask(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, DimensionalSlashLine line, float renderAge, float globalAlpha) {
        if (!line.isStarted(renderAge)) return;

        float progress = line.revealProgress(renderAge);
        if (progress <= 0.001f) return;
        float bloomReveal = postRevealDetailAlpha(progress);
        if (bloomReveal <= 0.001f) return;

        Vec3 start = line.currentStart(renderAge);
        Vec3 currentEnd = line.currentEnd(renderAge);
        Vec3 side = cameraFacingSide(start, currentEnd, camera, line.normal());
        float flicker = DimensionalSlashTuning.WorldSlash.FLICKER_BASE + DimensionalSlashTuning.WorldSlash.FLICKER_RANDOM * Mth.sin((renderAge + line.startTick()) * DimensionalSlashTuning.WorldSlash.FLICKER_SPEED);
        float alpha = globalAlpha * flicker * bloomReveal * DimensionalSlashTuning.WorldSlash.BLOOM_MASK_ALPHA * DimensionalSlashTuning.Quick.WORLD_BLOOM_INTENSITY_SCALE;
        float width = worldLineWidth(line);
        VertexConsumer mask = buffers.getBuffer(DimensionalSlashBloomRenderer.maskRenderType());

        drawCutTaperedRibbon(poseStack, mask, camera, line, start, currentEnd, side, width * DimensionalSlashTuning.WorldSlash.BLOOM_MASK_WIDTH, line.outerColor(), alpha, alpha);
        drawCutTaperedRibbon(poseStack, mask, camera, line, start, currentEnd, side, width * DimensionalSlashTuning.WorldSlash.MAIN_CORE_WIDTH, line.coreColor(), alpha, alpha);

        float auxiliaryAlpha = lineAuxiliaryAlpha(line, renderAge, progress);
        if (auxiliaryAlpha > 0.001f) {
            float extend = line.length() * (DimensionalSlashTuning.WorldSlash.NEEDLE_EXTEND_BASE + progress * DimensionalSlashTuning.WorldSlash.NEEDLE_EXTEND_PROGRESS);
            Vec3 direction = line.direction();
            Vec3 needleStart = start.subtract(direction.scale(extend * DimensionalSlashTuning.WorldSlash.NEEDLE_START_BACK));
            Vec3 needleEnd = currentEnd.add(direction.scale(extend * DimensionalSlashTuning.WorldSlash.NEEDLE_END_FORWARD));
            float helperAlpha = alpha * auxiliaryAlpha;
            drawCutTaperedRibbon(poseStack, mask, camera, line, needleStart, needleEnd, side, width * DimensionalSlashTuning.WorldSlash.BLOOM_MASK_NEEDLE_WIDTH, line.outerColor(), helperAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_OUTER_ALPHA, helperAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_OUTER_ALPHA);
        }

        if (auxiliaryAlpha > 0.001f) {
            renderHairlineMask(poseStack, mask, camera, line, progress, alpha * auxiliaryAlpha);
        }
    }

    private void renderNeedleExtensions(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, DimensionalSlashLine line, Vec3 start, Vec3 end, Vec3 side, float progress, float alpha) {
        if (progress <= DimensionalSlashTuning.WorldSlash.NEEDLE_PROGRESS_START) return;

        float extend = line.length() * (DimensionalSlashTuning.WorldSlash.NEEDLE_EXTEND_BASE + progress * DimensionalSlashTuning.WorldSlash.NEEDLE_EXTEND_PROGRESS);
        Vec3 direction = line.direction();
        Vec3 needleStart = start.subtract(direction.scale(extend * DimensionalSlashTuning.WorldSlash.NEEDLE_START_BACK));
        Vec3 needleEnd = end.add(direction.scale(extend * DimensionalSlashTuning.WorldSlash.NEEDLE_END_FORWARD));
        VertexConsumer feather = buffers.getBuffer(AdorableArmoryShaders.DIMENSIONAL_SLASH_CORE);
        VertexConsumer core = buffers.getBuffer(AdorableArmoryShaders.DIMENSIONAL_SLASH_CORE);
        float width = worldLineWidth(line);
        float fineWidth = fineLineWidth(line);

        float needleAlpha = alpha * (DimensionalSlashTuning.WorldSlash.NEEDLE_ALPHA_BASE + progress * DimensionalSlashTuning.WorldSlash.NEEDLE_ALPHA_PROGRESS);
        drawCutTaperedRibbon(poseStack, feather, camera, line, needleStart, needleEnd, side, width * DimensionalSlashTuning.WorldSlash.NEEDLE_OUTER_WIDTH, line.outerColor(), needleAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_OUTER_ALPHA, needleAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_OUTER_ALPHA);
        drawCutTaperedRibbon(poseStack, feather, camera, line, needleStart, needleEnd, side, width * DimensionalSlashTuning.WorldSlash.NEEDLE_CORE_WIDTH, line.coreColor(), needleAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_CORE_ALPHA, needleAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_CORE_ALPHA);

        float sidePhase = ((line.startTick() * 37) % 19) / 19.0f - 0.5f;
        Vec3 offset = side.scale(fineWidth * (DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_SIDE_BASE + Math.abs(sidePhase) * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_SIDE_RANDOM));
        Vec3 fineStart = start.subtract(direction.scale(extend * (DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_START_BASE + Math.abs(sidePhase) * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_START_RANDOM))).add(offset.scale(sidePhase));
        Vec3 fineEnd = end.add(direction.scale(extend * (DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_END_BASE + progress * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_END_PROGRESS))).add(offset.scale(sidePhase * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_END_SIDE_PULL));
        float fineAlpha = fineLineAlpha(needleAlpha);
        drawCutTaperedRibbon(poseStack, core, camera, line, fineStart, fineEnd, side, fineWidth * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_OUTER_WIDTH, line.outerColor(), fineAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_OUTER_ALPHA, fineAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_OUTER_ALPHA);
        drawCutTaperedRibbon(poseStack, core, camera, line, fineStart, fineEnd, side, fineWidth * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_CORE_WIDTH, line.coreColor(), fineAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_CORE_ALPHA, fineAlpha * DimensionalSlashTuning.WorldSlash.NEEDLE_FINE_CORE_ALPHA);
    }

    private void renderHairlineScratches(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, DimensionalSlashLine line, float progress, float alpha) {
        if (progress <= DimensionalSlashTuning.WorldSlash.HAIRLINE_PROGRESS_START) return;

        VertexConsumer bloom = buffers.getBuffer(AdorableArmoryShaders.DIMENSIONAL_SLASH_CORE);
        VertexConsumer core = buffers.getBuffer(AdorableArmoryShaders.DIMENSIONAL_SLASH_CORE);
        float fineWidth = fineLineWidth(line);
        Vec3 baseDir = line.direction();
        Vec3 normal = line.normal();
        Vec3 baseSide = baseDir.cross(normal);
        if (baseSide.lengthSqr() < 1.0e-8) baseSide = pickSide(baseDir);
        baseSide = baseSide.normalize();

        int scratchCount = DimensionalSlashTuning.WorldSlash.HAIRLINE_MIN_COUNT
                + (line.startTick() % Math.max(1, DimensionalSlashTuning.WorldSlash.HAIRLINE_EXTRA_CHANCE_MODULO) == 0 ? DimensionalSlashTuning.WorldSlash.HAIRLINE_EXTRA_COUNT : 0);
        for (int i = 0; i < scratchCount; i++) {
            float h0 = hash01(line.startTick() * 31 + i * 131 + 17);
            float h1 = hash01(line.startTick() * 47 + i * 149 + 43);
            float h2 = hash01(line.startTick() * 61 + i * 173 + 91);
            float angle = (h0 - 0.5f) * DimensionalSlashTuning.WorldSlash.HAIRLINE_ANGLE_SPREAD + (i - 1) * DimensionalSlashTuning.WorldSlash.HAIRLINE_INDEX_ANGLE_OFFSET;
            Vec3 dir = baseDir.scale(Mth.cos(angle))
                    .add(normal.scale(Mth.sin(angle) * DimensionalSlashTuning.WorldSlash.HAIRLINE_NORMAL_MIX))
                    .add(baseSide.scale((h1 - 0.5f) * DimensionalSlashTuning.WorldSlash.HAIRLINE_SIDE_MIX));
            if (dir.lengthSqr() < 1.0e-8) dir = baseDir;
            dir = dir.normalize();

            float length = line.length() * (DimensionalSlashTuning.WorldSlash.HAIRLINE_LENGTH_MIN + h1 * DimensionalSlashTuning.WorldSlash.HAIRLINE_LENGTH_RANDOM);
            float revealLength = length * Mth.clamp((progress - DimensionalSlashTuning.WorldSlash.HAIRLINE_REVEAL_START) / DimensionalSlashTuning.WorldSlash.HAIRLINE_REVEAL_PORTION, 0.0f, 1.0f);
            Vec3 offset = normal.scale((h2 - 0.5f) * line.length() * DimensionalSlashTuning.WorldSlash.HAIRLINE_OFFSET_NORMAL)
                    .add(baseSide.scale((h0 - 0.5f) * line.length() * DimensionalSlashTuning.WorldSlash.HAIRLINE_OFFSET_SIDE));
            Vec3 mid = line.center().add(offset);
            Vec3 start = mid.subtract(dir.scale(revealLength * 0.5f));
            Vec3 end = mid.add(dir.scale(revealLength * 0.5f));
            Vec3 side = cameraFacingSide(start, end, camera, normal);
            float scratchAlpha = fineLineAlpha(alpha * (DimensionalSlashTuning.WorldSlash.HAIRLINE_ALPHA_MIN + h2 * DimensionalSlashTuning.WorldSlash.HAIRLINE_ALPHA_RANDOM) * progress);

            drawCutTaperedRibbon(poseStack, bloom, camera, line, start, end, side, fineWidth * (DimensionalSlashTuning.WorldSlash.HAIRLINE_OUTER_WIDTH_MIN + h0 * DimensionalSlashTuning.WorldSlash.HAIRLINE_OUTER_WIDTH_RANDOM), line.outerColor(), scratchAlpha, scratchAlpha);
            drawCutTaperedRibbon(poseStack, core, camera, line, start, end, side, fineWidth * (DimensionalSlashTuning.WorldSlash.HAIRLINE_CORE_WIDTH_MIN + h1 * DimensionalSlashTuning.WorldSlash.HAIRLINE_CORE_WIDTH_RANDOM), line.coreColor(), scratchAlpha * DimensionalSlashTuning.WorldSlash.HAIRLINE_CORE_ALPHA, scratchAlpha * DimensionalSlashTuning.WorldSlash.HAIRLINE_CORE_ALPHA);
        }
    }

    private void renderHairlineMask(PoseStack poseStack, VertexConsumer mask, Vec3 camera, DimensionalSlashLine line, float progress, float alpha) {
        float fineWidth = fineLineWidth(line);
        Vec3 baseDir = line.direction();
        Vec3 normal = line.normal();
        Vec3 baseSide = baseDir.cross(normal);
        if (baseSide.lengthSqr() < 1.0e-8) baseSide = pickSide(baseDir);
        baseSide = baseSide.normalize();

        int scratchCount = DimensionalSlashTuning.WorldSlash.HAIRLINE_MIN_COUNT
                + (line.startTick() % Math.max(1, DimensionalSlashTuning.WorldSlash.HAIRLINE_EXTRA_CHANCE_MODULO) == 0 ? DimensionalSlashTuning.WorldSlash.HAIRLINE_EXTRA_COUNT : 0);
        for (int i = 0; i < scratchCount; i++) {
            float h0 = hash01(line.startTick() * 31 + i * 131 + 17);
            float h1 = hash01(line.startTick() * 47 + i * 149 + 43);
            float h2 = hash01(line.startTick() * 61 + i * 173 + 91);
            float angle = (h0 - 0.5f) * DimensionalSlashTuning.WorldSlash.HAIRLINE_ANGLE_SPREAD + (i - 1) * DimensionalSlashTuning.WorldSlash.HAIRLINE_INDEX_ANGLE_OFFSET;
            Vec3 dir = baseDir.scale(Mth.cos(angle))
                    .add(normal.scale(Mth.sin(angle) * DimensionalSlashTuning.WorldSlash.HAIRLINE_NORMAL_MIX))
                    .add(baseSide.scale((h1 - 0.5f) * DimensionalSlashTuning.WorldSlash.HAIRLINE_SIDE_MIX));
            if (dir.lengthSqr() < 1.0e-8) dir = baseDir;
            dir = dir.normalize();

            float length = line.length() * (DimensionalSlashTuning.WorldSlash.HAIRLINE_LENGTH_MIN + h1 * DimensionalSlashTuning.WorldSlash.HAIRLINE_LENGTH_RANDOM);
            float revealLength = length * Mth.clamp((progress - DimensionalSlashTuning.WorldSlash.HAIRLINE_REVEAL_START) / DimensionalSlashTuning.WorldSlash.HAIRLINE_REVEAL_PORTION, 0.0f, 1.0f);
            Vec3 offset = normal.scale((h2 - 0.5f) * line.length() * DimensionalSlashTuning.WorldSlash.HAIRLINE_OFFSET_NORMAL)
                    .add(baseSide.scale((h0 - 0.5f) * line.length() * DimensionalSlashTuning.WorldSlash.HAIRLINE_OFFSET_SIDE));
            Vec3 mid = line.center().add(offset);
            Vec3 start = mid.subtract(dir.scale(revealLength * 0.5f));
            Vec3 end = mid.add(dir.scale(revealLength * 0.5f));
            Vec3 side = cameraFacingSide(start, end, camera, normal);
            float scratchAlpha = fineLineAlpha(alpha * (DimensionalSlashTuning.WorldSlash.HAIRLINE_ALPHA_MIN + h2 * DimensionalSlashTuning.WorldSlash.HAIRLINE_ALPHA_RANDOM) * progress);
            drawCutTaperedRibbon(poseStack, mask, camera, line, start, end, side, fineWidth * DimensionalSlashTuning.WorldSlash.BLOOM_MASK_HAIRLINE_WIDTH, line.outerColor(), scratchAlpha, scratchAlpha);
        }
    }

    private void renderWhiteCore(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera, DimensionalSlashLine line, Vec3 start, Vec3 end, Vec3 side, float alpha) {
        VertexConsumer consumer = buffers.getBuffer(AdorableArmoryShaders.DIMENSIONAL_SLASH_CORE);
        float width = worldLineWidth(line);
        drawFeatheredCutTaperedRibbon(poseStack, consumer, camera, line, start, end, side, width * DimensionalSlashTuning.WorldSlash.MAIN_CORE_WIDTH, line.coreColor(), DimensionalSlashTuning.WorldSlash.MAIN_CORE_ALPHA * alpha, DimensionalSlashTuning.WorldSlash.MAIN_CORE_ALPHA * alpha, DimensionalSlashTuning.WorldSlash.MAIN_CORE_SOFT_EDGE);
    }

    private float globalAlpha(float renderAge) {
        if (renderAge <= fadeStartTick) return 1.0f;
        float t = (renderAge - fadeStartTick) / DimensionalSlashTuning.WorldSlash.FINAL_FADE_TICKS;
        return 1.0f - Mth.clamp(t, 0.0f, 1.0f);
    }

    private float globalBloomAlpha(float renderAge) {
        float alpha = globalAlpha(renderAge);
        return alpha * alpha;
    }

    private static float lineAuxiliaryAlpha(DimensionalSlashLine line, float renderAge, float progress) {
        float revealAlpha = Mth.clamp(progress, 0.0f, 1.0f);
        float fadeStart = line.startTick() + line.revealTicks();
        if (renderAge <= fadeStart) return revealAlpha;
        float fadeTicks = Math.max(1.0f, DimensionalSlashTuning.WorldSlash.LINE_HOLD_TICKS);
        float t = (renderAge - fadeStart) / fadeTicks;
        return revealAlpha * (1.0f - smooth(Mth.clamp(t, 0.0f, 1.0f)));
    }

    private static float postRevealDetailAlpha(float progress) {
        return smooth((progress - 0.96f) / 0.04f);
    }

    private static float worldLineWidth(DimensionalSlashLine line) {
        return line.width() * DimensionalSlashTuning.Quick.WORLD_SLASH_WIDTH_SCALE;
    }

    private static float fineLineWidth(DimensionalSlashLine line) {
        return worldLineWidth(line) * DimensionalSlashTuning.Quick.WORLD_FINE_LINE_SCALE;
    }

    private static float fineLineAlpha(float alpha) {
        return alpha * DimensionalSlashTuning.Quick.WORLD_FINE_LINE_SCALE;
    }

    private static void drawCutTaperedRibbon(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, DimensionalSlashLine line, Vec3 start, Vec3 end, Vec3 side, float width, int color, float startAlpha, float endAlpha) {
        drawTaperedRibbon(poseStack, consumer, camera, start, end, side, width, color, startAlpha, endAlpha);
    }

    private static void drawFeatheredCutTaperedRibbon(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, DimensionalSlashLine line, Vec3 start, Vec3 end, Vec3 side, float width, int color, float startAlpha, float endAlpha, float softEdge) {
        drawFeatheredTaperedRibbon(poseStack, consumer, camera, start, end, side, width, color, startAlpha, endAlpha, softEdge);
    }

    private static void drawFeatheredCutTaperedRibbonAgainstBlocks(PoseStack poseStack, VertexConsumer consumer, ClientLevel level, Vec3 camera, Vec3 start, Vec3 end, Vec3 side, float width, int color, float startAlpha, float endAlpha, float softEdge) {
        Vec3 span = end.subtract(start);
        double length = span.length();
        if (length <= 1.0e-5) return;

        int segments = Math.max(8, Math.min(DimensionalSlashTuning.WorldSlash.TAPER_SEGMENTS * 2, Mth.ceil(length * 8.0)));
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            Vec3 p0 = start.lerp(end, t0);
            Vec3 p1 = start.lerp(end, t1);
            Vec3 midpoint = start.lerp(end, (t0 + t1) * 0.5f);
            if (!isVisibleThroughBlocks(level, camera, midpoint)) continue;

            float w0 = width * taperProfile(t0);
            float w1 = width * taperProfile(t1);
            float a0 = Mth.lerp(t0, startAlpha, endAlpha) * alphaProfile(t0);
            float a1 = Mth.lerp(t1, startAlpha, endAlpha) * alphaProfile(t1);
            drawFeatheredRibbonSegment(poseStack, consumer, camera, p0, p1, side, w0, w1, color, a0, a1, softEdge);
        }
    }

    private static boolean isVisibleThroughBlocks(ClientLevel level, Vec3 camera, Vec3 point) {
        if (level == null) return true;

        double targetDistanceSqr = camera.distanceToSqr(point);
        if (targetDistanceSqr <= 1.0e-5) return true;

        HitResult hit = level.clip(new ClipContext(camera, point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, null));
        if (hit.getType() == HitResult.Type.MISS) return true;

        return camera.distanceToSqr(hit.getLocation()) >= targetDistanceSqr - 0.09;
    }

    private static void drawTaperedRibbon(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, Vec3 start, Vec3 end, Vec3 side, float width, int color, float startAlpha, float endAlpha) {
        Vec3 span = end.subtract(start);
        double length = span.length();
        if (length <= 1.0e-5) return;

        int segments = Math.max(4, Math.min(DimensionalSlashTuning.WorldSlash.TAPER_SEGMENTS, Mth.ceil(length * 2.0)));
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            Vec3 p0 = start.lerp(end, t0);
            Vec3 p1 = start.lerp(end, t1);
            float w0 = width * taperProfile(t0);
            float w1 = width * taperProfile(t1);
            float a0 = Mth.lerp(t0, startAlpha, endAlpha) * alphaProfile(t0);
            float a1 = Mth.lerp(t1, startAlpha, endAlpha) * alphaProfile(t1);
            drawRibbonSegment(poseStack, consumer, camera, p0, p1, side, w0, w1, color, a0, a1);
        }
    }

    private static void drawFeatheredTaperedRibbon(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, Vec3 start, Vec3 end, Vec3 side, float width, int color, float startAlpha, float endAlpha, float softEdge) {
        Vec3 span = end.subtract(start);
        double length = span.length();
        if (length <= 1.0e-5) return;

        int segments = Math.max(4, Math.min(DimensionalSlashTuning.WorldSlash.TAPER_SEGMENTS, Mth.ceil(length * 2.0)));
        for (int i = 0; i < segments; i++) {
            float t0 = i / (float) segments;
            float t1 = (i + 1) / (float) segments;
            Vec3 p0 = start.lerp(end, t0);
            Vec3 p1 = start.lerp(end, t1);
            float w0 = width * taperProfile(t0);
            float w1 = width * taperProfile(t1);
            float a0 = Mth.lerp(t0, startAlpha, endAlpha) * alphaProfile(t0);
            float a1 = Mth.lerp(t1, startAlpha, endAlpha) * alphaProfile(t1);
            drawFeatheredRibbonSegment(poseStack, consumer, camera, p0, p1, side, w0, w1, color, a0, a1, softEdge);
        }
    }

    private static float taperProfile(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        float endTaper = Mth.sin(Mth.PI * t);
        endTaper = (float) Math.pow(Math.max(0.0f, endTaper), DimensionalSlashTuning.WorldSlash.TAPER_POWER);
        float bladeBelly = DimensionalSlashTuning.WorldSlash.TAPER_BELLY_MIN + (1.0f - DimensionalSlashTuning.WorldSlash.TAPER_BELLY_MIN) * Mth.sin(Mth.PI * t);
        float midWidth = (float) Math.pow(Math.max(0.0f, Mth.sin(Mth.PI * t)), DimensionalSlashTuning.WorldSlash.TAPER_MID_WIDTH_POWER);
        float midBoost = Mth.lerp(midWidth, 1.0f, DimensionalSlashTuning.WorldSlash.TAPER_MID_WIDTH_BOOST);
        return endTaper * bladeBelly * midBoost;
    }

    private static float alphaProfile(float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        float fade = Mth.sin(Mth.PI * t);
        return Mth.clamp((float) Math.pow(Math.max(0.0f, fade), DimensionalSlashTuning.WorldSlash.ALPHA_POWER) * DimensionalSlashTuning.WorldSlash.ALPHA_MULTIPLIER, 0.0f, 1.0f);
    }

    private static float smooth(float value) {
        value = Mth.clamp(value, 0.0f, 1.0f);
        return value * value * (3.0f - 2.0f * value);
    }

    private static void drawRibbonSegment(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, Vec3 start, Vec3 end, Vec3 side, float startWidth, float endWidth, int color, float startAlpha, float endAlpha) {
        Vec3 halfStart = side.scale(startWidth * 0.5f);
        Vec3 halfEnd = side.scale(endWidth * 0.5f);
        Vec3 p0 = start.add(halfStart).subtract(camera);
        Vec3 p1 = start.subtract(halfStart).subtract(camera);
        Vec3 p2 = end.subtract(halfEnd).subtract(camera);
        Vec3 p3 = end.add(halfEnd).subtract(camera);

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        Matrix4f matrix = poseStack.last().pose();
        consumer.vertex(matrix, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, Mth.clamp(startAlpha, 0.0f, 1.0f)).endVertex();
        consumer.vertex(matrix, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, Mth.clamp(startAlpha, 0.0f, 1.0f)).endVertex();
        consumer.vertex(matrix, (float) p2.x, (float) p2.y, (float) p2.z).color(r, g, b, Mth.clamp(endAlpha, 0.0f, 1.0f)).endVertex();
        consumer.vertex(matrix, (float) p3.x, (float) p3.y, (float) p3.z).color(r, g, b, Mth.clamp(endAlpha, 0.0f, 1.0f)).endVertex();
    }

    private static void drawFeatheredRibbonSegment(PoseStack poseStack, VertexConsumer consumer, Vec3 camera, Vec3 start, Vec3 end, Vec3 side, float startWidth, float endWidth, int color, float startAlpha, float endAlpha, float softEdge) {
        if (startWidth <= 1.0e-5f && endWidth <= 1.0e-5f) return;

        float featherFraction = Mth.clamp(softEdge, 0.0f, 1.0f);
        if (featherFraction <= 1.0e-4f) {
            drawRibbonSegment(poseStack, consumer, camera, start, end, side, startWidth, endWidth, color, startAlpha, endAlpha);
            return;
        }

        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;

        float solidFraction = 1.0f - featherFraction;
        Vec3 outerHalfStart = side.scale(startWidth * 0.5f);
        Vec3 outerHalfEnd = side.scale(endWidth * 0.5f);
        Vec3 innerHalfStart = side.scale(startWidth * solidFraction * 0.5f);
        Vec3 innerHalfEnd = side.scale(endWidth * solidFraction * 0.5f);

        Vec3 rightOuterStart = start.add(outerHalfStart).subtract(camera);
        Vec3 rightInnerStart = start.add(innerHalfStart).subtract(camera);
        Vec3 leftInnerStart = start.subtract(innerHalfStart).subtract(camera);
        Vec3 leftOuterStart = start.subtract(outerHalfStart).subtract(camera);

        Vec3 rightOuterEnd = end.add(outerHalfEnd).subtract(camera);
        Vec3 rightInnerEnd = end.add(innerHalfEnd).subtract(camera);
        Vec3 leftInnerEnd = end.subtract(innerHalfEnd).subtract(camera);
        Vec3 leftOuterEnd = end.subtract(outerHalfEnd).subtract(camera);

        float a0 = Mth.clamp(startAlpha, 0.0f, 1.0f);
        float a1 = Mth.clamp(endAlpha, 0.0f, 1.0f);
        Matrix4f matrix = poseStack.last().pose();

        if (solidFraction > 1.0e-4f) {
            consumer.vertex(matrix, (float) rightInnerStart.x, (float) rightInnerStart.y, (float) rightInnerStart.z).color(r, g, b, a0).endVertex();
            consumer.vertex(matrix, (float) leftInnerStart.x, (float) leftInnerStart.y, (float) leftInnerStart.z).color(r, g, b, a0).endVertex();
            consumer.vertex(matrix, (float) leftInnerEnd.x, (float) leftInnerEnd.y, (float) leftInnerEnd.z).color(r, g, b, a1).endVertex();
            consumer.vertex(matrix, (float) rightInnerEnd.x, (float) rightInnerEnd.y, (float) rightInnerEnd.z).color(r, g, b, a1).endVertex();
        }

        consumer.vertex(matrix, (float) rightOuterStart.x, (float) rightOuterStart.y, (float) rightOuterStart.z).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, (float) rightInnerStart.x, (float) rightInnerStart.y, (float) rightInnerStart.z).color(r, g, b, a0).endVertex();
        consumer.vertex(matrix, (float) rightInnerEnd.x, (float) rightInnerEnd.y, (float) rightInnerEnd.z).color(r, g, b, a1).endVertex();
        consumer.vertex(matrix, (float) rightOuterEnd.x, (float) rightOuterEnd.y, (float) rightOuterEnd.z).color(r, g, b, 0.0f).endVertex();

        consumer.vertex(matrix, (float) leftInnerStart.x, (float) leftInnerStart.y, (float) leftInnerStart.z).color(r, g, b, a0).endVertex();
        consumer.vertex(matrix, (float) leftOuterStart.x, (float) leftOuterStart.y, (float) leftOuterStart.z).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, (float) leftOuterEnd.x, (float) leftOuterEnd.y, (float) leftOuterEnd.z).color(r, g, b, 0.0f).endVertex();
        consumer.vertex(matrix, (float) leftInnerEnd.x, (float) leftInnerEnd.y, (float) leftInnerEnd.z).color(r, g, b, a1).endVertex();
    }

    private static Vec3 cameraFacingSide(Vec3 start, Vec3 end, Vec3 camera, Vec3 fallback) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0e-8) return fallback.normalize();

        Vec3 midpoint = start.add(end).scale(0.5);
        Vec3 toCamera = camera.subtract(midpoint);
        Vec3 side = direction.normalize().cross(toCamera);
        if (side.lengthSqr() < 1.0e-8) side = fallback;
        if (side.lengthSqr() < 1.0e-8) side = new Vec3(0.0, 1.0, 0.0);
        return side.normalize();
    }

    private static float hash01(int value) {
        int x = value;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        return (x & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    private static Vec3 pickSide(Vec3 direction) {
        Vec3 up = Math.abs(direction.y) > 0.85 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(up);
        if (side.lengthSqr() < 1.0e-8) return new Vec3(1.0, 0.0, 0.0);
        return side.normalize();
    }

    private static SlashPlacement slashPlacement(int index, int count, float radius) {
        int anchorCount = Math.min(count, count <= 4 ? 2 : 3);
        if (index < anchorCount) {
            float yaw = ANCHOR_YAWS[index];
            float pitch = ANCHOR_PITCHES[index];
            Vec3 direction = directionFromAngles(yaw, pitch);
            Vec3 offset = ANCHOR_OFFSETS[index].scale(radius * 0.42f);
            Vec3 normal = stableNormal(direction);
            return new SlashPlacement(offset, direction, normal, ANCHOR_LENGTH_SCALES[index], ANCHOR_WIDTHS[index], index == 2 ? 1 : 0);
        }

        int orbitIndex = index - anchorCount;
        int orbitCount = Math.max(1, count - anchorCount);
        float h0 = hash01(index * 97 + count * 31 + 11);
        float h1 = hash01(index * 131 + count * 17 + 53);
        float h2 = hash01(index * 173 + count * 43 + 89);
        float h3 = hash01(index * 211 + count * 59 + 7);

        float u = (orbitIndex + 0.5f) / orbitCount;
        float y = 1.0f - 2.0f * u;
        float horizontal = Mth.sqrt(Math.max(0.0f, 1.0f - y * y));
        float angle = orbitIndex * GOLDEN_ANGLE + 0.72f + (h0 - 0.5f) * 0.46f;
        Vec3 shell = new Vec3(Mth.cos(angle) * horizontal, y * 0.92f, Mth.sin(angle) * horizontal);
        if (shell.lengthSqr() < 1.0e-8) shell = new Vec3(1.0, 0.0, 0.0);
        shell = shell.normalize();

        float shellRadius = radius * (0.52f + h1 * 0.36f);
        Vec3 offset = shell.scale(shellRadius);

        Vec3 tangent = shell.cross(new Vec3(0.0, 1.0, 0.0));
        if (tangent.lengthSqr() < 1.0e-8) tangent = shell.cross(new Vec3(1.0, 0.0, 0.0));
        tangent = tangent.normalize();
        Vec3 bitangent = shell.cross(tangent).normalize();
        float cutAngle = h2 * Mth.TWO_PI + orbitIndex * 0.37f;
        float radialSkew = (h3 - 0.5f) * 0.36f;
        Vec3 direction = tangent.scale(Mth.cos(cutAngle))
                .add(bitangent.scale(Mth.sin(cutAngle)))
                .add(shell.scale(radialSkew));
        if (direction.lengthSqr() < 1.0e-8) direction = tangent;
        direction = direction.normalize();

        Vec3 normal = shell.subtract(direction.scale(shell.dot(direction)));
        if (normal.lengthSqr() < 1.0e-8) normal = stableNormal(direction);
        normal = normal.normalize();

        float lengthScale = Mth.lerp(h0, 0.48f, 0.86f);
        if (orbitIndex % 5 == 1) lengthScale += 0.18f;
        float width = Mth.lerp(h2, 0.16f, 0.24f);
        int startTick = 1 + Math.floorMod(orbitIndex * 2 + orbitIndex / 3, 3);
        return new SlashPlacement(offset, direction, normal, lengthScale, width, startTick);
    }

    private static Vec3 directionFromAngles(float yaw, float pitch) {
        return new Vec3(Mth.cos(yaw) * Mth.cos(pitch), Mth.sin(pitch), Mth.sin(yaw) * Mth.cos(pitch)).normalize();
    }

    private static Vec3 stableNormal(Vec3 direction) {
        Vec3 raw = Math.abs(direction.y) > 0.88 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        raw = raw.subtract(direction.scale(raw.dot(direction)));
        if (raw.lengthSqr() < 1.0e-8) raw = pickSide(direction);
        return raw.normalize();
    }

    private static int fixedOuterColor(int index) {
        return switch (Math.floorMod(index, 4)) {
            case 1 -> DimensionalSlashTuning.WorldSlash.OUTER_COLOR_ALT_A;
            case 2 -> DimensionalSlashTuning.WorldSlash.OUTER_COLOR_ALT_B;
            default -> DimensionalSlashTuning.WorldSlash.OUTER_COLOR_PRIMARY;
        };
    }

    private record SlashPlacement(Vec3 offset, Vec3 direction, Vec3 normal, float lengthScale, float width, int startTick) {}

}
