package flu.kitten.adorablearmory.render;

import flu.kitten.adorablearmory.item.SparklingDreamIdolStar;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.awt.*;

public class RenderItemTooltip {

    private static final int[] COLORS = {
            0x4B0082, // Indigo
            0x191970, // Midnight Blue
            0x483D8B, // Dark Slate Blue
            0x800080, // Purple
            0x00008B, // Dark Blue
            0x000050, // Custom Dark Color
            0x3C1385  // Custom Purple Color
    };

    @SubscribeEvent
    public void renderTooltip(RenderTooltipEvent.Color render) {
        ItemStack stack = render.getItemStack();
        if (stack.getItem() instanceof SparklingDreamIdolStar) {
            long currentTime = System.currentTimeMillis();
            float hue = (currentTime % 10000L) / 10000.0f;
            int index = (int) (hue * COLORS.length);
            int nextIndex = (index + 1) % COLORS.length;
            float ratio = hue * COLORS.length - index;

            int startColor = blendColors(COLORS[index], COLORS[nextIndex], ratio);
            float[] hsb = adjustHSB(startColor);
            startColor = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);

            render.setBackground(0xE6000000);
            render.setBorderStart(startColor);
            render.setBorderEnd(startColor);
        }
    }

    private static int blendColors(int color1, int color2, float ratio) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 * (1 - ratio) + r2 * ratio);
        int g = (int) (g1 * (1 - ratio) + g2 * ratio);
        int b = (int) (b1 * (1 - ratio) + b2 * ratio);

        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    private static float[] adjustHSB(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        hsb[1] = Math.min(1.0f, hsb[1] * (float) 1.1); // <-1.2f = 20%
        hsb[2] = Math.min(1.0f, hsb[2] * (float) 1.1);
        return hsb;
    }
}
