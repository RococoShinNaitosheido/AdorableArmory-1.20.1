package flu.kitten.adorablearmory.entity.weapons.cap;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.network.NetworkHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID)
public class DemonArrowStuckEvents {

    // 每支箭平均几秒掉落一次
    private static final int DROP_MIN_TICKS = 40;   // 2s
    private static final int DROP_MAX_TICKS = 120;  // 6s

    @SubscribeEvent
    public static void registerCaps(RegisterCapabilitiesEvent event) {
        event.register(IDemonArrowStuckCap.class);
    }

    @SubscribeEvent
    public static void attachCaps(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(DemonArrowStuckProvider.ID, new DemonArrowStuckProvider());
        }
    }

    @SubscribeEvent
    public static void livingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide) return;

        player.getCapability(DemonArrowStuckProvider.CAPABILITY).ifPresent(cap -> tickDrop(player, cap));
    }

    private static void tickDrop(Player player, IDemonArrowStuckCap cap) {
        int count = cap.getCount();
        if (count <= 0) {
            cap.setDropCooling(0);
            return;
        }

        int cooling = cap.getDropCooling();

        if (cooling <= 0) {
            cooling = Mth.nextInt(player.level().random, DROP_MIN_TICKS, DROP_MAX_TICKS);
        }

        cooling--;

        if (cooling <= 0) {
            cap.setCount(count - 1);
            cap.setDropCooling(Mth.nextInt(player.level().random, DROP_MIN_TICKS, DROP_MAX_TICKS));
            NetworkHandler.syncStuck(player);
        } else {
            cap.setDropCooling(cooling);
        }
    }
}
