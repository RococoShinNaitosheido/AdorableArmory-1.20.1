package flu.kitten.adorablearmory.client.render.barrier;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.OptionalInt;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class LoadedChunksTracker {

    private static final Long2ObjectOpenHashMap<LongOpenHashSet> LOADED = new Long2ObjectOpenHashMap<>();

    private static LongOpenHashSet setFor(ResourceKey<Level> dim) {
        return LOADED.computeIfAbsent(ChunkKey(dim), k -> new LongOpenHashSet());
    }

    private static long ChunkKey(ResourceKey<Level> dim) {
        return dim.location().toString().hashCode();
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void chunkLoad(ChunkEvent.Load e) {
        Level level = (Level) e.getLevel();
        if (level.isClientSide) return;
        ChunkPos cp = e.getChunk().getPos();
        setFor(level.dimension()).add(cp.toLong());
    }

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void chunkUnload(ChunkEvent.Unload e) {
        Level level = (Level) e.getLevel();
        if (level.isClientSide) return;
        ChunkPos cp = e.getChunk().getPos();
        setFor(level.dimension()).remove(cp.toLong());
    }

    public static OptionalInt[] getLoadedChunkRect(ResourceKey<Level> dim) {
        LongSet set = setFor(dim);
        if (set.isEmpty()) return new OptionalInt[]{OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty(), OptionalInt.empty()};
        int minCX = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE;
        int minCZ = Integer.MAX_VALUE, maxCZ = Integer.MIN_VALUE;
        for (long packed : set) {
            ChunkPos cp = new ChunkPos(packed);
            if (cp.x < minCX) minCX = cp.x;
            if (cp.x > maxCX) maxCX = cp.x;
            if (cp.z < minCZ) minCZ = cp.z;
            if (cp.z > maxCZ) maxCZ = cp.z;
        }
        return new OptionalInt[]{
                OptionalInt.of(minCX), OptionalInt.of(maxCX),
                OptionalInt.of(minCZ), OptionalInt.of(maxCZ)
        };
    }

    public static boolean isEmpty(ResourceKey<Level> dim) {
        return setFor(dim).isEmpty();
    }
}
