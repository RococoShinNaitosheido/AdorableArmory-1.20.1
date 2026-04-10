package flu.kitten.adorablearmory.util;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.level.Level;

import java.awt.*;
import java.util.Optional;

public class RococoColor {

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
            gradientText.append(Component.literal(String.valueOf(content.charAt(i))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF))));
        }
        return gradientText;
    }

    private static Component createDazzlingRainbowText(Component text) {
        if (text == null || text.getString().isEmpty()) return Component.empty();

        String content = text.getString();
        int length = content.length();
        MutableComponent gradientText = Component.empty();
        long gameTime = Optional.ofNullable(Minecraft.getInstance().level).map(Level::getGameTime).orElse(0L);

        // Faster speed and multiple frequencies for dazzling effect
        float speed = 0.025F;
        float primaryFrequency = 1.5F;
        float secondaryFrequency = 0.7F;

        for (int i = 0; i < length; i++) {
            float positionRatio = (float) i / length;
            float timeFactor = gameTime * speed;

            float primaryHue = (float) (Math.sin(2 * Math.PI * (primaryFrequency * (positionRatio + timeFactor))) * 0.5 + 0.5);
            float secondaryHue = (float) (Math.cos(2 * Math.PI * (secondaryFrequency * (positionRatio + timeFactor * 1.3))) * 0.3 + 0.7);

            float hue = (primaryHue * 0.7F + secondaryHue * 0.3F) % 1.0F;

            float saturation = (float) (0.8 + Math.sin(timeFactor * 3 + positionRatio * 8) * 0.15);
            saturation = Math.max(0.65F, Math.min(0.95F, saturation));

            float brightness = (float) (0.85 + Math.sin(timeFactor * 4 + positionRatio * 12) * 0.1 +
                    Math.cos(timeFactor * 6 + positionRatio * 20) * 0.05);
            brightness = Math.max(0.8F, Math.min(1.0F, brightness));

            int rgb = Color.HSBtoRGB(hue, saturation, brightness);
            gradientText.append(Component.literal(String.valueOf(content.charAt(i))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF))));
        }
        return gradientText;
    }

    private static Component createSmoothRainbowText(Component text) {
        if (text == null || text.getString().isEmpty()) return Component.empty();

        String content = text.getString();
        int length = content.length();
        MutableComponent gradientText = Component.empty();
        long gameTime = Optional.ofNullable(Minecraft.getInstance().level).map(Level::getGameTime).orElse(0L);

        float speed = 0.005F;
        float frequency = 0.6F;

        for (int i = 0; i < length; i++) {
            float positionRatio = (float) i / length;
            float timeFactor = gameTime * speed;
            float hue = (float) (Math.sin(2 * Math.PI * (frequency * (positionRatio + timeFactor))) * 0.4 + 0.5);

            hue += (float) (Math.sin(2 * Math.PI * (frequency * 0.3 * (positionRatio + timeFactor * 0.7))) * 0.1);
            hue = hue % 1.0F;
            if (hue < 0) hue += 1.0F;

            float saturation = 0.45F + (float) (Math.sin(timeFactor + positionRatio * 2) * 0.1);
            saturation = Math.max(0.35F, Math.min(0.55F, saturation));

            float brightness = 0.88F + (float) (Math.sin(timeFactor * 0.5 + positionRatio) * 0.05);
            brightness = Math.max(0.83F, Math.min(0.93F, brightness));

            int rgb = Color.HSBtoRGB(hue, saturation, brightness);
            gradientText.append(Component.literal(String.valueOf(content.charAt(i))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF))).withStyle(ChatFormatting.BOLD));
        }
        return gradientText;
    }

    private static Component createVarietyRainbowText(Component text) {
        if (text == null || text.getString().isEmpty()) return Component.empty();

        String content = text.getString();
        int length = content.length();
        MutableComponent gradientText = Component.empty();
        long gameTime = Optional.ofNullable(Minecraft.getInstance().level).map(Level::getGameTime).orElse(0L);

        float baseSpeed = 0.015F;
        float waveSpeed = 0.008F;
        float sparkleSpeed = 0.03F;

        for (int i = 0; i < length; i++) {
            float positionRatio = (float) i / length;
            float baseTime = gameTime * baseSpeed;
            float waveTime = gameTime * waveSpeed;
            float sparkleTime = gameTime * sparkleSpeed;

            float baseHue = (float) (Math.sin(2 * Math.PI * (positionRatio + baseTime)) * 0.4 + 0.5);
            float waveHue = (float) (Math.cos(2 * Math.PI * (positionRatio * 2 + waveTime * 1.5)) * 0.3);
            float spiralHue = (float) (Math.sin(2 * Math.PI * (positionRatio * 3 + baseTime * 0.8)) * 0.2);

            float hue = (baseHue + waveHue + spiralHue) % 1.0F;
            if (hue < 0) hue += 1.0F;

            float baseSaturation = 0.6F;
            float saturationWave = (float) (Math.sin(sparkleTime + positionRatio * 5) * 0.2);
            float saturationPulse = (float) (Math.cos(waveTime * 2 + positionRatio * 3) * 0.15);
            float saturation = baseSaturation + saturationWave + saturationPulse;
            saturation = Math.max(0.4F, Math.min(0.9F, saturation));

            float baseBrightness = 0.85F;
            float brightnessWave = (float) (Math.sin(sparkleTime * 1.5 + positionRatio * 8) * 0.1);
            float brightnessShimmer = (float) (Math.cos(baseTime * 3 + positionRatio * 15) * 0.05);
            float brightnessPulse = (float) (Math.sin(waveTime * 4 + positionRatio * 6) * 0.08);

            float brightness = baseBrightness + brightnessWave + brightnessShimmer + brightnessPulse;
            brightness = Math.max(0.7F, Math.min(1.0F, brightness));

            int rgb = Color.HSBtoRGB(hue, saturation, brightness);
            gradientText.append(Component.literal(String.valueOf(content.charAt(i))).setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb & 0xFFFFFF))));
        }
        return gradientText;
    }

    public static Component createRainbowText(Component text, int effectType) {
        return switch (effectType) {
            case 1 -> createDazzlingRainbowText(text);
            case 2 -> createSmoothRainbowText(text);
            case 3 -> createVarietyRainbowText(text);
            default -> createRainbowGradientText(text);
        };
    }

    private static void renderWaveRainbowText(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, Component text, int baseX, int baseY, int effectType, boolean dropShadow) {
        if (text == null || text.getString().isEmpty()) return;

        String content = text.getString();
        int length = content.length();
        long gameTime = Optional.ofNullable(Minecraft.getInstance().level).map(Level::getGameTime).orElse(0L);

        float waveSpeed = 0.24F;           // Speed of wave animation
        float waveAmplitude = 1.78F;        // Height of wave motion (pixels)
        float waveFrequency = 0.5F;        // How many waves across the text length
        float characterSpacing = 2.4F;     // Spacing between wave peaks per character

        int totalWidth = font.width(text);
        int currentX = baseX - totalWidth / 2;

        for (int i = 0; i < length; i++) {
            char character = content.charAt(i);
            String charStr = String.valueOf(character);

            if (character == ' ') {
                currentX += font.width(" ");
                continue;
            }

            float characterPosition = (float) i / Math.max(1, length - 1);
            float waveTime = gameTime * waveSpeed;
            float wavePhase = characterPosition * characterSpacing * 2 * (float) Math.PI * waveFrequency;
            float waveOffset = (float) (Math.sin(wavePhase + waveTime) * waveAmplitude);

            int charY = Math.round(baseY + waveOffset);
            Component coloredChar = createSingleCharacterRainbow(character, i, gameTime, effectType);
            graphics.drawString(font, coloredChar, currentX, charY, 0xFFFFFF, dropShadow);
            currentX += font.width(charStr);
        }
    }

    // HSB-RGB
    public static float[] hsbToRgb(float h, float s, float b) {
        float r = b, g = b, bl = b;
        if (s != 0.0f) {
            float hf = (h - (float)Math.floor(h)) * 6.0f;
            int i = (int)Math.floor(hf);
            float f = hf - i;
            float p = b * (1.0f - s);
            float q = b * (1.0f - s * f);
            float t = b * (1.0f - s * (1.0f - f));
            switch (i) {
                case 0 -> { r = b; g = t; bl = p; }
                case 1 -> { r = q; g = b; bl = p; }
                case 2 -> { r = p; g = b; bl = t; }
                case 3 -> { r = p; g = q; bl = b; }
                case 4 -> { r = t; g = p; bl = b; }
                default -> { r = b; g = p; bl = q; }
            }
        }
        return new float[]{ r, g, bl };
    }

    @SuppressWarnings("unused")
    private static Component createSingleCharacterRainbow(char character, int charIndex, long gameTime, int effectType) {
        String charStr = String.valueOf(character);
        Component charComponent = Component.literal(charStr);
        return switch (effectType) {
            case 1 -> createDazzlingRainbowText(charComponent);
            case 2 -> createSmoothRainbowText(charComponent);
            case 3 -> createVarietyRainbowText(charComponent);
            default -> createRainbowGradientText(charComponent);
        };
    }

    @SuppressWarnings("unused")
    public static void renderWaveRainbowText(GuiGraphics graphics, Font font, Component text, int baseX, int baseY, boolean dropShadow) {
        renderWaveRainbowText(graphics, font, text, baseX, baseY, 2, dropShadow);
    }
}
