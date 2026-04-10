package flu.kitten.adorablearmory.util;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

public final class TrueDemonBowNameEffects {

    private TrueDemonBowNameEffects() {}

    private static final int SWEEP_MAGENTA = 0xFF00FF; // Violet(亮紫罗兰) // 0xFF00FF
    private static final int BASE_VIOLET = 0xEE82EE; // Magenta(洋红) // 0xEE82EE
    private static final long PERIOD_MS = 2000L; // 每2秒一轮
    private static final float SWEEP_WIDTH = 1.8f; // 扫光“宽度” 字符单位
    private static final float GAMMA = 1.5f; // 扫光衰减曲线 越大越“锐利”

    public static Component violetMagentaSweep(String text) {
        int[] cps = text.codePoints().toArray();
        int n = cps.length;
        if (n == 0) return Component.empty();

        long now = System.currentTimeMillis();
        double phase = (now % PERIOD_MS) / (double) PERIOD_MS;

        double start = -SWEEP_WIDTH;
        double end = (n - 1) + SWEEP_WIDTH;
        double center = start + (end - start) * phase;

        MutableComponent out = Component.empty();

        for (int i = 0; i < n; i++) {
            double dist = Math.abs(i - center);
            double w = 1.0 - (dist / SWEEP_WIDTH);
            if (w < 0) w = 0;

            float t = (float) Math.pow(w, GAMMA);
            int rgb = leapRgb(BASE_VIOLET, SWEEP_MAGENTA, t);

            String chars = new String(Character.toChars(cps[i]));
            out.append(Component.literal(chars).withStyle(style -> style.withColor(TextColor.fromRgb(rgb)).withItalic(false)));
        }

        return out;
    }

    private static int leapRgb(int a, int b, float t) {
        t = clamp01(t);
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        return Math.min(v, 1f);
    }
}
