package flu.kitten.adorablearmory.client.render.barrier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;

public final class BarrierAreaUtil {

    private BarrierAreaUtil() {}

    public static boolean inAnyBarrier(Level level, BlockPos blockPos) {

        List<BarrierRenderer.Barrier> barriers = BarrierCollisionNoEntity.getServerBarriers(level);
        if (barriers == null || barriers.isEmpty()) return false;

        double x = blockPos.getX() + 0.5;
        double z = blockPos.getZ() + 0.5;

        for (var b : barriers) {
            final double cx = b.center().getX() + 0.5;
            final double cz = b.center().getZ() + 0.5;
            final int half = Math.max(1, b.half());

            final double minX = cx - half, maxX = cx + half;
            final double minZ = cz - half, maxZ = cz + half;

            if (x >= minX && x <= maxX && z >= minZ && z <= maxZ) {
                return true;
            }
        }
        return false;
    }
}
