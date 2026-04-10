package flu.kitten.adorablearmory.entity.damagetype.rendertag;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.entity.damagetype.Capabilities;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonDamageSource;
import flu.kitten.adorablearmory.network.NetworkHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TrueDemonEffectMarkEvents {

    @SubscribeEvent
    public static void livingDamageMark(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (TrueDemonDamageSource.isTrueDemonDamage(event.getSource())) {
            LivingEntity target = event.getEntity();
            target.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
                PacketSyncTrueDemonMark packet = new PacketSyncTrueDemonMark(
                        target.getId(),
                        cap.getRemainingDuration(),
                        cap.hasEffect()
                );
                NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), packet);
            });
        }
    }

    @SubscribeEvent
    public static void livingTickMark(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            entity.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
                if (cap.hasEffect()) {
                    cap.tick(entity);
                }
            });
        }
    }
}
