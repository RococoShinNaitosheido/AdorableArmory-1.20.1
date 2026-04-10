package flu.kitten.adorablearmory.item;

import flu.kitten.adorablearmory.api.duck.IGlintColorProvider;
import flu.kitten.adorablearmory.entity.weapons.TrueDemonArrow;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import flu.kitten.adorablearmory.util.TrueDemonBowNameEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TrueDemonArrowItem extends ArrowItem implements IGlintColorProvider {

    public TrueDemonArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull AbstractArrow createArrow(@NotNull Level level, @NotNull ItemStack stack, @NotNull LivingEntity livingEntity) {
        TrueDemonArrow trueDemonArrow = new TrueDemonArrow(AdorableArmoryRegister.TRUE_DEMON_ARROW_ENTITY.get(), level);
        trueDemonArrow.setOwner(livingEntity);

        Vec3 eye = livingEntity.getEyePosition().subtract(0.0, 0.1, 0.0);
        trueDemonArrow.setPos(eye.x, eye.y, eye.z);

        trueDemonArrow.xOld = trueDemonArrow.getX();
        trueDemonArrow.yOld = trueDemonArrow.getY();
        trueDemonArrow.zOld = trueDemonArrow.getZ();

        trueDemonArrow.setYRot(livingEntity.getYRot());
        trueDemonArrow.setXRot(livingEntity.getXRot());
        trueDemonArrow.yRotO = trueDemonArrow.getYRot();
        trueDemonArrow.xRotO = trueDemonArrow.getXRot();

        trueDemonArrow.setBaseDamage(10);
        trueDemonArrow.setKnockback(1);
        return trueDemonArrow;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> list, @NotNull TooltipFlag flag) {
        Component bullet = Component.literal("• ").withStyle(ChatFormatting.LIGHT_PURPLE);
        String line1 = Component.translatable("斯卡蕾特·萝拉·艾莉米娅的特殊真魔箭").getString();
        String line2 = Component.translatable("目前正在测试中...").getString();
        Component effect1 = TrueDemonBowNameEffects.violetMagentaSweep(line1);
        Component effect2 = TrueDemonBowNameEffects.violetMagentaSweep(line2);

        list.add(bullet.copy().append(effect1));
        list.add(bullet.copy().append(effect2));
    }

    @Override
    public @NotNull Component getName(@NotNull ItemStack stack) {
        return Component.translatable("item.adorablearmory.true_demon_arrow");
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
}
