package flu.kitten.adorablearmory.item;

import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class OrePerspectiveCore extends Item {
    public static final int MAX_DURABILITY = 200; // 最大耐久
    public static final int DURABILITY_TICK_INTERVAL = 10; // 扣耐久的时间间隔/tick
    public static final int DURABILITY_COST_PER_INTERVAL = 1; // 每次扣除的耐久点数
    public OrePerspectiveCore(Properties props) {
        super(props.stacksTo(1).durability(MAX_DURABILITY));
    }

    @Override
    public boolean isRepairable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return 0x4AFFDD;
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, Level level, @NotNull Entity entity, int slot, boolean isSelected) {
        if (level.isClientSide()) return;
        if (!(entity instanceof Player player)) return;
        if (player.isCreative() || player.isSpectator()) return; // test
        if (stack.isEmpty() || !stack.isDamageableItem()) return;

        ItemStack active = getActiveStack(player);
        if (active != stack) return;
        if (player.tickCount % DURABILITY_TICK_INTERVAL != 0) return;

        boolean broke = stack.hurt(DURABILITY_COST_PER_INTERVAL, level.getRandom(), (player instanceof ServerPlayer serverPlayer) ? serverPlayer : null);

        if (broke) {
            if (player.getMainHandItem() == stack) {
                player.broadcastBreakEvent(EquipmentSlot.MAINHAND);
            } else if (player.getOffhandItem() == stack) {
                player.broadcastBreakEvent(EquipmentSlot.OFFHAND);
            }
            stack.shrink(1);
        }
    }

    public static boolean hasUsableActivator(Player player) {
        ItemStack stack = getActiveStack(player);
        return !stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() < stack.getMaxDamage();
    }

    public static ItemStack getActiveStack(Player player) {
        Item target = AdorableArmoryRegister.ORE_PERSPECTIVE_CORE.get();

        ItemStack mainHandItem = player.getMainHandItem();
        if (mainHandItem.is(target) && isUsable(mainHandItem)) return mainHandItem;

        ItemStack offhandItem = player.getOffhandItem();
        if (offhandItem.is(target) && isUsable(offhandItem)) return offhandItem;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(target) && isUsable(stack)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isUsable(ItemStack stack) {
        return stack.isDamageableItem() && stack.getDamageValue() < stack.getMaxDamage();
    }
}
