package flu.kitten.adorablearmory.entity.damagetype;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TrueDemonHealingPrevention {

    private static boolean isBeneficialEffect(MobEffect effect) {
        return effect != null && effect.getCategory() == MobEffectCategory.BENEFICIAL;
    }

    private static final Set<Item> HEALING_ITEMS = Set.of(
            Items.GOLDEN_APPLE,
            Items.ENCHANTED_GOLDEN_APPLE,
            Items.HONEY_BOTTLE,
            Items.SUSPICIOUS_STEW,
            Items.MUSHROOM_STEW,
            Items.RABBIT_STEW,
            Items.BEETROOT_SOUP
    );

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void livingHeal(LivingHealEvent event) {
        LivingEntity entity = event.getEntity();

        entity.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
            if (cap.blocksHealing()) {
                event.setCanceled(true);

                if (!entity.level().isClientSide) {
                    AdorableArmory.LOGGER.debug("Blocked healing for entity {} (amount: {})", entity.getDisplayName().getString(), event.getAmount());
                }
            }
        });
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void itemUseStart(LivingEntityUseItemEvent.Start event) {
        LivingEntity entity = event.getEntity();
        ItemStack itemStack = event.getItem();

        entity.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
            if (cap.blocksHealing()) {
                if (isHealingItem(itemStack, entity)) {
                    event.setCanceled(true);

                    if (entity instanceof Player player && !entity.level().isClientSide) {
                        player.displayClientMessage(Component.translatable("message.adorablearmory.healing_blocked", itemStack.getHoverName()), false);
                    }
                }
            }
        });
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void onMobEffectApplicable(MobEffectEvent.Applicable event) {
        LivingEntity entity = event.getEntity();
        MobEffectInstance effectInstance = event.getEffectInstance();

        entity.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
            if (cap.blocksHealing()) {
                if (isBeneficialEffect(effectInstance.getEffect())) {
                    event.setResult(Event.Result.DENY);
                    if (!entity.level().isClientSide) {
                        AdorableArmory.LOGGER.debug("Blocked beneficial effect {} for entity {}", effectInstance.getEffect().getDescriptionId(), entity.getDisplayName().getString());
                    }
                }
            }
        });
    }

    private static boolean isHealingItem(ItemStack stack, LivingEntity context) {
        Item item = stack.getItem();
        if (HEALING_ITEMS.contains(item)) return true;

        if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION || item == Items.TIPPED_ARROW) {
            for (MobEffectInstance instance : PotionUtils.getMobEffects(stack)) {
                if (isBeneficialEffect(instance.getEffect())) return true;
            }
        }

        // FoodProperties.getEffects return Pair<MobEffectInstance, Float>
        if (stack.isEdible()) {
            FoodProperties food = stack.getFoodProperties(context);
            if (food != null) {
                for (var pair : food.getEffects()) {
                    MobEffectInstance instance = pair.getFirst();
                    if (instance != null && isBeneficialEffect(instance.getEffect())) return true;
                }
            }
        }

        return false;
    }

    public static void removeHealingEffects(LivingEntity entity) {
        if (entity.level().isClientSide) return;

        List<MobEffect> toRemove = new ArrayList<>();
        for (MobEffectInstance instance : entity.getActiveEffects()) {
            MobEffect effect = instance.getEffect();
            if (isBeneficialEffect(effect)) {
                toRemove.add(effect);
            }
        }
        if (toRemove.isEmpty()) return;

        for (MobEffect effect : toRemove) {
            try {
                if (entity.hasEffect(effect)) {
                    entity.removeEffect(effect);
                    AdorableArmory.LOGGER.debug("Removed beneficial effect {} from entity {}", effect.getDescriptionId(), entity.getDisplayName().getString());
                }
            } catch (Throwable t) {
                AdorableArmory.LOGGER.warn("Failed to remove effect {} from entity {}: {}", effect, entity.getDisplayName().getString(), t.toString());
            }
        }
    }
}
