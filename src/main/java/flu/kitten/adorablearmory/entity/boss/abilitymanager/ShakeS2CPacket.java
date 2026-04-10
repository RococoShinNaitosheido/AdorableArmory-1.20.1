package flu.kitten.adorablearmory.entity.boss.abilitymanager;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record ShakeS2CPacket(float amplitude, int duration, float frequency) {
    public static void encode(ShakeS2CPacket pkt, FriendlyByteBuf buf) {
        buf.writeFloat(pkt.amplitude);
        buf.writeVarInt(pkt.duration);
        buf.writeFloat(pkt.frequency);
    }
    public static ShakeS2CPacket decode(FriendlyByteBuf buf) {
        return new ShakeS2CPacket(buf.readFloat(), buf.readVarInt(), buf.readFloat());
    }
    public static void handle(ShakeS2CPacket pkt, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> EarthquakeVisualsManager.trigger(pkt.amplitude(), pkt.duration(), pkt.frequency()))
        );
        ctx.setPacketHandled(true);
    }
}
