package flu.kitten.adorablearmory.entity.damagetype;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/*
Modified the bridging layer of the code
RococoShin 2026/03/11
*/
@SuppressWarnings("unused")
public final class TrueDemonCoreMod {

    public static float trueDemonSetHealth(LivingEntity self, float newHealth) {
        return Bridging.setHealth(self, newHealth);
    }

    public static float trueDemonHeal(LivingEntity self, float amount) {
        return Bridging.heal(self, amount);
    }

    public static boolean shouldBlockTotem(LivingEntity self, DamageSource source) {
        return Bridging.blockTotem(self, source);
    }

    public static float trueDemonActuallyHurtAmount(LivingEntity self, DamageSource source, float amount) {
        return Bridging.actuallyHurtAmount(self, source, amount);
    }

    public static boolean trueDemonBypassDamageBlock(LivingEntity self, DamageSource source) {
        return Bridging.bypassDamageBlock(self, source);
    }

    public static boolean trueDemonBypassEntityInvulnerability(Entity self, DamageSource source) {
        return Bridging.bypassEntityInvulnerability(self, source);
    }

    public static boolean trueDemonForgeLivingDeath(boolean canceled, LivingEntity self, DamageSource source) {
        return Bridging.forgeLivingDeath(canceled, self, source);
    }

    public static boolean trueDemonForgeLivingAttack(boolean allowed, LivingEntity self, DamageSource source, float amount) {
        return Bridging.forgeLivingAttack(allowed, self, source, amount);
    }

    public static Object trueDemonDataSet(Entity entity, EntityDataAccessor<?> accessor, Object value) {
        return Bridging.dataSet(entity, accessor, value);
    }

    public static ItemStack trueDemonPlayerSetItemSlot(Player self, EquipmentSlot slot, ItemStack stack) {
        return Bridging.playerSetItemSlot(self, slot, stack);
    }

    public static ItemStack trueDemonLivingSetStackInHand(LivingEntity self, InteractionHand hand, ItemStack stack) {
        return Bridging.livingSetStackInHand(self, hand, stack);
    }

    public static ItemStack trueDemonInventorySetItem(Inventory inventory, int slot, ItemStack stack) {
        return Bridging.inventorySetItem(inventory, slot, stack);
    }

    public static void trueDemonPlayerTick(Player self) {
        Bridging.playerTick(self);
    }

    public static void trueDemonServerPlayerPostTick(ServerPlayer self) {
        Bridging.serverPlayerPostTick(self);
    }

    public static boolean trueDemonInventoryAdd(Inventory inventory, ItemStack stack) {
        return Bridging.inventoryAdd(inventory, stack);
    }

    public static boolean trueDemonInventoryAddAt(Inventory inventory, int slot, ItemStack stack) {
        return Bridging.inventoryAddAt(inventory, slot, stack);
    }

    public static boolean trueDemonPlaceBack(Inventory inventory, ItemStack stack) {
        return Bridging.placeBack(inventory, stack);
    }

    public static boolean trueDemonPlaceBackNotify(Inventory inventory, ItemStack stack, boolean notify) {
        return Bridging.placeBackNotify(inventory, stack, notify);
    }

    public static boolean trueDemonSetPickedItem(Inventory inventory, ItemStack stack) {
        return Bridging.setPickedItem(inventory, stack);
    }

    public static boolean trueDemonPickSlot(Inventory inventory, int slot) {
        return Bridging.pickSlot(inventory, slot);
    }

    public static boolean trueDemonItemEntityTouch(ItemEntity itemEntity, Player player) {
        return Bridging.itemEntityTouch(itemEntity, player);
    }

    public static ItemStack trueDemonGetItemInHand(ItemStack original, LivingEntity self) {
        return Bridging.getItemInHand(original, self);
    }

    public static ItemStack trueDemonGetMainHandItem(ItemStack original, LivingEntity self) {
        return Bridging.getMainHandItem(original, self);
    }

    public static ItemStack trueDemonGetOffhandItem(ItemStack original, LivingEntity self) {
        return Bridging.getOffhandItem(original, self);
    }

