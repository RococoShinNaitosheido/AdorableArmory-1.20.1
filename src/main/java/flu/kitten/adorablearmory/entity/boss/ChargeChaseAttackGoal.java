package flu.kitten.adorablearmory.entity.boss;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class ChargeChaseAttackGoal extends Goal {

    private final ScarletLoraAlysia boss;
    private final double baseSpeed; // 1.0 ~ 1.4
    private boolean inCharge = false; // 冲刺阶段
    private Vec3 chargeDir = Vec3.ZERO; // 当前冲刺方向
    private int dashTicks = 0;
    private static final int MAX_DASH_TICKS = 100; // 冲刺的最长持续时间
    private static final double HIT_RADIUS = 0.024;
    private static final double VERTICAL_BUMP = 0.32; // 命中后对玩家施加的垂直方向击飞力度
    private static final double KNOCK_STRENGTH = 9; // 命中后对玩家施加的水平方向击退力度
    private static final double MIN_CHARGE_SPEED = 1.24; // MOVEMENT_SPEED倍率
    private static final double MAX_CHARGE_SPEED = 10.0;
    private static final double SPEED_DISTANCE_FACTOR = 0.30;
    private static final int RETARGET_EVERY_TICKS = 2;
    private static final double LEAD_PREDICT_TICKS = 2.0;
    public static final double START_MIN_DIST = 6; // 小于该距离不冲刺
    private static final double START_BOOST_FACTOR = 1.6; // 起步爆发
    private static final double SUSTAIN_FACTOR = 3.56; // 过程维持
    private static final double GLOBAL_SPEED_CAP = 2.0; // 水平最终上限[格/tick]
    private static final int MAX_CHARGE_ATTEMPTS = 3; // 连追击次数
    private int chargeAttempts = 0;
    private Vec3 lastPos = null;
    private int stuckTicks = 0;
    private static final double STUCK_POS_EPS_SQR = 0.01;
    private static final int STUCK_TICKS_LIMIT = 40;
    private boolean pendingHit = false;

    public ChargeChaseAttackGoal(ScarletLoraAlysia boss, double baseSpeed) {
        this.boss = boss;
        this.baseSpeed = baseSpeed;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (boss.getCurrentPhase() != 0) {
            return false;
        }

        if (boss.isTeleporting()) {
            return false;
        }

        if (boss.isInChargeCool()) {
            return false;
        }

        return boss.distanceTo(target) >= START_MIN_DIST; // 只有距离≥START_MIN_DIST才启动
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = boss.getTarget();
        if (target == null || !target.isAlive()) return false;

        if (boss.getCurrentPhase() != 0) {
            return false;
        }

        if (boss.isTeleporting()) {
            return false;
        }

        if (inCharge) {
            return dashTicks <= MAX_DASH_TICKS;
        }

        return boss.distanceTo(target) >= START_MIN_DIST && !boss.isInChargeCool();
    }

    @Override
    public void start() {
        inCharge = false;
        chargeDir = Vec3.ZERO;
        dashTicks = 0;
        lastPos = null;
        stuckTicks = 0;
        chargeAttempts = 0;
        pendingHit = false;
    }

    @Override
    public void stop() {
        if (inCharge) internalEndCharge(true);
        boss.getNavigation().stop();
        inCharge = false;
        chargeDir = Vec3.ZERO;
        dashTicks = 0;
        lastPos = null;
        stuckTicks = 0;
        pendingHit = false;
    }

    @Override
    public void tick() {
        LivingEntity target = boss.getTarget();
        if (target == null) return;

        if (inCharge) {
            tickCharge(target);
            return;
        }

        if (boss.tickCount % 5 == 0) {
            boss.getNavigation().moveTo(target, baseSpeed); // baseSpeed = 倍率 1.0 ~ 1.4
        }
        boss.getLookControl().setLookAt(target, 30.0F, 30.0F);

        double dist = boss.distanceTo(target);
        if (dist >= START_MIN_DIST) {
            if (boss.isInChargeCool()) return;
            beginCharge(target, dist);
        }
    }

    private void beginCharge(LivingEntity target, double dist) {
        Vec3 desired = computeDesiredDirection(target, LEAD_PREDICT_TICKS);
        if (desired.lengthSqr() < 1.0e-6) return;

        chargeDir = desired.normalize();
        inCharge = true;
        dashTicks = 0;
        pendingHit = false;

        if (chargeAttempts == 0) {
            boss.startCharge();
        }
        boss.getNavigation().stop();

        double multi = calculateChargeSpeed(dist); // 2.0 ~ 10.0
        double attr = Math.max(0.0001, boss.getAttributeValue(Attributes.MOVEMENT_SPEED));
        double vxz = attr * multi * START_BOOST_FACTOR;
        vxz = Math.min(vxz, GLOBAL_SPEED_CAP);

        Vec3 dashV = new Vec3(chargeDir.x * vxz, boss.getDeltaMovement().y, chargeDir.z * vxz);
        boss.setDeltaMovement(dashV);
        boss.hasImpulse = true;
    }

    private void tickCharge(LivingEntity target) {
        if (pendingHit && !boss.level().isClientSide()) {
            if (boss.getBoundingBox().intersects(target.getBoundingBox())) {
                applyHitAndKnock(target);
                Vec3 cur = boss.getDeltaMovement();
                boss.setDeltaMovement(0.0, cur.y, 0.0);
                pendingHit = false;
                internalEndCharge(true);
                return;
            }
        }

        dashTicks++;

        // 重新引导方向
        if (dashTicks % RETARGET_EVERY_TICKS == 0) {
            Vec3 desired = computeDesiredDirection(target, LEAD_PREDICT_TICKS);
            if (desired.lengthSqr() > 1.0e-6) {
                chargeDir = desired.normalize();
            }
        }

        // 朝向
        double yawDeg = Math.toDegrees(Math.atan2(chargeDir.z, chargeDir.x)) - 90.0;
        float yaw = (float) yawDeg;
        boss.setYRot(yaw);
        boss.setYBodyRot(yaw);
        boss.setYHeadRot(yaw);

        // 速度维持
        double dx = target.getX() - boss.getX();
        double dz = target.getZ() - boss.getZ();
        double remainHoriz = Math.sqrt(dx * dx + dz * dz);

        double multi = Math.max(1.4, Math.min(5.0, 1.32 + Math.min(remainHoriz, 10.0) * 0.32));
        double attr = Math.max(0.0001, boss.getAttributeValue(Attributes.MOVEMENT_SPEED));
        double vxz = attr * multi * SUSTAIN_FACTOR;
        vxz = clamp(vxz, 0.0, GLOBAL_SPEED_CAP);

        Vec3 cur = boss.getDeltaMovement();
        Vec3 nextDelta = new Vec3(chargeDir.x * vxz, cur.y, chargeDir.z * vxz);

        if (!boss.level().isClientSide) {
            double t = sweptAABBImpactTime(target, nextDelta, HIT_RADIUS);
            if (t >= 0.0 && t <= 1.0) {
                double overshoot = 1.0e-3;
                double u = Math.min(1.0, t + overshoot);
                nextDelta = nextDelta.scale(u);
                pendingHit = true;
            }
        }

        boss.setDeltaMovement(nextDelta);
        boss.hasImpulse = true;

        boss.getLookControl().setLookAt(target, 30.0F, 30.0F);

        Vec3 pos = boss.position();
        if (lastPos == null || pos.distanceToSqr(lastPos) > STUCK_POS_EPS_SQR) {
            stuckTicks = 0;
            lastPos = pos;
        } else if (++stuckTicks > STUCK_TICKS_LIMIT) {
            onMissOrTimeout(target);
            return;
        }

        if (dashTicks > MAX_DASH_TICKS) {
            onMissOrTimeout(target);
        }
    }

    private void internalEndCharge(boolean enterCool) {
        if (enterCool) {
            boss.forceEndCharge(); // 进入冷却的唯一口
        }
        boss.getNavigation().stop();
        inCharge = false;
        chargeDir = Vec3.ZERO;
        dashTicks = 0;
        lastPos = null;
        stuckTicks = 0;
    }

    private void onMissOrTimeout(LivingEntity target) {
        internalEndCharge(false);

        chargeAttempts++;
        if (chargeAttempts < MAX_CHARGE_ATTEMPTS && target != null && target.isAlive()) {
            if (boss.isInChargeCool()) return;
            double dist = boss.distanceTo(target);
            if (dist >= START_MIN_DIST) {
                beginCharge(target, dist);
            } else {
                internalEndCharge(true);
            }
        } else {
            internalEndCharge(true);
        }
    }

    private Vec3 computeDesiredDirection(LivingEntity target, double leadTicks) {
        Vec3 targetPos = target.position();

        Vec3 tv = target.getDeltaMovement();
        Vec3 tvHoriz = new Vec3(tv.x, 0, tv.z);
        if (leadTicks > 0 && tvHoriz.lengthSqr() > 1.0e-6) {
            targetPos = targetPos.add(tvHoriz.scale(leadTicks));
        }

        Vec3 to = targetPos.subtract(boss.position());
        Vec3 horiz = new Vec3(to.x, 0, to.z);
        double len = horiz.length();
        return (len < 1.0e-6) ? Vec3.ZERO : horiz.scale(1.0 / len);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (Math.min(v, hi));
    }

    private double calculateChargeSpeed(double distance) {
        double speedMultiplier = MIN_CHARGE_SPEED + (distance * SPEED_DISTANCE_FACTOR);
        return Math.max(MIN_CHARGE_SPEED, Math.min(MAX_CHARGE_SPEED, speedMultiplier));
    }

    private void applyHitAndKnock(LivingEntity target) {
        if (boss.level().isClientSide) return;

        boss.swing(InteractionHand.MAIN_HAND, true);
        boss.doHurtTarget(target);

        Vec3 horiz = new Vec3(chargeDir.x, 0.0, chargeDir.z);
        if (horiz.lengthSqr() < 1.0e-4) horiz = new Vec3(1.0, 0.0, 0.0);

        target.knockback(KNOCK_STRENGTH, -horiz.x, -horiz.z); // push

        target.setDeltaMovement(target.getDeltaMovement().add(0.0, VERTICAL_BUMP, 0.0));
        target.hasImpulse = true;
    }

    private double sweptAABBImpactTime(LivingEntity target, Vec3 nextDelta, double inflateMargin) {
        AABB moving = boss.getBoundingBox();
        AABB targetBox = target.getBoundingBox();

        AABB m = moving.inflate(inflateMargin);
        AABB t = targetBox.inflate(inflateMargin);

        if (m.intersects(t)) return 0.0;

        double dx = nextDelta.x;
        double dy = nextDelta.y;
        double dz = nextDelta.z;

        final double EPS = 1.0e-8;

        double xEntry, yEntry, zEntry;
        double xExit, yExit, zExit;

        // X axis
        if (Math.abs(dx) < EPS) {
            if (m.maxX < t.minX || m.minX > t.maxX) return -1.0;
            xEntry = Double.NEGATIVE_INFINITY;
            xExit = Double.POSITIVE_INFINITY;
        } else {
            if (dx > 0.0) {
                xEntry = (t.minX - m.maxX) / dx;
                xExit  = (t.maxX - m.minX) / dx;
            } else {
                xEntry = (t.maxX - m.minX) / dx;
                xExit  = (t.minX - m.maxX) / dx;
            }
        }

        // Y axis
        if (Math.abs(dy) < EPS) {
            if (m.maxY < t.minY || m.minY > t.maxY) return -1.0;
            yEntry = Double.NEGATIVE_INFINITY;
            yExit = Double.POSITIVE_INFINITY;
        } else {
            if (dy > 0.0) {
                yEntry = (t.minY - m.maxY) / dy;
                yExit  = (t.maxY - m.minY) / dy;
            } else {
                yEntry = (t.maxY - m.minY) / dy;
                yExit  = (t.minY - m.maxY) / dy;
            }
        }

        // Z axis
        if (Math.abs(dz) < EPS) {
            if (m.maxZ < t.minZ || m.minZ > t.maxZ) return -1.0;
            zEntry = Double.NEGATIVE_INFINITY;
            zExit = Double.POSITIVE_INFINITY;
        } else {
            if (dz > 0.0) {
                zEntry = (t.minZ - m.maxZ) / dz;
                zExit  = (t.maxZ - m.minZ) / dz;
            } else {
                zEntry = (t.maxZ - m.minZ) / dz;
                zExit  = (t.minZ - m.maxZ) / dz;
            }
        }

        double entryTime = Math.max(xEntry, Math.max(yEntry, zEntry));
        double exitTime  = Math.min(xExit, Math.min(yExit, zExit));

        if (entryTime > exitTime || exitTime < 0.0 || entryTime > 1.0) {
            return -1.0;
        }

        return Math.max(0.0, Math.min(1.0, entryTime));
    }
}
