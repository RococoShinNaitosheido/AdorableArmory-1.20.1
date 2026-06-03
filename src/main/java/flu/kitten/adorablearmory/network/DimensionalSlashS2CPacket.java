package flu.kitten.adorablearmory.network;

import flu.kitten.adorablearmory.client.render.dimensional.DimensionalSlashClientEffects;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DimensionalSlashS2CPacket(double x, double y, double z, int slashCount, float length, float radius, long seed) {
    public static void encode(DimensionalSlashS2CPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeVarInt(packet.slashCount);
        buffer.writeFloat(packet.length);
        buffer.writeFloat(packet.radius);
        buffer.writeLong(packet.seed);
    }

    public static DimensionalSlashS2CPacket decode(FriendlyByteBuf buffer) {
        return new DimensionalSlashS2CPacket(
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readLong()
        );
    }

    public static void handle(DimensionalSlashS2CPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        DimensionalSlashClientEffects.trigger(new Vec3(packet.x, packet.y, packet.z), packet.slashCount, packet.length, packet.radius, packet.seed)
                )
        );
        context.setPacketHandled(true);
    }
}
