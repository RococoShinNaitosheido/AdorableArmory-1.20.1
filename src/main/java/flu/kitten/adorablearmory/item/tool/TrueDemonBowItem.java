package flu.kitten.adorablearmory.item.tool;

import flu.kitten.adorablearmory.api.duck.IGlintColorProvider;
import flu.kitten.adorablearmory.api.duck.IItemOutlineItem;
import flu.kitten.adorablearmory.client.compat.oculus.itemoutline.ItemOutlineData;
import flu.kitten.adorablearmory.util.TrueDemonBowNameEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.ForgeEventFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TrueDemonBowItem extends BowItem implements IItemOutlineItem, IGlintColorProvider {

    public TrueDemonBowItem(Properties properties) {
        super(properties);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int timeLeft) {
        if (entity instanceof Player player) {
            boolean flag = player.getAbilities().instabuild || EnchantmentHelper.getItemEnchantmentLevel(Enchantments.INFINITY_ARROWS, stack) > 0;
            ItemStack itemStack = player.getProjectile(stack);

            int i = this.getUseDuration(stack) - timeLeft;
            i = ForgeEventFactory.onArrowLoose(stack, level, player, i, !itemStack.isEmpty() || flag);
            if (i < 0) return;

            if (!itemStack.isEmpty() || flag) {
                if (itemStack.isEmpty()) {
                    itemStack = new ItemStack(Items.ARROW);
                }

                float forTime = getPowerForTime(i);
                if (!((double)forTime < 0.1D)) {
                    boolean flag1 = player.getAbilities().instabuild || (itemStack.getItem() instanceof ArrowItem && ((ArrowItem)itemStack.getItem()).isInfinite(itemStack, stack, player));
                    if (!level.isClientSide) {
                        ArrowItem arrowitem = (ArrowItem)(itemStack.getItem() instanceof ArrowItem ? itemStack.getItem() : Items.ARROW);
                        AbstractArrow abstractarrow = arrowitem.createArrow(level, itemStack, player);
                        abstractarrow = customArrow(abstractarrow);
                        abstractarrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, forTime * 3.0F, 1.0F);
                        if (forTime == 1.0F) {
                            abstractarrow.setCritArrow(true);
                        }

                        int j = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.POWER_ARROWS, stack);
                        if (j > 0) {
                            abstractarrow.setBaseDamage(abstractarrow.getBaseDamage() + (double)j * 0.5D + 0.5D);
                        }

                        int k = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.PUNCH_ARROWS, stack);
                        if (k > 0) {
                            abstractarrow.setKnockback(k);
                        }

                        if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FLAMING_ARROWS, stack) > 0) {
                            abstractarrow.setSecondsOnFire(100);
                        }

                        stack.hurtAndBreak(1, player, (play) -> play.broadcastBreakEvent(player.getUsedItemHand()));

                        if (flag1 || player.getAbilities().instabuild && (itemStack.is(Items.SPECTRAL_ARROW) || itemStack.is(Items.TIPPED_ARROW))) {
                            abstractarrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                        }

                        level.addFreshEntity(abstractarrow);
                    }

                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.0f / (level.getRandom().nextFloat() * 0.4f + 1.2f) + forTime * 0.5f);
                    if (!flag1 && !player.getAbilities().instabuild) {
                        itemStack.shrink(1);
                        if (itemStack.isEmpty()) {
                            player.getInventory().removeItem(itemStack);
                        }
                    }

                    player.awardStat(Stats.ITEM_USED.get(this));
                }
            }
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, List<Component> list, @NotNull TooltipFlag flag) {
        // 测试
        Component bullet = Component.literal("• ");
        // 翻译 - 纯字符串
        String line1 = Component.translatable("斯卡蕾特·萝拉·艾莉米娅的专属武器").getString();
        String line2 = Component.translatable("目前正在测试中...").getString();
        String line3 = Component.translatable("字体测试字体测试").getString();
        Component text1 = TrueDemonBowNameEffects.violetMagentaSweep(line1);
        Component text2 = TrueDemonBowNameEffects.violetMagentaSweep(line2);
        Component text3 = TrueDemonBowNameEffects.violetMagentaSweep(line3);

        list.add(bullet.copy().append(text1));
        list.add(bullet.copy().append(text2));
        list.add(bullet.copy().append(text3));
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable("item.adorablearmory.true_demon_bow");
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getGlintColor(ItemStack stack) {
        return 0xffb755ff;
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
    public int getMaxStackSize(ItemStack stack) {
        return 1;
    }

    @Override
    public @Nullable ItemOutlineData getItemOutline(ItemStack stack, ItemDisplayContext context) {
        return switch (context) {
            case GUI -> new ItemOutlineData(0xffb755ff, 3);
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> new ItemOutlineData(0xffb755ff, 6);
            default -> new ItemOutlineData(0xffb755ff, 4);
        };
    }
}
