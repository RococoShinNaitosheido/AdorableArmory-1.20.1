package flu.kitten.adorablearmory.entity.boss.abilitymanager;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@SuppressWarnings("unused")
@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EarthquakeVisualsManager {

    private static volatile boolean ENABLED = true; // test
    private static final float MAX_AMPLITUDE_NORMAL = 12f;
    private static final float DECAY_NORMAL = 0.92f;
    private static final float MAX_AMPLITUDE_EXTREME = 30f;
    private static final float DECAY_EXTREME = 0.965f;
    private static final float BASE_FREQ_EXTREME = 22f;
    private static final float YAW_WEIGHT = 1.00f;
    private static final float PITCH_WEIGHT = 0.85f;
    private static final float ROLL_WEIGHT = 0.45f;
    private static boolean EXTREME_ENABLED = true;
    private static float amplitude = 0f;
    private static float frequency = 14f;
    private static int ticksLeft = 0;
    private static long seed = 0L;
    private static float trauma = 0f;
    private static float maxCap = MAX_AMPLITUDE_NORMAL;

    private EarthquakeVisualsManager() {}

    public static void trigger(float amp, int durationTicks, float freq) {
        if (!ENABLED) return;
        maxCap = EXTREME_ENABLED ? MAX_AMPLITUDE_EXTREME : MAX_AMPLITUDE_NORMAL;
        amplitude = Math.min(amp + amplitude * 0.5f, maxCap);
        frequency = freq;
        ticksLeft = Math.max(ticksLeft, durationTicks);
        seed = System.nanoTime();
        trauma = Math.min(1f, trauma + (amp / maxCap) * 0.6f);
    }

    public static void triggerExtreme(float amp, int durationTicks) {
        if (!EXTREME_ENABLED) {
            trigger(amp, durationTicks, 18f); return;
        }
        maxCap = MAX_AMPLITUDE_EXTREME;
        float boosted = amp * 1.4f + 2.0f;
        amplitude = Math.min(boosted + amplitude * 0.5f, maxCap);
        frequency = BASE_FREQ_EXTREME;
        ticksLeft = Math.max(ticksLeft, durationTicks);
        seed = System.nanoTime();
        trauma = Math.min(1f, trauma + (boosted / maxCap) * 0.8f);
    }

    public static void enableExtreme(boolean enable) {
        EXTREME_ENABLED = enable;
        if (!enable && amplitude > MAX_AMPLITUDE_NORMAL) {
            amplitude = MAX_AMPLITUDE_NORMAL;
        }
        maxCap = enable ? MAX_AMPLITUDE_EXTREME : MAX_AMPLITUDE_NORMAL;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (ticksLeft > 0) {
            ticksLeft--;

            float decay = amplitude > 8f ? DECAY_EXTREME : DECAY_NORMAL;
            amplitude *= decay;

            trauma *= (decay + 0.02f);

            if (ticksLeft == 0) {
                amplitude = 0f;
                trauma = 0f;
            }
        }
    }

    @SubscribeEvent
    public static void apply(ViewportEvent.ComputeCameraAngles e) {
        if (ticksLeft <= 0) return;

        float traumaFactor = clamp01(trauma * trauma);
        float ampCap = maxCap;
        float amp = Math.min(amplitude + traumaFactor * ampCap * 0.35f, ampCap);

        if (amp <= 0.001f) return;

        Minecraft mc = Minecraft.getInstance();
        double t = (mc.level == null ? 0.0 : mc.level.getGameTime()) + e.getPartialTick();

        float freq = frequency + traumaFactor * 8.0f;
        double base = t * (0.18 + (freq * 0.012));

        double s0 = Math.sin(base + (seed & 0xFF) * 0.01);
        double s1 = Math.sin(base * 1.37 + ((seed >> 8) & 0xFF) * 0.01);
        double s2 = Math.sin(base * 0.73 + ((seed >> 16) & 0xFF) * 0.01);

        float yawAdd   = (float) (s0 * amp * YAW_WEIGHT);
        float pitchAdd = (float) (s1 * amp * PITCH_WEIGHT);
        float rollAdd  = (float) (s2 * amp * ROLL_WEIGHT);

        e.setYaw(e.getYaw() + yawAdd);
        e.setPitch(e.getPitch() + pitchAdd);
        e.setRoll(e.getRoll() + rollAdd);
    }

    private static float clamp01(float x) {
        return x < 0f ? 0f : (Math.min(x, 1f));
    }

    // test
    public static void setEnabled(boolean enabled) {
        ENABLED = enabled;
    }

    // test
    public static boolean isEnabled() {
        return ENABLED;
    }
}
