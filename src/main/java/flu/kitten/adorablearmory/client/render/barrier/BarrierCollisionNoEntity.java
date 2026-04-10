package flu.kitten.adorablearmory.client.render.barrier;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BarrierCollisionNoEntity {

    private static final double EPS = 1.0E-3;

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void levelTick(TickEvent.LevelTickEvent event) {

        if (event.phase != TickEvent.Phase.END) return;
        final Level level = event.level;
        if (level.isClientSide) return;

        final List<BarrierRenderer.Barrier> barriers = getServerBarriers(level);
        if (barriers == null || barriers.isEmpty()) return;

        int minBarrierY = Integer.MAX_VALUE;
        int maxBarrierY = Integer.MIN_VALUE;
        double minBarrierX = Double.POSITIVE_INFINITY, maxBarrierX = Double.NEGATIVE_INFINITY;
        double minBarrierZ = Double.POSITIVE_INFINITY, maxBarrierZ = Double.NEGATIVE_INFINITY;

        for (var b : barriers) {
            int cy = b.center().getY();
            int h  = Math.max(1, b.height());
            int half = Math.max(1, b.half());

            minBarrierY = Math.min(minBarrierY, cy);
            maxBarrierY = Math.max(maxBarrierY, cy + h);

            double cx = b.center().getX() + 0.5;
            double cz = b.center().getZ() + 0.5;
            minBarrierX = Math.min(minBarrierX, cx - half);
            maxBarrierX = Math.max(maxBarrierX, cx + half);
            minBarrierZ = Math.min(minBarrierZ, cz - half);
            maxBarrierZ = Math.max(maxBarrierZ, cz + half);
        }

        if (minBarrierY == Integer.MAX_VALUE) return;
        double minY = minBarrierY;
        double maxY = maxBarrierY;

        double minX, maxX, minZ, maxZ;
        var rect = LoadedChunksTracker.getLoadedChunkRect(level.dimension());
        if (rect[0].isPresent() && rect[1].isPresent() && rect[2].isPresent() && rect[3].isPresent()) {
            int minCX = rect[0].getAsInt(), maxCX = rect[1].getAsInt();
            int minCZ = rect[2].getAsInt(), maxCZ = rect[3].getAsInt();
            double lMinX = (minCX << 4);
            double lMaxX = ((maxCX + 1) << 4) - 1;
            double lMinZ = (minCZ << 4);
            double lMaxZ = ((maxCZ + 1) << 4) - 1;
            minX = lMinX;
            maxX = lMaxX;
            minZ = lMinZ;
            maxZ = lMaxZ;
        } else {
            minX = minBarrierX; maxX = maxBarrierX;
            minZ = minBarrierZ; maxZ = maxBarrierZ;
        }

        minX -= 2; maxX += 2;
        minZ -= 2; maxZ += 2;

        AABB aabb = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        List<Entity> candidates = level.getEntitiesOfClass(Entity.class, aabb, entity -> entity.isAlive() && !entity.isSpectator() && !entity.noPhysics);

        for (Entity entity : candidates) {
            double x = entity.getX(), y = entity.getY(), z = entity.getZ();
            Vec3 vel = entity.getDeltaMovement();
            boolean changedAny = false;

            for (BarrierRenderer.Barrier barrier : barriers) {
                Vec3 center = new Vec3(barrier.center().getX() + 0.5, barrier.center().getY(), barrier.center().getZ() + 0.5);
                int half = barrier.half();
                double halfW = entity.getBbWidth() * 0.5;

                double bxMinX = center.x - half, bxMaxX = center.x + half;
                double bxMinZ = center.z - half, bxMaxZ = center.z + half;

                boolean outsideHoriz = (x - halfW < bxMinX) || (x + halfW > bxMaxX) || (z - halfW < bxMinZ) || (z + halfW > bxMaxZ);
                if (!outsideHoriz) continue;

                if (x - halfW < bxMinX) {
                    x = bxMinX + halfW + EPS; vel = vel.multiply(0, 1, 1);
                }

                else if (x + halfW > bxMaxX) {
                    x = bxMaxX - halfW - EPS; vel = vel.multiply(0, 1, 1);
                }

                if (z - halfW < bxMinZ) {
                    z = bxMinZ + halfW + EPS; vel = vel.multiply(1, 1, 0);
                }

                else if (z + halfW > bxMaxZ) {
                    z = bxMaxZ - halfW - EPS; vel = vel.multiply(1, 1, 0);
                }

                changedAny = true;
                break;
            }

            if (changedAny) {
                entity.setDeltaMovement(vel);
                if (entity instanceof ServerPlayer player) {
                    player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
                    player.setDeltaMovement(vel);
                } else {
                    entity.setPos(x, y, z);
                    entity.hurtMarked = true;
                }
            }
        }
    }

    public static List<BarrierRenderer.Barrier> getServerBarriers(Level level) {
        return BarrierFieldSharedState.get(level);
    }
}

