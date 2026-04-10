package flu.kitten.adorablearmory.entity.boss;

import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.function.Supplier;

public class TeleportPreStrikeGoal extends Goal {

    private final ScarletLoraAlysia boss;
    private final Supplier<Boolean> shouldStart;
    private final int hopIntervalTicks;
    private Vec3 center;
    private double radius;
    private final List<Line> route = new ArrayList<>();
    private int segIndex = -1;
    private int tickCd = 0;
    private Player target;

    // 粒子轨迹
    private final Map<Integer, List<BlockPos>> lineSamples = new HashMap<>();
    private final Set<Long> spawnedRouteMarks = new HashSet<>();
    private int sampleCursor = 0;
    private static final double SAMPLE_STEP = 0.32D; // 采样步长/越小越密
    private static final int SAMPLES_PER_TICK = 12; // 每tick发多少个点
    private static final int PARTICLES_PER_POINT = 6; // 每个采样点的粒子

    // 瞬移当帧的补强特效 轨迹&起点/终点AABB
    private static final boolean ENABLE_TELEPORT_TRAIL_BURST = true;
    private static final int TRAIL_STEPS_PER_BLOCK = 4;
    private static final int TELEPORT_START_BURST = 8;
    private static final int TELEPORT_END_BURST = 8;

    public TeleportPreStrikeGoal(ScarletLoraAlysia boss) {
        this(boss, () -> boss.getCurrentPhase() == 0 || !boss.isCharging(), 3);
    }

    public TeleportPreStrikeGoal(ScarletLoraAlysia boss, Supplier<Boolean> shouldStart, int hopIntervalTicks) {
        this.boss = boss;
        this.shouldStart = (shouldStart == null) ? () -> true : shouldStart;
        this.hopIntervalTicks = Math.max(1, hopIntervalTicks);
        setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = boss.getTarget();
        if (boss.level().isClientSide()) return false;
        if (!(target instanceof Player player)) return false;
        if (!boss.isAlive()) return false;
        if (!player.isAlive()) return false;
        if (!boss.isTeleporting()) {
            return false;
        }

        if (!shouldStart.get()) return false;

        this.target = player;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (boss.level().isClientSide()) return false;
        if (!boss.isAlive()) return false;
        if (!boss.isTeleporting()) {
            return false;
        }
        return target != null && target.isAlive();
    }

    @Override
    public void start() {
        System.out.println("[TeleportGoal-DEBUG] Goal STARTED. Building route...");
        if (!boss.isTeleporting()) boss.setTeleporting(true);
        boss.getNavigation().stop();
        boss.forceEndCharge();

        this.center = (target != null && target.isAlive()) ? target.position() : boss.position();
        double stepMin = Math.max(0.1, ScarletLoraAlysia.getMinDistance());
        double stepMax = Math.max(stepMin, ScarletLoraAlysia.getMaxDistance());
        this.radius = (stepMin + stepMax) * 2;

        buildRoute();
        buildRouteSamples(); // 预采样整张buildRoute
        this.segIndex = 0;
        this.tickCd = hopIntervalTicks;

        if (target == null || !target.isAlive()) {
            finishAndStrike();
        } else {
            boss.getLookControl().setLookAt(target, 180.0F, 30.0F);
        }
    }

    @Override
    public void stop() {
        System.out.println("[TeleportGoal-DEBUG] Goal STOPPED.");
        if (boss.isTeleporting()) {
            boss.setTeleporting(false);
            boss.scheduleNextTeleport();
        }
        route.clear();
        segIndex = -1;
        tickCd = 0;
        target = null;
    }

    @Override
    public void tick() {
        if (target == null || !target.isAlive()) {
            finishAndStrike();
            return;
        }
        boss.getLookControl().setLookAt(target, 180.0F, 30.0F);

        emitRouteParticlesThisTick(); // 不论本帧是否瞬移 沿当前线段推进“图案尾迹粒子”

        if (--tickCd > 0) return;
        tickCd = hopIntervalTicks;

        if (segIndex >= route.size()) {
            System.out.println("[TeleportGoal-DEBUG] All segments finished. Finishing strike.");
            finishAndStrike();
            return;
        }

        Line currentSegment = route.get(segIndex);
        Vec3 destination = currentSegment.b;

        System.out.println("[TeleportGoal-DEBUG] Processing segment #" + segIndex + ". Attempting teleport to endpoint: " + destination);

        Vec3 safeSpot = findSafeGround(destination.x, destination.z);

        if (safeSpot == null) {
            System.out.println("[TeleportGoal-DEBUG] Endpoint 'b' is not safe. Trying endpoint 'a'.");
            destination = currentSegment.a;
            safeSpot = findSafeGround(destination.x, destination.z);
        }

        if (safeSpot != null) {
            System.out.println("[TeleportGoal-DEBUG] Safe ground found at " + safeSpot + ". Teleporting.");
            if (tryTeleport(safeSpot)) {
                boss.level().playSound(null, boss.getX(), boss.getY(), boss.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.32F, 1.56F);
            } else {
                System.out.println("[TeleportGoal-DEBUG] tryTeleport() returned false for " + safeSpot);
            }
        } else {
            System.out.println("[TeleportGoal-DEBUG] No safe ground found for segment #" + segIndex + ". Skipping.");
        }

        segIndex++;
        sampleCursor = 0;
    }

