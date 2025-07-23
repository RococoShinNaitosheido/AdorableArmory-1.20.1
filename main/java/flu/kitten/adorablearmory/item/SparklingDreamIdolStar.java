package flu.kitten.adorablearmory.item;

import flu.kitten.adorablearmory.color.VariousColor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SparklingDreamIdolStar extends Item {
    private static final String NAME_CN = "sparkling_dream_idol_star.cn";
    private static final String NAME_EN = "sparkling_dream_idol_star.en";
    private static long lastSwitchTime = 0;
    private static boolean showJapanese = true;
    private boolean transitioningToBlack = false;

    public SparklingDreamIdolStar(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isDamageable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean canBeDepleted() {
        return false;
    }

    @Override
    public boolean isFireResistant() {
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> list, @NotNull TooltipFlag flag) {
        list.add(VariousColor.createRainbowGradientText(Component.translatable("这仅仅只是一个图标物品...")));
        if (stack.getTag() == null || !stack.getTag().getBoolean("Unbreakable")) {
            stack.getOrCreateTag().putBoolean("Unbreakable", true);
            CompoundTag display = stack.getOrCreateTagElement("display");
            ListTag lore = new ListTag();
            display.put("Lore", lore);
            stack.getTag().put("display", display);
        }
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        long currentTime = System.currentTimeMillis();
        long switchInterval = 2000;

        float elapsedTime = (currentTime - lastSwitchTime) / (float) switchInterval;
        float alpha = Math.min(1.0f, elapsedTime);

        if (elapsedTime >= 1.0f) {
            if (transitioningToBlack) {
                showJapanese = !showJapanese;
                transitioningToBlack = false;
            } else {
                transitioningToBlack = true;
            }
            lastSwitchTime = currentTime;
            alpha = 0.0f;
        }

        int startColor, endColor;
        if (transitioningToBlack) {
            startColor = showJapanese ? 0xFFC0CB : 0xFFC0CB;
            endColor = 0x000000;
        } else {
            startColor = 0x000000;
            endColor = 0xFFC0CB;
        }

        int blendedColor = blendColors(startColor, endColor, alpha);
        Style colorStyle = Style.EMPTY.withColor(TextColor.fromRgb(blendedColor));
        if (showJapanese) return Component.translatable(NAME_CN).setStyle(colorStyle);
        else return Component.translatable(NAME_EN).setStyle(colorStyle);
    }

    private int blendColors(int color1, int color2, float alpha) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        int r = (int) (r1 * (1 - alpha) + r2 * alpha);
        int g = (int) (g1 * (1 - alpha) + g2 * alpha);
        int b = (int) (b1 * (1 - alpha) + b2 * alpha);
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int tick, boolean p_41408_) {
        if (entity instanceof Player player) {
            if (stack.getItem() == this) player.removeAllEffects();
        }
    }
}
