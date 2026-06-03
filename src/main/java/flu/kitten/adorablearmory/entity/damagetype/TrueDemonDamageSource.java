package flu.kitten.adorablearmory.entity.damagetype;

import com.mojang.datafixers.util.Pair;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.CommonConfig;
import flu.kitten.adorablearmory.api.duck.ITrueDemonExecutionTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TrueDemonDamageSource extends DamageSource {

    public static final ResourceKey<DamageType> TRUE_DEMON_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation(AdorableArmory.MODID, "true_demon"));
    private static final ConcurrentHashMap<Integer, EntityDamageInfo> PENDING_TRUE_DAMAGE = new ConcurrentHashMap<>();
    private static final ConcurrentMap<UUID, TrueDemonExecutionState> EXECUTION_STATES = new ConcurrentHashMap<>();
    private static final EntityDataAccessor<Float> HEALTH_ACCESSOR = resolveHealthAccessor();
    private static final MethodHandle DATA_ITEMS_GETTER;
    private static final MethodHandle ITEM_VALUE_SETTER;
    private static final MethodHandle ITEM_DIRTY_SETTER;
    private static final MethodHandle ITEM_ACCESSOR_GETTER;
    private static final VarHandle LIVING_USE_ITEM_HANDLE;
    private static final VarHandle LIVING_USE_ITEM_REMAINING_HANDLE;
    private static final VarHandle MENU_CARRIED_HANDLE;

    private TrueDemonDamageSource(Holder<DamageType> damageType, @Nullable Entity direct, @Nullable Entity causing) {
        super(damageType, direct, causing);
    }

    public static DamageSource causeTrueDemonDamage(Level level, @Nullable Entity source) {
        return new TrueDemonDamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TRUE_DEMON_TYPE), source, source);
    }

    public static DamageSource causeTrueDemonDamage(Level level, @Nullable Entity direct, @Nullable Entity indirect) {
        return new TrueDemonDamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TRUE_DEMON_TYPE), direct, indirect);
    }

    private static final int REPEAT_KILL_MAX_ATTEMPTS = 12;

    static {
        MethodHandle itemsGetter = null;
        MethodHandle valueSetter = null;
        MethodHandle dirtySetter = null;
        MethodHandle accessorGetter = null;
        VarHandle useItemHandle = null;
        VarHandle useItemRemainingHandle = null;
        VarHandle menuCarriedHandle = null;

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            Field itemsField = ObfuscationReflectionHelper.findField(SynchedEntityData.class, "f_135345_"); // itemsById
            itemsField.setAccessible(true);
            itemsGetter = lookup.unreflectGetter(itemsField);

            Class<?> dataItemClass = null;
            for (Class<?> inner : SynchedEntityData.class.getDeclaredClasses()) {
                boolean hasAccessorField = false;
                boolean hasDirtyField = false;

                for (Field field : inner.getDeclaredFields()) {
                    if (EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                        hasAccessorField = true;
                    }
                    if (field.getType() == boolean.class) {
                        hasDirtyField = true;
                    }
                }

                if (hasAccessorField && hasDirtyField) {
                    dataItemClass = inner;
                    break;
                }
            }

            if (dataItemClass != null) {
                Field valueField = null;
                Field dirtyField = null;
                Field accessorField = null;

                for (Field field : dataItemClass.getDeclaredFields()) {
                    field.setAccessible(true);

                    if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;

                    if (field.getType() == boolean.class) {
                        dirtyField = field;
                    } else if (EntityDataAccessor.class.isAssignableFrom(field.getType())) {
                        accessorField = field;
                    } else {
                        valueField = field;
                    }
                }

                if (valueField != null && dirtyField != null && accessorField != null) {
                    valueSetter = lookup.unreflectSetter(valueField);
                    dirtySetter = lookup.unreflectSetter(dirtyField);
                    accessorGetter = lookup.unreflectGetter(accessorField);
                }
            }
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] Failed to initialize SynchedEntityData DataItem hooks", t);
        }

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandles.Lookup livingLookup = MethodHandles.privateLookupIn(LivingEntity.class, lookup);

            Field useItemField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20935_"); // useItem
            useItemField.setAccessible(true);
            if (useItemField.getType() == ItemStack.class) {
                useItemHandle = livingLookup.unreflectVarHandle(useItemField);
            }

            Field useItemRemainingField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20936_"); // useItemRemaining
            useItemRemainingField.setAccessible(true);
            if (useItemRemainingField.getType() == int.class) {
                useItemRemainingHandle = livingLookup.unreflectVarHandle(useItemRemainingField);
            }
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] Failed to initialize LivingEntity held-use VarHandles", t);
        }

        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandles.Lookup menuLookup = MethodHandles.privateLookupIn(AbstractContainerMenu.class, lookup);

            Field carriedField = ObfuscationReflectionHelper.findField(AbstractContainerMenu.class, "f_150393_"); // carried
            carriedField.setAccessible(true);
            if (carriedField.getType() == ItemStack.class) {
                menuCarriedHandle = menuLookup.unreflectVarHandle(carriedField);
            }
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] Failed to initialize AbstractContainerMenu carried VarHandle", t);
        }

        DATA_ITEMS_GETTER = itemsGetter;
        ITEM_VALUE_SETTER = valueSetter;
        ITEM_DIRTY_SETTER = dirtySetter;
        ITEM_ACCESSOR_GETTER = accessorGetter;
        LIVING_USE_ITEM_HANDLE = useItemHandle;
        LIVING_USE_ITEM_REMAINING_HANDLE = useItemRemainingHandle;
        MENU_CARRIED_HANDLE = menuCarriedHandle;
    }

    public static void stopRepeatingCodeKill(@Nullable LivingEntity entity) {
        if (entity == null) return;
        TrueDemonExecutionState state = executionState(entity);
        if (state != null) {
            state.stopRetry();
        }
    }

    @SuppressWarnings("unchecked")
    private static boolean directWriteHealthDataItem(LivingEntity target, float health) {
        if (target == null || HEALTH_ACCESSOR == null) return false;
        if (DATA_ITEMS_GETTER == null || ITEM_VALUE_SETTER == null || ITEM_DIRTY_SETTER == null || ITEM_ACCESSOR_GETTER == null) {
            return false;
        }

        try {
            SynchedEntityData entityData = target.getEntityData();
            Map<Integer, ?> itemsMap = (Map<Integer, ?>) DATA_ITEMS_GETTER.invoke(entityData);
            if (itemsMap == null) return false;

            Object dataItem = itemsMap.get(HEALTH_ACCESSOR.getId());
            if (dataItem == null) return false;

            Object accessor = ITEM_ACCESSOR_GETTER.invoke(dataItem);
            if (accessor != HEALTH_ACCESSOR) return false;

            ITEM_VALUE_SETTER.invoke(dataItem, health);
            ITEM_DIRTY_SETTER.invoke(dataItem, true);

            AdorableArmory.LOGGER.debug("[TrueDemon] DataItem direct write applied: {} -> {}", target.getUUID(), health);
            return true;
        } catch (Throwable t) {
            AdorableArmory.LOGGER.debug("[TrueDemon] directWriteHealthDataItem failed for {}", target.getClass().getName(), t);
            return false;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static EntityDataAccessor<Float> resolveHealthAccessor() {
        try {
            Field healthField = ObfuscationReflectionHelper.findField(LivingEntity.class, "f_20961_"); // DATA_HEALTH_ID
            healthField.setAccessible(true);
            return (EntityDataAccessor<Float>) healthField.get(null);
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] Failed to resolve LivingEntity.DATA_HEALTH_ID", t);
            return null;
        }
    }

    @Nullable
    private static TrueDemonExecutionState executionState(@Nullable LivingEntity entity) {
        return entity == null ? null : EXECUTION_STATES.get(entity.getUUID());
    }

    private static TrueDemonExecutionState executionState(LivingEntity entity, @Nullable Entity source) {
        return EXECUTION_STATES.compute(entity.getUUID(), (uuid, current) -> {
            TrueDemonExecutionState state = current == null ? new TrueDemonExecutionState(source) : current;
            state.refresh(source);
            return state;
        });
    }

    private static void transitionExecution(@Nullable LivingEntity entity, ExecutionPhase phase) {
        TrueDemonExecutionState state = executionState(entity);
        if (state != null) {
            state.transitionTo(phase);
        }
    }

    public static void armDeathGuard(LivingEntity target) {
        if (target != null) {
            executionState(target, null).armDeathGuard();
        }
    }

    public static boolean isGuardActive(@Nullable LivingEntity entity) {
        TrueDemonExecutionState state = executionState(entity);
        return state != null && state.deathGuard;
    }

    public static boolean isCodeKillInProgress(@Nullable LivingEntity entity) {
        if (entity == null) return false;
        TrueDemonExecutionState state = executionState(entity);
        return state != null && state.blocksCodeKillHooks();
    }

    public static boolean isTrueDemonDamage(@Nullable DamageSource source) {
        return source instanceof TrueDemonDamageSource || (source != null && source.is(TRUE_DEMON_TYPE));
    }

    private static boolean isProtectedPlayerMode(@Nullable LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return false;
        if (CommonConfig.TRUE_DEMON_HITS_CREATIVE.get()) return false;
        return player.isCreative() || player.isSpectator() || player.isInvulnerable();
    }

    private static boolean canApplyTrueDemonTo(@Nullable LivingEntity entity) {
        return entity != null && !entity.isRemoved() && !entity.level().isClientSide() && !isProtectedPlayerMode(entity);
    }

    private static boolean shouldForceTrueDemonEvent(@Nullable LivingEntity entity, @Nullable DamageSource source) {
        if (entity == null || isProtectedPlayerMode(entity)) return false;
        return isTrueDemonDamage(source) || isGuardActive(entity) || isCodeKillInProgress(entity);
    }

    private static boolean shouldRaiseToFatalAmount(@Nullable LivingEntity entity) {
        TrueDemonExecutionState state = executionState(entity);
        return entity != null && !isProtectedPlayerMode(entity) && state != null && state.blocksCodeKillHooks();
    }

    public static boolean shouldForceTrueDemonAttack(@Nullable LivingEntity entity, @Nullable DamageSource source) {
        return shouldForceTrueDemonEvent(entity, source);
    }

    public static boolean shouldBlockTrueDemonAttack(@Nullable LivingEntity entity, @Nullable DamageSource source) {
        return isProtectedPlayerMode(entity) && isTrueDemonDamage(source);
    }

    public static float strengthenActuallyHurtAmount(@Nullable LivingEntity entity, @Nullable DamageSource source, float amount) {
        if (entity == null || isProtectedPlayerMode(entity)) return amount;
        if (!shouldRaiseToFatalAmount(entity)) return amount;

        disableInvulnerability(entity);
        setAbsorptionZero(entity);
        transitionExecution(entity, ExecutionPhase.FORCE_HEALTH_ZERO);
        return Math.max(amount, getFatalEventAmount(entity));
    }

    public static boolean shouldBypassTrueDemonDamageBlock(@Nullable LivingEntity entity, @Nullable DamageSource source) {
        return shouldForceTrueDemonEvent(entity, source);
    }

    public static boolean shouldBypassTrueDemonInvulnerability(@Nullable Entity entity, @Nullable DamageSource source) {
        if (!(entity instanceof LivingEntity living)) return false;
        return shouldForceTrueDemonEvent(living, source);
    }

    public static void armHandPurge(LivingEntity target) {
        if (target != null) {
            executionState(target, null).handPurge = true;
        }
    }

    public static boolean isHandPurgeActive(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        TrueDemonExecutionState state = executionState(living);
        return (state != null && state.handPurge) || isCodeKillInProgress(living);
    }

    public static boolean shouldVoidEquipmentSlot(@Nullable Entity entity, @Nullable EquipmentSlot slot) {
        if (slot == null) return false;

        if (isHandEquipmentSlot(slot) && isHandPurgeActive(entity)) {
            return true;
        }

        return isArmorEquipmentSlot(slot) && isArmorPurgeActive(entity);
    }

    public static boolean shouldVoidHand(@Nullable Entity entity, @Nullable InteractionHand hand) {
        return hand != null && isHandPurgeActive(entity);
    }

    public static ItemStack sanitizeEquipmentWrite(@Nullable Entity entity, @Nullable EquipmentSlot slot, ItemStack incoming) {
        return shouldVoidEquipmentSlot(entity, slot) ? ItemStack.EMPTY : incoming;
    }

    public static ItemStack sanitizeHandWrite(@Nullable Entity entity, @Nullable InteractionHand hand, ItemStack incoming) {
        return shouldVoidHand(entity, hand) ? ItemStack.EMPTY : incoming;
    }

    public static ItemStack sanitizeInventoryWrite(@Nullable Inventory inventory, int slot, ItemStack incoming) {
        if (inventory == null) return incoming;

        if (isHandPurgeActive(inventory.player)) {
            if (slot == inventory.selected || slot == Inventory.SLOT_OFFHAND) {
                return ItemStack.EMPTY;
            }
        }

        if (isArmorPurgeActive(inventory.player) && isArmorInventoryRawSlot(inventory, slot)) {
            return ItemStack.EMPTY;
        }

        return incoming;
    }

    public static boolean shouldRejectAnyItemGain(@Nullable Player player, @Nullable ItemStack stack) {
        return isHandPurgeActive(player) && stack != null && !stack.isEmpty();
    }

    public static boolean shouldRejectInventoryAdd(@Nullable Inventory inventory, @Nullable ItemStack stack) {
        return inventory != null && shouldRejectAnyItemGain(inventory.player, stack);
    }

    public static boolean shouldRejectInventoryAddAt(@Nullable Inventory inventory, int slot, @Nullable ItemStack stack) {
        if (inventory == null) return false;
        if (!shouldRejectAnyItemGain(inventory.player, stack)) return false;
        return slot == inventory.selected || slot == Inventory.SLOT_OFFHAND;
    }

    public static boolean shouldRejectPickBlock(@Nullable Inventory inventory, @Nullable ItemStack stack) {
        return inventory != null && shouldRejectAnyItemGain(inventory.player, stack);
    }

    public static boolean shouldRejectPickSlot(@Nullable Inventory inventory, int slot) {
        return inventory != null && isHandPurgeActive(inventory.player);
    }

    public static boolean shouldBlockGroundPickup(@Nullable Player player, @Nullable ItemStack stack) {
        return shouldRejectAnyItemGain(player, stack);
    }

    public static ItemStack sanitizeHandRead(@Nullable LivingEntity entity, @Nullable ItemStack original) {
        if (isHandPurgeActive(entity)) {
            return ItemStack.EMPTY;
        }
        return original == null ? ItemStack.EMPTY : original;
    }

    private static void directClearUseItem(@Nullable LivingEntity entity) {
        if (entity == null) return;

        boolean wroteUseItem = false;
        boolean wroteUseTicks = false;

        if (LIVING_USE_ITEM_HANDLE != null) {
            try {
                LIVING_USE_ITEM_HANDLE.set(entity, ItemStack.EMPTY);
                wroteUseItem = true;
            } catch (Throwable ignored) {
            }
        }

        if (LIVING_USE_ITEM_REMAINING_HANDLE != null) {
            try {
                LIVING_USE_ITEM_REMAINING_HANDLE.set(entity, 0);
                wroteUseTicks = true;
            } catch (Throwable ignored) {
            }
        }

        if (!wroteUseItem || !wroteUseTicks) {
            try {
                entity.stopUsingItem();
            } catch (Throwable ignored) {
            }

            if (LIVING_USE_ITEM_HANDLE != null) {
                try {
                    LIVING_USE_ITEM_HANDLE.set(entity, ItemStack.EMPTY);
                } catch (Throwable ignored) {
                }
            }

            if (LIVING_USE_ITEM_REMAINING_HANDLE != null) {
                try {
                    LIVING_USE_ITEM_REMAINING_HANDLE.set(entity, 0);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static void clearHeldInventorySlots(@Nullable Player player, @Nullable Inventory inventory) {
        if (player == null || inventory == null) return;

        try {
            if (inventory.selected >= 0 && inventory.selected < inventory.items.size()) {
                inventory.items.set(inventory.selected, ItemStack.EMPTY);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (!inventory.offhand.isEmpty()) {
                inventory.offhand.set(0, ItemStack.EMPTY);
            }
        } catch (Throwable ignored) {
        }

        try {
            inventory.setChanged();
        } catch (Throwable ignored) {
        }
    }

    private static void clearMenuCarried(@Nullable AbstractContainerMenu menu) {
        if (menu == null) return;

        boolean clearedDirectly = false;
        if (MENU_CARRIED_HANDLE != null) {
            try {
                MENU_CARRIED_HANDLE.set(menu, ItemStack.EMPTY);
                clearedDirectly = true;
            } catch (Throwable ignored) {
            }
        }

        if (!clearedDirectly) {
            try {
                menu.setCarried(ItemStack.EMPTY);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void broadcastMenu(@Nullable AbstractContainerMenu menu) {
        if (menu == null) return;
        try {
            menu.broadcastChanges();
        } catch (Throwable ignored) {
        }
    }

    private static void syncHeldItemPurge(ServerPlayer serverPlayer) {
        try {
            serverPlayer.connection.send(new ClientboundSetEquipmentPacket(serverPlayer.getId(), List.of(Pair.of(EquipmentSlot.MAINHAND, ItemStack.EMPTY), Pair.of(EquipmentSlot.OFFHAND, ItemStack.EMPTY))));
        } catch (Throwable t) {
            AdorableArmory.LOGGER.debug("[TrueDemon] equipment sync after purge failed for {}", serverPlayer.getUUID(), t);
        }

        try {
            Inventory inventory = serverPlayer.getInventory();
            if (inventory.selected >= 0 && inventory.selected < inventory.items.size()) {
                serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(-2, serverPlayer.inventoryMenu.getStateId(), inventory.selected, ItemStack.EMPTY));
            }
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(-2, serverPlayer.inventoryMenu.getStateId(), Inventory.SLOT_OFFHAND, ItemStack.EMPTY));
        } catch (Throwable t) {
            AdorableArmory.LOGGER.debug("[TrueDemon] inventory slot sync after purge failed for {}", serverPlayer.getUUID(), t);
        }

        try {
            serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(-1, serverPlayer.containerMenu.getStateId(), -1, ItemStack.EMPTY));
        } catch (Throwable t) {
            AdorableArmory.LOGGER.debug("[TrueDemon] carried item sync after purge failed for {}", serverPlayer.getUUID(), t);
        }
    }

    public static void sanitizeMenuCursor(@Nullable AbstractContainerMenu menu, @Nullable Player player) {
        if (menu == null || player == null) return;
        if (!isHandPurgeActive(player)) return;

        clearMenuCarried(menu);
        broadcastMenu(menu);
        purgeHeldItemsNow(player);
    }

    public static void armArmorPurge(LivingEntity target) {
        if (target != null) {
            executionState(target, null).armorPurge = true;
        }
    }

    public static boolean isArmorPurgeActive(@Nullable Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        TrueDemonExecutionState state = executionState(living);
        return (state != null && state.armorPurge) || isCodeKillInProgress(living);
    }

    private static boolean isArmorEquipmentSlot(@Nullable EquipmentSlot slot) {
        return slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
    }

    private static boolean isHandEquipmentSlot(@Nullable EquipmentSlot slot) {
        return slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
    }

    private static boolean isArmorInventoryRawSlot(@Nullable Inventory inventory, int slot) {
        if (inventory == null) return false;

        int armorStart = inventory.items.size();
        int armorEnd = armorStart + inventory.armor.size();

        return slot >= armorStart && slot < armorEnd;
    }

    public static int getHealthAccessorId() {
        return HEALTH_ACCESSOR != null ? HEALTH_ACCESSOR.getId() : -1;
    }

    public static void clearAllTracking(Entity entity) {
        if (entity == null) return;
        PENDING_TRUE_DAMAGE.remove(entity.getId());
        if (entity instanceof LivingEntity living) {
            EXECUTION_STATES.remove(living.getUUID());
        }
    }

    public static boolean applyTrueDemonDamage(LivingEntity target, float amount, @Nullable Entity source, Level level) {
        if (target == null || target.isRemoved() || amount <= 0.0f) return false;
        if (level.isClientSide()) return false;
        if (!canApplyTrueDemonTo(target)) return false;

        EntityDamageInfo info = new EntityDamageInfo(target.getId(), amount, System.currentTimeMillis());
        PENDING_TRUE_DAMAGE.put(target.getId(), info);
        transitionExecution(target, ExecutionPhase.DAMAGE_PIPELINE);

        try {
            disableInvulnerability(target);

            float before = target.getHealth();
            float expectedAfter = Math.max(0.0f, before - amount);
            DamageSource damageSource = causeTrueDemonDamage(level, source);

            boolean hurtResult = false;
            try {
                hurtResult = target.hurt(damageSource, amount);
            } catch (Throwable t) {
                AdorableArmory.LOGGER.debug("[TrueDemon] target.hurt threw in applyTrueDemonDamage for {}", target.getClass().getName(), t);
            }

            float currentAfter = target.getHealth();

            if (!hurtResult || currentAfter > expectedAfter + 0.001f) {
                float missing = currentAfter - expectedAfter;
                if (missing > 0.001f) {
                    forceHealthReduction(target, missing, damageSource);
                }
            }

            if (isLikelyBoss(target) && target.isAlive() && !target.isRemoved()) {
                float afterForce = target.getHealth();
                if (afterForce > expectedAfter + 0.001f) {
                    float stillMissing = afterForce - expectedAfter;
                    handleSpecialBossTypes(target, stillMissing, damageSource);
                }
            }

            return true;
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] Failed to apply true demon damage to {}", target.getClass().getName(), t);
            return forceHealthReduction(target, amount, causeTrueDemonDamage(level, source));
        } finally {
            PENDING_TRUE_DAMAGE.remove(target.getId());
        }
    }

    private static boolean forceHealthReduction(LivingEntity target, float amount, DamageSource damageSource) {
        try {
            transitionExecution(target, ExecutionPhase.FORCE_HEALTH_ZERO);
            float current = target.getHealth();
            float newHealth = Math.max(0.0f, current - Math.max(0.0f, amount));

            disableInvulnerability(target);
            setAbsorptionZero(target);
            target.setHealth(newHealth);
            forceSyncedHealth(target, newHealth);

            if (newHealth <= 0.0f) {
                killEntityReliable(target, damageSource);
            }
            return true;
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] forceHealthReduction failed for {}", target.getClass().getName(), t);
            return false;
        }
    }

    public static void forceSyncedHealth(LivingEntity entity, float health) {
        if (entity == null) return;
        try {
            entity.setHealth(health);
        } catch (Throwable ignored) {
        }

        if (HEALTH_ACCESSOR != null) {
            try {
                entity.getEntityData().set(HEALTH_ACCESSOR, health);
            } catch (Throwable t) {
                AdorableArmory.LOGGER.debug("[TrueDemon] forceSyncedHealth(entityData.set) failed for {}", entity.getClass().getName(), t);
            }
        }
    }

    private static boolean isLikelyBoss(LivingEntity entity) {
        return entity instanceof WitherBoss || entity instanceof EnderDragon || entity.getMaxHealth() > 50.0f;
    }

    private static void handleSpecialBossTypes(LivingEntity target, float missing, DamageSource damageSource) {
        if (missing <= 0.001f || target.isRemoved() || !target.isAlive()) return;

        try {
            disableInvulnerability(target);
            setAbsorptionZero(target);

            float desired = Math.max(0.0f, target.getHealth() - missing);
            target.setHealth(desired);
            forceSyncedHealth(target, desired);

            if (desired <= 0.0f || target.getHealth() <= 0.0f) {
                killEntityReliable(target, damageSource);
            }
        } catch (Throwable t) {
            AdorableArmory.LOGGER.debug("[TrueDemon] handleSpecialBossTypes failed for {}", target.getClass().getName(), t);
        }
    }

    private static boolean forceZeroHealthViaNbt(LivingEntity target) {
        if (target == null || target.level().isClientSide()) return false;

        try {
            CompoundTag tag = new CompoundTag();
            target.saveWithoutId(tag);

            tag.putFloat("Health", 0.0f);
            target.load(tag);

            AdorableArmory.LOGGER.debug("[TrueDemon] NBT zero-health injection applied: {}", target.getUUID());
            return true;
        } catch (Throwable t) {
            AdorableArmory.LOGGER.debug("[TrueDemon] forceZeroHealthViaNbt failed for {}", target.getClass().getName(), t);
            return false;
        }
    }

    private static boolean runTerminalHealthFallbacks(LivingEntity target, DamageSource damageSource) {
        if (target == null || target.level().isClientSide()) return false;
        if (isFinalized(target)) return true;

        transitionExecution(target, ExecutionPhase.FORCE_HEALTH_ZERO);
        boolean touched = false;

        if (!isFinalized(target)) {
            touched = directWriteHealthDataItem(target, 0.0f);
        }


        if (!isFinalized(target)) {
            touched |= forceZeroHealthViaNbt(target);
        }

        if (touched) {
            forceSyncedHealth(target, 0.0f);

            if (target instanceof ServerPlayer serverPlayer) {
                try {
                    serverPlayer.connection.send(new ClientboundSetHealthPacket(0.0f, serverPlayer.getFoodData().getFoodLevel(), serverPlayer.getFoodData().getSaturationLevel()));
                } catch (Throwable t) {
                    AdorableArmory.LOGGER.debug("[TrueDemon] post-hack player health sync failed for {}", serverPlayer.getUUID(), t);
                }
            }
        }

        if (!isFinalized(target)) {
            killEntityReliable(target, damageSource);
        }

        return isFinalized(target);
    }

    private static void disableInvulnerability(LivingEntity entity) {
        try {
            entity.invulnerableTime = 0;
        } catch (Throwable ignored) {
        }
    }

    private static void setAbsorptionZero(LivingEntity entity) {
        try {
            entity.setAbsorptionAmount(0.0f);
        } catch (Throwable ignored) {
        }
    }

    private static void killEntityReliable(LivingEntity target, DamageSource damageSource) {
        transitionExecution(target, ExecutionPhase.DEATH_COMMIT);
        if (target instanceof ServerPlayer serverPlayer) {
            killPlayerReliable(serverPlayer, damageSource);
        } else {
            killNonPlayerReliable(target, damageSource);
        }
    }

    private static void killPlayerReliable(ServerPlayer player, DamageSource damageSource) {
        try {
            disableInvulnerability(player);
            setAbsorptionZero(player);
            forceSyncedHealth(player, 0.0f);

            try {
                player.connection.send(new ClientboundSetHealthPacket(0.0f, player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()));
            } catch (Throwable ignored) {
            }

            try {
                player.die(damageSource);
            } catch (Throwable t) {
                AdorableArmory.LOGGER.debug("[TrueDemon] player.die failed for {}", player.getUUID(), t);
            }
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] killPlayerReliable failed for {}", player.getUUID(), t);
        }
    }

    private static void killNonPlayerReliable(LivingEntity target, DamageSource damageSource) {
        try {
            disableInvulnerability(target);
            setAbsorptionZero(target);
            forceSyncedHealth(target, 0.0F);

            try {
                target.die(damageSource);
            } catch (Throwable t) {
                AdorableArmory.LOGGER.debug("[TrueDemon] target.die failed for {}", target.getClass().getName(), t);
            }

            if (!target.isRemoved() && target.getHealth() <= 0.0F) {
                try {
                    target.kill();
                } catch (Throwable t) {
                    AdorableArmory.LOGGER.debug("[TrueDemon] target.kill failed for {}", target.getClass().getName(), t);
                }
            }

            if (!target.isRemoved() && target.getHealth() <= 0.0F) {
                try {
                    target.remove(Entity.RemovalReason.KILLED);
                } catch (Throwable t) {
                    AdorableArmory.LOGGER.debug("[TrueDemon] target.remove(KILLED) failed for {}", target.getClass().getName(), t);
                }
            }
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] killNonPlayerReliable failed for {}", target.getClass().getName(), t);
        }
    }

    private static boolean isFinalized(LivingEntity target) {
        return target == null || target.isRemoved() || !target.isAlive() || target.getHealth() <= 0.0f;
    }

    private static boolean isExecutionMarked(LivingEntity target) {
        return target instanceof ITrueDemonExecutionTarget executionTarget && executionTarget.isMarkedForExecution();
    }

    private static float getFatalEventAmount(LivingEntity entity) {
        return Math.max(entity.getHealth() + entity.getAbsorptionAmount() + 1.0f, 1024.0f);
    }

    public static boolean trueDemonCodeKill(LivingEntity target, @Nullable Entity source) {
        if (target == null || target.isRemoved()) return false;
        if (target.level().isClientSide()) return false;
        if (!canApplyTrueDemonTo(target)) return false;

        TrueDemonExecutionState state = executionState(target, source);
        state.armFullExecution();
        purgeHeldItemsNow(target);
        purgeArmorNow(target);

        PENDING_TRUE_DAMAGE.put(target.getId(), new EntityDamageInfo(target.getId(), getFatalEventAmount(target), System.currentTimeMillis()));
        DamageSource damageSource = causeTrueDemonDamage(target.level(), source);

        try {
            disableInvulnerability(target);
            setAbsorptionZero(target);

            try {
                state.transitionTo(ExecutionPhase.DAMAGE_PIPELINE);
                target.hurt(damageSource, getFatalEventAmount(target));
            } catch (Throwable t) {
                AdorableArmory.LOGGER.debug("[TrueDemon] target.hurt threw in trueDemonCodeKill for {}", target.getClass().getName(), t);
            }

            purgeHeldItemsNow(target);
            purgeArmorNow(target);

            if (!isFinalized(target)) {
                float remaining = Math.max(target.getHealth(), 1.0f);
                forceHealthReduction(target, remaining, damageSource);
            }

            killEntityReliable(target, damageSource);

            if (!isFinalized(target)) {
                runTerminalHealthFallbacks(target, damageSource);
            }

            if (!isFinalized(target) && target instanceof ITrueDemonExecutionTarget executionTarget) {
                executionTarget.markForExecution();
                AdorableArmory.LOGGER.info("[TrueDemon] executionMark armed after terminal fallbacks. target={}, source={}", target.getUUID(), source != null ? source.getUUID() : "null");
            }

            boolean finalized = isFinalized(target) || isExecutionMarked(target);

            if (!finalized && target instanceof ServerPlayer player && player.getHealth() > 0.0f) {
                startRepeatingCodeKillIfNeeded(player, source);
            } else {
                stopRepeatingCodeKill(target);
                state.transitionTo(ExecutionPhase.CLEANUP);
                clearAllTracking(target);
            }

            return finalized;
        } finally {
            PENDING_TRUE_DAMAGE.remove(target.getId());
        }
    }

    private static void startRepeatingCodeKillIfNeeded(@Nullable LivingEntity entity, @Nullable Entity source) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (player.isRemoved()) return;
        if (isProtectedPlayerMode(player)) return;

        if (player.getHealth() <= 0.0f || !player.isAlive()) {
            stopRepeatingCodeKill(player);
            return;
        }

        executionState(player, source).startRetry(source);
    }

    private static void runRepeatingCodeKillTick(ServerPlayer player, TrueDemonExecutionState state) {
        UUID uuid = player.getUUID();

        try {
            if (player.isRemoved() || player.level().isClientSide() || isProtectedPlayerMode(player)) {
                clearAllTracking(player);
                return;
            }

            if (isFinalized(player) || isExecutionMarked(player)) {
                state.transitionTo(ExecutionPhase.CLEANUP);
                clearAllTracking(player);
                return;
            }

            if (++state.attempts > REPEAT_KILL_MAX_ATTEMPTS) {
                AdorableArmory.LOGGER.warn("[TrueDemon] repeating code kill reached max tick attempts for {}", uuid);
                if (player instanceof ITrueDemonExecutionTarget executionTarget) {
                    executionTarget.markForExecution();
                }
                runTerminalHealthFallbacks(player, causeTrueDemonDamage(player.level(), state.source));
                state.transitionTo(ExecutionPhase.CLEANUP);
                clearAllTracking(player);
                return;
            }

            state.transitionTo(ExecutionPhase.BLOCK_RECOVERY);
            armDeathGuard(player);
            armHandPurge(player);
            armArmorPurge(player);
            purgeHeldItemsNow(player);
            purgeArmorNow(player);
            disableInvulnerability(player);
            setAbsorptionZero(player);

            TrueDemonTypes.TrueDemonDamageUtil.trueDemonMechanismKill(player, state.source);

            if (isFinalized(player) || isExecutionMarked(player)) {
                state.transitionTo(ExecutionPhase.CLEANUP);
                clearAllTracking(player);
            }
        } catch (Throwable t) {
            AdorableArmory.LOGGER.error("[TrueDemon] repeating code kill tick failed for {}", uuid, t);
            clearAllTracking(player);
        }
    }

    public static void purgeHeldItemsNow(@Nullable LivingEntity entity) {
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        Inventory inventory = player.getInventory();

        clearHeldInventorySlots(player, inventory);

        try {
            player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } catch (Throwable ignored) {}

        try {
            player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } catch (Throwable ignored) {}

        clearHeldInventorySlots(player, inventory);
        directClearUseItem(player);
        clearMenuCarried(player.inventoryMenu);
        clearMenuCarried(player.containerMenu);

        broadcastMenu(player.inventoryMenu);

        if (player.containerMenu != player.inventoryMenu) {
            broadcastMenu(player.containerMenu);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            syncHeldItemPurge(serverPlayer);
        }
    }

    public static void purgeArmorNow(@Nullable LivingEntity entity) {
        if (!(entity instanceof Player player)) return;
        if (player.level().isClientSide()) return;

        Inventory inventory = player.getInventory();

        try {
            player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        } catch (Throwable ignored) {}

        try {
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        } catch (Throwable ignored) {}

        try {
            player.setItemSlot(EquipmentSlot.LEGS, ItemStack.EMPTY);
        } catch (Throwable ignored) {}

        try {
            player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        } catch (Throwable ignored) {}

        try {
            Collections.fill(inventory.armor, ItemStack.EMPTY);
        } catch (Throwable ignored) {}

        try {
            inventory.setChanged();
        } catch (Throwable ignored) {}

        try {
            player.inventoryMenu.broadcastChanges();
        } catch (Throwable ignored) {}

        try {
            if (player.containerMenu != player.inventoryMenu) {
                player.containerMenu.broadcastChanges();
            }
        } catch (Throwable ignored) {}

        if (player instanceof ServerPlayer serverPlayer) {
            try {
                serverPlayer.connection.send(new ClientboundSetEquipmentPacket(serverPlayer.getId(), List.of(Pair.of(EquipmentSlot.HEAD, ItemStack.EMPTY), Pair.of(EquipmentSlot.CHEST, ItemStack.EMPTY), Pair.of(EquipmentSlot.LEGS, ItemStack.EMPTY), Pair.of(EquipmentSlot.FEET, ItemStack.EMPTY))));
            } catch (Throwable t) {
                AdorableArmory.LOGGER.debug("[TrueDemon] armor sync after purge failed for {}", serverPlayer.getUUID(), t);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void attackHigh(LivingAttackEvent event) {
        if (shouldForceTrueDemonEvent(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void attackLow(LivingAttackEvent event) {
        if (shouldForceTrueDemonEvent(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void hurtHigh(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        if (shouldRaiseToFatalAmount(entity)) {
            event.setCanceled(false);
            float fatal = getFatalEventAmount(entity);
            if (event.getAmount() < fatal) {
                event.setAmount(fatal);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void livingHurt(LivingHurtEvent event) {
        LivingEntity entity = event.getEntity();
        EntityDamageInfo info = PENDING_TRUE_DAMAGE.get(entity.getId());

        if (shouldForceTrueDemonEvent(entity, event.getSource())) {
            event.setCanceled(false);

            float desired = event.getAmount();
            if (info != null) {
                desired = Math.max(desired, info.amount());
            }
            if (shouldRaiseToFatalAmount(entity)) {
                desired = Math.max(desired, getFatalEventAmount(entity));
            }

            if (event.getAmount() < desired) {
                event.setAmount(desired);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void damageHigh(LivingDamageEvent event) {
        if (shouldForceTrueDemonEvent(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void livingDamage(LivingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        if (shouldForceTrueDemonEvent(entity, event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void damageLow(LivingDamageEvent event) {
        if (shouldForceTrueDemonEvent(event.getEntity(), event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void deathLow(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (shouldForceTrueDemonEvent(entity, event.getSource())) {
            event.setCanceled(false);
        }
    }

    @SubscribeEvent
    public static void repeatingCodeKillTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;

        TrueDemonExecutionState state = executionState(player);
        if (state != null && state.retryActive) {
            runRepeatingCodeKillTick(player, state);
        }
    }

    @SubscribeEvent
    public static void entityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        clearAllTracking(event.getEntity());
    }

    private record EntityDamageInfo(int entityId, float amount, long timestamp) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EntityDamageInfo other)) return false;
            return entityId == other.entityId;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(entityId);
        }
    }

    private enum ExecutionPhase {
        ARMED,
        DAMAGE_PIPELINE,
        FORCE_HEALTH_ZERO,
        BLOCK_RECOVERY,
        DEATH_COMMIT,
        CLEANUP
    }

    private static final class TrueDemonExecutionState {
        @Nullable
        private Entity source;
        private ExecutionPhase phase = ExecutionPhase.ARMED;
        private boolean deathGuard;
        private boolean codeKill;
        private boolean handPurge;
        private boolean armorPurge;
        private boolean retryActive;
        private int attempts;

        private TrueDemonExecutionState(@Nullable Entity source) {
            this.source = source;
        }

        private void refresh(@Nullable Entity source) {
            if (source != null) {
                this.source = source;
            }
        }

        private void transitionTo(ExecutionPhase phase) {
            this.phase = phase;
        }

        private void armDeathGuard() {
            this.deathGuard = true;
            transitionTo(ExecutionPhase.ARMED);
        }

        private void armFullExecution() {
            this.codeKill = true;
            this.deathGuard = true;
            this.handPurge = true;
            this.armorPurge = true;
            transitionTo(ExecutionPhase.ARMED);
        }

        private boolean blocksCodeKillHooks() {
            return this.codeKill || this.deathGuard || this.retryActive || this.phase == ExecutionPhase.BLOCK_RECOVERY || this.phase == ExecutionPhase.DEATH_COMMIT;
        }

        private void startRetry(@Nullable Entity source) {
            if (source != null) {
                this.source = source;
            }
            if (!this.retryActive) {
                this.attempts = 0;
            }
            this.retryActive = true;
            transitionTo(ExecutionPhase.BLOCK_RECOVERY);
        }

        private void stopRetry() {
            this.retryActive = false;
            this.attempts = 0;
        }
    }

    @Override
    public boolean isIndirect() {
        return getDirectEntity() != getEntity();
    }

    @Override
    public boolean scalesWithDifficulty() {
        return false;
    }

    @Override
    public @NotNull Component getLocalizedDeathMessage(@NotNull LivingEntity victim) {
        Entity killer = this.getEntity();
        Component attackName = Component.literal("真魔终裁").withStyle(ChatFormatting.LIGHT_PURPLE).withStyle(ChatFormatting.BOLD);

        if (killer != null) {
            return Component.translatable("death.attack.true_demon.player", victim.getDisplayName(), killer.getDisplayName(), attackName);
        } else {
            return Component.translatable("death.attack.true_demon", victim.getDisplayName(), attackName);
        }
    }
}
