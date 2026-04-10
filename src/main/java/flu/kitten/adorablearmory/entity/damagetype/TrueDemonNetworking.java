package flu.kitten.adorablearmory.entity.damagetype;

import flu.kitten.adorablearmory.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.IntSupplier;

public final class TrueDemonNetworking {

    private TrueDemonNetworking() {}

    public static void registerPackets(SimpleChannel channel, IntSupplier nextId) {
        channel.messageBuilder(TrueDemonEffectSyncPacket.class, nextId.getAsInt())
                .encoder(TrueDemonEffectSyncPacket::encode)
                .decoder(TrueDemonEffectSyncPacket::decode)
                .consumerMainThread(TrueDemonEffectSyncPacket::handle)
                .add();
    }

    @SuppressWarnings("unused")
    public static void sendToTracking(Object msg, Entity entity) {
        NetworkHandler.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), msg);
    }

    @SuppressWarnings("unused")
    public static void sendToPlayer(Object msg, ServerPlayer player) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    @SuppressWarnings("unused")
    public static void sendToServer(Object msg) {
        NetworkHandler.CHANNEL.sendToServer(msg);
    }
}
