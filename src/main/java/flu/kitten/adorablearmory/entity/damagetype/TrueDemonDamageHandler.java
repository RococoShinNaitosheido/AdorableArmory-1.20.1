package flu.kitten.adorablearmory.entity.damagetype;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.CommonConfig;
import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import flu.kitten.adorablearmory.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TrueDemonDamageHandler {

    private static int calcDurabilityLoss(float damage) {
        int value = Mth.floor(damage);
        return Mth.clamp(value, 2, 1000); // min = 2, max = 20
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void afterDamageCleanse(LivingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();

        if (victim.level().isClientSide()) return;
        if (source == null || !source.is(TrueDemonDamageSource.TRUE_DEMON_TYPE)) return;
        if (victim instanceof ScarletLoraAlysia) return;

        victim.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
            if (cap.blocksHealing() || cap.hasEffect()) {
                TrueDemonHealingPrevention.removeHealingEffects(victim);
            }
        });

        // TrueDemon 本体 DOT 不刷新
        if (source.getEntity() == victim && (source.getDirectEntity() == null || source.getDirectEntity() == victim)) {
            return;
        }

        // 赋予/刷新效果&同步
        victim.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
            cap.setRemainingDuration(200);  // 200tick
            cap.setHealingBlockDuration(200);

            TrueDemonEffectSyncPacket pkt = new TrueDemonEffectSyncPacket(victim.getId(), cap.hasEffect(), cap.getRemainingDuration(), cap.blocksHealing(), cap.getHealingBlockDuration());
            NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> victim), pkt);
        });
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void trueDemonDamaged(final LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        DamageSource source = event.getSource();
        if (!source.is(TrueDemonDamageSource.TRUE_DEMON_TYPE)) return;

        LivingEntity target = event.getEntity();
        int dmgPerPiece = calcDurabilityLoss(event.getAmount());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;

            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) continue;

            stack.hurtAndBreak(dmgPerPiece, target, (p) -> p.broadcastBreakEvent(slot));
        }

        ItemStack offhand = target.getItemBySlot(EquipmentSlot.OFFHAND);
        if (!offhand.isEmpty() && offhand.isDamageableItem()) {
            if (target instanceof ServerPlayer player && player.isBlocking()) {
                offhand.hurtAndBreak(dmgPerPiece, target, (p) -> p.broadcastBreakEvent(EquipmentSlot.OFFHAND));
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void trueDemonAttacked(LivingAttackEvent event) {
        DamageSource source = event.getSource();
        if (!source.is(TrueDemonDamageSource.TRUE_DEMON_TYPE)) return;

        if (event.getEntity() instanceof ScarletLoraAlysia) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (CommonConfig.TRUE_DEMON_HITS_CREATIVE.get()) return;
        if (player.isCreative() || player.isSpectator() || player.isInvulnerable()) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) return;

        entity.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
            cap.tick(entity);

            boolean active = cap.hasEffect() || cap.blocksHealing();
            if (entity.tickCount % 20 == 0) {
                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), new TrueDemonEffectSyncPacket(entity.getId(), cap.hasEffect(), cap.getRemainingDuration(), cap.blocksHealing(), cap.getHealingBlockDuration()));
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void knockBack(LivingKnockBackEvent event) {
        event.getEntity().getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
            DamageSource source = event.getEntity().getLastDamageSource();
            if (source != null && source.is(TrueDemonDamageSource.TRUE_DEMON_TYPE) && cap.hasEffect()) {
                event.setCanceled(true);
            }
        });
    }
}
