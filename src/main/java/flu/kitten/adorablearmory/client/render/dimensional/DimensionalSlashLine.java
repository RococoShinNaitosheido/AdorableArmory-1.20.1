package flu.kitten.adorablearmory.client.render.dimensional;

import flu.kitten.adorablearmory.client.render.dimensional.config.DimensionalSlashTuning;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class DimensionalSlashLine {
    private static final int DEFAULT_OUTER_COLOR = DimensionalSlashTuning.WorldSlash.OUTER_COLOR_PRIMARY;
    private static final int DEFAULT_CORE_COLOR = DimensionalSlashTuning.WorldSlash.CORE_COLOR_ALT;

    private final Vec3 center;
    private final Vec3 direction;
    private final Vec3 normal;
    private final float length;
    private final float width;
    private final int outerColor;
    private final int coreColor;
    private final int startTick;
    private final int revealTicks;
    private final int holdTicks;
    private boolean completionEffectsSpawned;

    public DimensionalSlashLine(Vec3 center, Vec3 direction, Vec3 normal, float length, float width, int startTick, int revealTicks, int holdTicks) {
        this(center, direction, normal, length, width, DEFAULT_OUTER_COLOR, DEFAULT_CORE_COLOR, startTick, revealTicks, holdTicks);
    }

    public DimensionalSlashLine(Vec3 center, Vec3 direction, Vec3 normal, float length, float width, int outerColor, int coreColor, int startTick, int revealTicks, int holdTicks) {
        this.center = center;
        this.direction = safeNormalize(direction, new Vec3(1.0, 0.0, 0.0));
        this.normal = safeNormalize(normal, new Vec3(0.0, 1.0, 0.0));
        this.length = Math.max(0.1f, length);
        this.width = Math.max(0.02f, width);
        this.outerColor = outerColor;
        this.coreColor = coreColor;
        this.startTick = Math.max(0, startTick);
        this.revealTicks = Math.max(1, revealTicks);
        this.holdTicks = Math.max(0, holdTicks);
    }

    public Vec3 center() {
        return center;
    }

    public Vec3 direction() {
        return direction;
    }

    public Vec3 normal() {
        return normal;
    }

    public float length() {
        return length;
    }

    public float width() {
        return width;
    }

    public int outerColor() {
        return outerColor;
    }

    public int coreColor() {
        return coreColor;
    }

    public int startTick() {
        return startTick;
    }

    public int revealTicks() {
        return revealTicks;
    }

    public int endTick() {
        return startTick + revealTicks + holdTicks;
    }

    public int removeTick() {
        return endTick() + DimensionalSlashTuning.WorldSlash.LINE_COLLAPSE_TICKS + DimensionalSlashTuning.WorldSlash.LINE_COLLAPSE_FADE_TICKS;
    }

    public boolean isStarted(float age) {
        return age >= startTick;
    }

    public boolean isFullyRevealed(float age) {
        return revealProgress(age) >= 1.0f;
    }

    public boolean consumeCompletionEffect(float age) {
        if (completionEffectsSpawned || !isFullyRevealed(age)) return false;
        completionEffectsSpawned = true;
        return true;
    }

    public float revealProgress(float age) {
        float raw = (age - startTick) / revealTicks;
        return smooth(Mth.clamp(raw, 0.0f, 1.0f));
    }

    public float visibleLength(float age) {
        return length * revealProgress(age);
    }

    public Vec3 start() {
        return center.subtract(direction.scale(length * 0.5f));
    }

    public Vec3 end() {
        return center.add(direction.scale(length * 0.5f));
    }

    public Vec3 currentStart(float age) {
        return center.subtract(direction.scale(visibleLength(age) * 0.5f));
    }

    public Vec3 currentEnd(float age) {
        return center.add(direction.scale(visibleLength(age) * 0.5f));
    }

    private static float smooth(float x) {
        return x * x * (3.0f - 2.0f * x);
    }

    private static Vec3 safeNormalize(Vec3 value, Vec3 fallback) {
        if (value.lengthSqr() < 1.0e-8) return fallback;
        return value.normalize();
    }
}