    // 在当前线段上按采样推进发粒子/每tick固定发SAMPLES_PER_TICK个尚未发过的点
    private void emitRouteParticlesThisTick() {
        if (boss.level().isClientSide() || segIndex < 0 || segIndex >= route.size()) return;
        final ServerLevel serverLevel = (ServerLevel) boss.level();
        final List<BlockPos> samples = lineSamples.get(segIndex);
        if (samples == null || samples.isEmpty()) return;

        int emitted = 0;
        while (sampleCursor < samples.size() && emitted < SAMPLES_PER_TICK) {
            BlockPos pos = samples.get(sampleCursor++);
            long key = pos.asLong();
            if (spawnedRouteMarks.add(key)) {
                final double width = boss.getBbWidth() * 0.5;
                final double height = boss.getBbHeight() * 0.5;
                final double cx = pos.getX() + width;
                final double cy = pos.getY() + height;
                final double cz = pos.getZ() + 0.5;
                serverLevel.sendParticles(AdorableArmoryRegister.TRUE_DEMON_PARTICLE.get(), cx, cy, cz, PARTICLES_PER_POINT, width, height, width, 0.0);
                emitted++;
            }
        }
    }

    private boolean tryTeleport(Vec3 pos) {
        if (boss.level().isClientSide()) {
            return false;
        }
        final ServerLevel serverLevel = (ServerLevel) boss.level();
        Vec3 before = boss.position();
        boolean teleport = boss.randomTeleport(pos.x, pos.y, pos.z, false);

        // 轨迹/起点/终点粒子
        if (teleport && ENABLE_TELEPORT_TRAIL_BURST) {
            // 起AABB
            spawnInAABBStrict(serverLevel, boss, before, TELEPORT_START_BURST);

            // 轨迹before -> after
            Vec3 after = boss.position();
            double dist = before.distanceTo(after);
            int steps = Math.max(4, Mth.floor(dist * TRAIL_STEPS_PER_BLOCK));
            for (int i = 1; i <= steps; i++) {
                double t = i / (double) (steps + 1);
                Vec3 mid = before.lerp(after, t);
                serverLevel.sendParticles(AdorableArmoryRegister.TRUE_DEMON_PARTICLE.get(), mid.x, mid.y, mid.z, 2, 0.08, 0.08, 0.08, 0.0);
            }

            // 终AABB
            spawnInAABBStrict(serverLevel, boss, boss.position(), TELEPORT_END_BURST);
        }
        return teleport;
    }

