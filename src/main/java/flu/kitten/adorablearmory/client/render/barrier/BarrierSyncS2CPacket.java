package flu.kitten.adorablearmory.client.render.barrier;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class BarrierSyncS2CPacket {
    private final ResourceKey<Level> dimension;
    private final List<BarrierRenderer.Barrier> barriers;

    public BarrierSyncS2CPacket(ResourceKey<Level> dimension, List<BarrierRenderer.Barrier> barriers) {
        this.dimension = dimension;
        this.barriers = barriers;
    }

    public static void encode(BarrierSyncS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dimension.location());
        buf.writeVarInt(msg.barriers.size());
        for (var b : msg.barriers) {
            buf.writeBlockPos(b.center());
            buf.writeVarInt(b.half());
            buf.writeVarInt(b.height());
        }
    }

    public static BarrierSyncS2CPacket decode(FriendlyByteBuf buf) {
        var dimLoc = buf.readResourceLocation();
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, dimLoc);
        int n = buf.readVarInt();
        List<BarrierRenderer.Barrier> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            BlockPos c = buf.readBlockPos();
            int half = buf.readVarInt();
            int height = buf.readVarInt();
            list.add(new BarrierRenderer.Barrier(c, half, height));
        }
        return new BarrierSyncS2CPacket(dim, list);
    }

    public static void handle(BarrierSyncS2CPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (!mc.level.dimension().equals(msg.dimension)) return;
            BarrierRenderer.setBarriers(msg.barriers);
        });
        ctx.get().setPacketHandled(true);
    }
}
