package flu.kitten.adorablearmory.entity.weapons.cap;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CSetDemonArrowStuckCount(int entityId, int count) {

    public static void encode(S2CSetDemonArrowStuckCount msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeVarInt(msg.count);
    }

    public static S2CSetDemonArrowStuckCount decode(FriendlyByteBuf buf) {
        return new S2CSetDemonArrowStuckCount(buf.readInt(), buf.readVarInt());
    }

    public static void handle(S2CSetDemonArrowStuckCount msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var level = Minecraft.getInstance().level;
            if (level == null) return;
            Entity entity = level.getEntity(msg.entityId);
            if (entity == null) return;

            entity.getCapability(DemonArrowStuckProvider.CAPABILITY).ifPresent(cap -> cap.setCount(msg.count));
        });
        ctx.get().setPacketHandled(true);
    }
}