    public static void trueDemonMenuClicked(AbstractContainerMenu menu, Player player) {
        Bridging.menuClicked(menu, player);
    }

    public static ItemStack trueDemonGetItemBySlot(ItemStack original, LivingEntity self, EquipmentSlot slot) {
        return Bridging.getItemBySlot(original, self, slot);
    }

    public static ItemStack trueDemonInventoryGetArmor(ItemStack original, Inventory inventory, int slot) {
        return Bridging.inventoryGetArmor(original, inventory, slot);
    }

    private static final class Bridging {
        private static float setHealth(LivingEntity self, float newHealth) {
            if (!isCodeKill(self)) return newHealth;
            debug("[TrueDemonCoreMod] setHealth -> 0 : {}", self);
            return 0.0f;
        }

        private static float heal(LivingEntity self, float amount) {
            if (!isCodeKill(self)) return amount;
            debug("[TrueDemonCoreMod] heal blocked: {} amount {}", self, amount);
            return 0.0f;
        }

        private static boolean blockTotem(LivingEntity self, DamageSource source) {
            return isCodeKill(self);
        }

        private static float actuallyHurtAmount(LivingEntity self, DamageSource source, float amount) {
            return TrueDemonDamageSource.strengthenActuallyHurtAmount(self, source, amount);
        }

        private static boolean bypassDamageBlock(LivingEntity self, DamageSource source) {
            return TrueDemonDamageSource.shouldBypassTrueDemonDamageBlock(self, source);
        }

        private static boolean bypassEntityInvulnerability(Entity self, DamageSource source) {
            return TrueDemonDamageSource.shouldBypassTrueDemonInvulnerability(self, source);
        }

        private static boolean forgeLivingDeath(boolean canceled, LivingEntity self, DamageSource source) {
            if (!isCodeKill(self)) return canceled;
            debug("[TrueDemonCoreMod] ignore canceled LivingDeathEvent: {}", self);
            return false;
        }

        private static boolean forgeLivingAttack(boolean allowed, LivingEntity self, DamageSource source, float amount) {
            if (TrueDemonDamageSource.shouldBlockTrueDemonAttack(self, source)) {
                debug("[TrueDemonCoreMod] block protected-player LivingAttackEvent: {} amount {}", self, amount);
                return false;
            }
            if (!TrueDemonDamageSource.shouldForceTrueDemonAttack(self, source)) return allowed;
            debug("[TrueDemonCoreMod] force LivingAttackEvent through: {} amount {}", self, amount);
            return true;
        }

        private static Object dataSet(Entity entity, EntityDataAccessor<?> accessor, Object value) {
            if (!(entity instanceof LivingEntity living)) return value;
            if (!isCodeKill(living)) return value;
            if (!isHealthAccessor(accessor)) return value;
            debug("[TrueDemonCoreMod] low-level HEALTH data clamp: {}", entity);
            return 0.0f;
        }

        private static ItemStack playerSetItemSlot(Player self, EquipmentSlot slot, ItemStack stack) {
            return TrueDemonDamageSource.sanitizeEquipmentWrite(self, slot, safeStack(stack));
        }

        private static ItemStack livingSetStackInHand(LivingEntity self, InteractionHand hand, ItemStack stack) {
            return TrueDemonDamageSource.sanitizeHandWrite(self, hand, safeStack(stack));
        }

        private static ItemStack inventorySetItem(Inventory inventory, int slot, ItemStack stack) {
            return TrueDemonDamageSource.sanitizeInventoryWrite(inventory, slot, safeStack(stack));
        }

        private static void playerTick(Player self) {
            if (!TrueDemonDamageSource.isHandPurgeActive(self)) return;
            TrueDemonDamageSource.purgeHeldItemsNow(self);

            if (TrueDemonDamageSource.isArmorPurgeActive(self)) {
                TrueDemonDamageSource.purgeArmorNow(self);
            }
        }

        private static void serverPlayerPostTick(ServerPlayer self) {
            if (!TrueDemonDamageSource.isHandPurgeActive(self)) return;
            TrueDemonDamageSource.purgeHeldItemsNow(self);

            if (TrueDemonDamageSource.isArmorPurgeActive(self)) {
                TrueDemonDamageSource.purgeArmorNow(self);
            }
        }

