package flu.kitten.adorablearmory.item.tool;

import flu.kitten.adorablearmory.api.duck.IGlintColorProvider;
import flu.kitten.adorablearmory.api.duck.IItemOutlineItem;
import flu.kitten.adorablearmory.client.itemoutline.ItemOutlineData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SoftLightEndLove extends SwordItem implements IItemOutlineItem, IGlintColorProvider {

    public SoftLightEndLove(Tier tier, int attack, float speed, Properties properties) {
        super(tier, attack, speed, properties);
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable("item.adorablearmory.soft_light_end_love").withStyle(ChatFormatting.LIGHT_PURPLE);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable ItemOutlineData getItemOutline(ItemStack stack, ItemDisplayContext context) {
        return switch (context) {
            case GUI -> new ItemOutlineData(0xffb755ff, 3);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> new ItemOutlineData(0xffb755ff, 6);
            default -> new ItemOutlineData(0xffb755ff, 4);
        };
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getGlintColor(ItemStack stack) {
        return 0xffb755ff;
    }
}
