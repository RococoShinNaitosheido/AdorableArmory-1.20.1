package flu.kitten.adorablearmory.color;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.level.Level;

import java.awt.*;
import java.util.Optional;

public class VariousColor {
    public static Component createRainbowGradientText(Component text) {
        if (text == null || text.getString().isEmpty()) return Component.empty();

        String content = text.getString();
        int length = content.length();
        MutableComponent gradientText = Component.empty();
        long gameTime = Optional.ofNullable(Minecraft.getInstance().level).map(Level::getGameTime).orElse(0L);
        float speed = 0.00920F;
        float frequency = 1.0F;

        for (int i = 0; i < length; i++) {
            float positionRatio = (float) i / length;
            float timeFactor = gameTime * speed;
            float hue = (float) (Math.sin(2 * Math.PI * (frequency * (positionRatio + timeFactor))) * 0.5 + 0.5);
            float saturation = 0.56F;
            float brightness = 0.92F;
            int rgb = Color.HSBtoRGB(hue, saturation, brightness);
            gradientText.append(Component.literal(String.valueOf(content.charAt(i))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF)))
            );
        }
        return gradientText;
    }
}
