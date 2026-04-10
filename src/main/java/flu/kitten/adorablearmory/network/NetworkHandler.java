package flu.kitten.adorablearmory.network;

import flu.kitten.adorablearmory.VisualToggleS2CPacket;
import flu.kitten.adorablearmory.client.render.barrier.BarrierSyncS2CPacket;
import flu.kitten.adorablearmory.entity.boss.abilitymanager.ShakeS2CPacket;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonNetworking;
import flu.kitten.adorablearmory.entity.damagetype.rendertag.PacketSyncTrueDemonMark;
import flu.kitten.adorablearmory.entity.weapons.cap.DemonArrowStuckProvider;
import flu.kitten.adorablearmory.entity.weapons.cap.S2CSetDemonArrowStuckCount;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";
    private static final ResourceLocation NAME = new ResourceLocation(MODID, "main");
    public static SimpleChannel CHANNEL;
    private static int id = 0;

    private static int nextId() {
        return id++;
    }

    public static void init() {
        if (CHANNEL != null) return;
        CHANNEL = NetworkRegistry.newSimpleChannel(NAME, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
        CHANNEL.registerMessage(nextId(), ShakeS2CPacket.class, ShakeS2CPacket::encode, ShakeS2CPacket::decode, ShakeS2CPacket::handle);
        CHANNEL.registerMessage(nextId(), VisualToggleS2CPacket.class, VisualToggleS2CPacket::encode, VisualToggleS2CPacket::decode, VisualToggleS2CPacket::handle);
        CHANNEL.registerMessage(nextId(), BarrierSyncS2CPacket.class, BarrierSyncS2CPacket::encode, BarrierSyncS2CPacket::decode, BarrierSyncS2CPacket::handle);
        CHANNEL.registerMessage(nextId(), PacketSyncTrueDemonMark.class, PacketSyncTrueDemonMark::toBytes, PacketSyncTrueDemonMark::new, PacketSyncTrueDemonMark::handle);
        CHANNEL.messageBuilder(S2CSetDemonArrowStuckCount.class, id++, NetworkDirection.PLAY_TO_CLIENT).encoder(S2CSetDemonArrowStuckCount::encode).decoder(S2CSetDemonArrowStuckCount::decode).consumerMainThread(S2CSetDemonArrowStuckCount::handle).add();

        TrueDemonNetworking.registerPackets(CHANNEL, NetworkHandler::nextId);
    }

    public static void syncStuck(LivingEntity victim) {
        victim.getCapability(DemonArrowStuckProvider.CAPABILITY).ifPresent(cap -> CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> victim), new S2CSetDemonArrowStuckCount(victim.getId(), cap.getCount())));
    }

    private NetworkHandler() {}
}
