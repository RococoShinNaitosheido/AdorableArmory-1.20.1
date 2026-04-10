package flu.kitten.adorablearmory.client.render.barrier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BarrierFieldSharedState {

    private static final Map<ResourceKey<Level>, List<BarrierRenderer.Barrier>> SERVER = new ConcurrentHashMap<>();

    private BarrierFieldSharedState() {}

    public static List<BarrierRenderer.Barrier> get(Level level) {
        return SERVER.computeIfAbsent(level.dimension(), k -> new ArrayList<>());
    }

    public static void addAndSync(ServerLevel level, BlockPos center, int half, int height) {
        List<BarrierRenderer.Barrier> list = get(level);
        list.add(new BarrierRenderer.Barrier(center.immutable(), Math.max(1, half), Math.max(1, height)));
        broadcastSync(level);
    }

    public static void removeAndSync(ServerLevel level, BlockPos center) {
        List<BarrierRenderer.Barrier> list = get(level);
        list.removeIf(b -> b.center().equals(center));
        broadcastSync(level);
    }

    public static void clearAndSync(ServerLevel level) {
        get(level).clear();
        broadcastSync(level);
    }

    public static void syncAll(ServerLevel level) {
        broadcastSync(level);
    }

    private static void broadcastSync(ServerLevel level) {
        var dim = level.dimension();
        var snapshot = List.copyOf(get(level));
        var pkt = new BarrierSyncS2CPacket(dim, snapshot);
        NetworkSend.toAllInDimension(level, pkt);
    }
}