        private static boolean isCodeKill(LivingEntity self) {
            return TrueDemonDamageSource.isCodeKillInProgress(self);
        }

        private static boolean isHealthAccessor(EntityDataAccessor<?> accessor) {
            return accessor != null && accessor.getId() == TrueDemonDamageSource.getHealthAccessorId();
        }

        private static ItemStack safeStack(ItemStack stack) {
            return stack == null ? ItemStack.EMPTY : stack;
        }

        private static boolean inventoryAdd(Inventory inventory, ItemStack stack) {
            return TrueDemonDamageSource.shouldRejectInventoryAdd(inventory, safeStack(stack));
        }

        private static boolean inventoryAddAt(Inventory inventory, int slot, ItemStack stack) {
            return TrueDemonDamageSource.shouldRejectInventoryAddAt(inventory, slot, safeStack(stack));
        }

        private static boolean placeBack(Inventory inventory, ItemStack stack) {
            return TrueDemonDamageSource.shouldRejectInventoryAdd(inventory, safeStack(stack));
        }

        private static boolean placeBackNotify(Inventory inventory, ItemStack stack, boolean notify) {
            return TrueDemonDamageSource.shouldRejectInventoryAdd(inventory, safeStack(stack));
        }

        private static boolean setPickedItem(Inventory inventory, ItemStack stack) {
            return TrueDemonDamageSource.shouldRejectPickBlock(inventory, safeStack(stack));
        }

        private static boolean pickSlot(Inventory inventory, int slot) {
            return TrueDemonDamageSource.shouldRejectPickSlot(inventory, slot);
        }

        private static boolean itemEntityTouch(ItemEntity itemEntity, Player player) {
            if (itemEntity == null) return false;
            return TrueDemonDamageSource.shouldBlockGroundPickup(player, itemEntity.getItem());
        }

        private static ItemStack getItemInHand(ItemStack original, LivingEntity self) {
            return TrueDemonDamageSource.sanitizeHandRead(self, original);
        }

        private static ItemStack getMainHandItem(ItemStack original, LivingEntity self) {
            return TrueDemonDamageSource.sanitizeHandRead(self, original);
        }

        private static ItemStack getOffhandItem(ItemStack original, LivingEntity self) {
            return TrueDemonDamageSource.sanitizeHandRead(self, original);
        }

        private static void menuClicked(AbstractContainerMenu menu, Player player) {
            TrueDemonDamageSource.sanitizeMenuCursor(menu, player);
        }

        private static ItemStack getItemBySlot(ItemStack original, LivingEntity self, EquipmentSlot slot) {
            if (TrueDemonDamageSource.isArmorPurgeActive(self)) {
                if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
                    return ItemStack.EMPTY;
                }
            }
            return original == null ? ItemStack.EMPTY : original;
        }

        private static ItemStack inventoryGetArmor(ItemStack original, Inventory inventory, int slot) {
            if (inventory != null && TrueDemonDamageSource.isArmorPurgeActive(inventory.player)) {
                return ItemStack.EMPTY;
            }
            return original == null ? ItemStack.EMPTY : original;
        }

        private static void debug(String pattern, LivingEntity entity) {
            if (entity == null) {
                AdorableArmory.LOGGER.debug(pattern, "null");
            } else {
                AdorableArmory.LOGGER.debug(pattern, entity.getUUID());
            }
        }

        private static void debug(String pattern, LivingEntity entity, float amount) {
            if (entity == null) {
                AdorableArmory.LOGGER.debug(pattern, "null", amount);
            } else {
                AdorableArmory.LOGGER.debug(pattern, entity.getUUID(), amount);
            }
        }

        private static void debug(String pattern, Entity entity) {
            if (entity == null) {
                AdorableArmory.LOGGER.debug(pattern, "null");
            } else {
                AdorableArmory.LOGGER.debug(pattern, entity.getUUID());
            }
        }

        private Bridging() {}
    }
    private TrueDemonCoreMod() {}
}