    // 在实体当前AABB/以某中心位置为参考内严格撒粒子-逐粒子采样
    private void spawnInAABBStrict(ServerLevel serverLevel, ScarletLoraAlysia entity, Vec3 centerPos, int count) {
        final AABB aabb = entity.getBoundingBox().move(centerPos.subtract(entity.position()));
        final Random rand = new Random();
        for (int i = 0; i < count; i++) {
            double px = Mth.lerp(rand.nextDouble(), aabb.minX, aabb.maxX);
            double py = Mth.lerp(rand.nextDouble(), aabb.minY, aabb.maxY);
            double pz = Mth.lerp(rand.nextDouble(), aabb.minZ, aabb.maxZ);
            serverLevel.sendParticles(AdorableArmoryRegister.TRUE_DEMON_PARTICLE.get(), px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private Vec3 findSafeGround(double x, double z) {
        ServerLevel level = (ServerLevel) boss.level();
        int gx = Mth.floor(x);
        int gz = Mth.floor(z);
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);

        int down = Math.max(level.getMinBuildHeight(), top - 10);
        int up = Math.min(level.getMaxBuildHeight() - 2, top + 10);

        for (int y = top; y >= down; y--) if (canStandHere(level, x, y, z)) return new Vec3(x, y, z);
        for (int y = top + 1; y <= up; y++) if (canStandHere(level, x, y, z)) return new Vec3(x, y, z);
        return null;
    }

    private boolean canStandHere(Level level, double x, int y, double z) {
        BlockPos below = BlockPos.containing(x, y - 1, z);
        if (!level.getBlockState(below).blocksMotion()) return false;
        double half = boss.getBbWidth() / 2.0;
        AABB box = new AABB(x - half, y, z - half, x + half, y + boss.getBbHeight(), z + half);
        return level.noCollision(boss, box);
    }

    void finishAndStrike() {
        System.out.println("[TeleportGoal-DEBUG] Executing finishAndStrike().");

        Player target = (this.target != null && this.target.isAlive()) ? this.target : boss.getPreTeleportTarget();
        boolean executed = false;
        if (target != null && target.isAlive()) {
            executed = boss.executeTeleportAttack(target);
        }
        boss.onTeleportCompleted(executed);
    }

    private void buildRoute() {
        route.clear();
        double rot = Mth.nextDouble(boss.getRandom(), 0.0, Math.PI * 2.0);

        // Star
        Vec3[] v = new Vec3[5];
        for (int i = 0; i < 5; i++) {
            double ang = rot + i * (2 * Math.PI / 5.0);
            double x = center.x + radius * Math.cos(ang);
            double z = center.z + radius * Math.sin(ang);
            v[i] = new Vec3(x, center.y, z);
        }
        int[] order = new int[]{0, 2, 4, 1, 3, 0};
        for (int i = 0; i < order.length - 1; i++) {
            route.add(new Line(v[order[i]], v[order[i + 1]]));
        }

        // Two Triangles
        Vec3[] hex = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double ang = rot + i * (2 * Math.PI / 6.0);
            double x = center.x + radius * 0.82 * Math.cos(ang);
            double z = center.z + radius * 0.82 * Math.sin(ang);
            hex[i] = new Vec3(x, center.y, z);
        }
        route.add(new Line(hex[0], hex[2]));
        route.add(new Line(hex[2], hex[4]));
        route.add(new Line(hex[4], hex[0]));
        route.add(new Line(hex[1], hex[3]));
        route.add(new Line(hex[3], hex[5]));
        route.add(new Line(hex[5], hex[1]));

        // Cross
        Vec3 a = new Vec3(center.x + radius * 0.92 * Math.cos(rot + Math.PI / 4), center.y, center.z + radius * 0.92 * Math.sin(rot + Math.PI / 4));
        Vec3 b = new Vec3(center.x + radius * 0.92 * Math.cos(rot + Math.PI * 5 / 4), center.y, center.z + radius * 0.92 * Math.sin(rot + Math.PI * 5 / 4));
        Vec3 c = new Vec3(center.x + radius * 0.92 * Math.cos(rot + Math.PI * 3 / 4), center.y, center.z + radius * 0.92 * Math.sin(rot + Math.PI * 3 / 4));
        Vec3 d = new Vec3(center.x + radius * 0.92 * Math.cos(rot + Math.PI * 7 / 4), center.y, center.z + radius * 0.92 * Math.sin(rot + Math.PI * 7 / 4));
        route.add(new Line(a, b));
        route.add(new Line(c, d));

        rot += Math.PI / 8.0;
        double rotationOffset = Math.toRadians(24);
        double scale = 1.12;
        Vec3[] oct = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            double ang = rot + rotationOffset + i * (Math.PI / 4.0);
            double x = center.x + radius * scale * Math.cos(ang);
            double z = center.z + radius * scale * Math.sin(ang);
            oct[i] = new Vec3(x, center.y, z);
        }

        int[] ints = new int[] {0, 3, 6, 1, 4, 7, 2, 5, 0};
        for (int i = 0; i < ints.length - 1; i++) {
            route.add(new Line(oct[ints[i]], oct[ints[i + 1]]));
        }
    }

    // 把route里每条线段按步长SAMPLE_STEP采样成BlockPos列表
    private void buildRouteSamples() {
        lineSamples.clear();
        spawnedRouteMarks.clear();

        for (int idx = 0; idx < route.size(); idx++) {
            Line ln = route.get(idx);
            List<BlockPos> pts = sampleLineToBlockPos(ln.a, ln.b, SAMPLE_STEP);
            lineSamples.put(idx, pts);
        }
    }

    private List<BlockPos> sampleLineToBlockPos(Vec3 a, Vec3 b, double step) {
        List<BlockPos> out = new ArrayList<>();
        Set<Long> once = new HashSet<>();

        double len = a.distanceTo(b);
        if (len < 1e-6) {
            BlockPos single = BlockPos.containing(a);
            if (once.add(single.asLong())) out.add(single);
            return out;
        }
        int n = Math.max(2, (int) Math.ceil(len / step));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            Vec3 vec3 = a.lerp(b, t);
            BlockPos containing = BlockPos.containing(vec3.x, vec3.y, vec3.z);
            long key = containing.asLong();
            if (once.add(key)) {
                out.add(containing);
            }
        }
        return out;
    }

    record Line(Vec3 a, Vec3 b) {}
}
