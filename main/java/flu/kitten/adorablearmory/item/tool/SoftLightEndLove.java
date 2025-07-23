package flu.kitten.adorablearmory.item.tool;

import flu.kitten.adorablearmory.color.VariousColor;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SoftLightEndLove extends SwordItem {

    public SoftLightEndLove(Tier tier, int attack, float speed, Properties properties) {
        super(tier, attack, speed, properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> list, @NotNull TooltipFlag flag) {
        stack.getOrCreateTag().putInt("HideFlags", 2);
        ChatFormatting[] rainbowColors = {ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.YELLOW, ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.BLUE, ChatFormatting.LIGHT_PURPLE};
        long time = System.currentTimeMillis();
        int colorOffset = (int) (time / 100 % rainbowColors.length);
        String infinityText = "无限";
        MutableComponent infinity = Component.literal("");
        for (int i = 0; i < infinityText.length(); i++) {
            int colorIndex = (infinityText.length() - 1 - i + colorOffset) % rainbowColors.length;
            infinity.append(Component.literal(String.valueOf(infinityText.charAt(i))).withStyle(Style.EMPTY.withColor(rainbowColors[colorIndex])));
        }
        list.add(Component.literal("在主手时：").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal("+ ").withStyle(ChatFormatting.GRAY).append(infinity).append(" 攻击伤害").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return (VariousColor.createRainbowGradientText(Component.translatable("item.adorablearmory.soft_light_end_love")));
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return false;
    }
}
