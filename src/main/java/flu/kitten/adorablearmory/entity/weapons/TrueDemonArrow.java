package flu.kitten.adorablearmory.entity.weapons;

import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import flu.kitten.adorablearmory.entity.weapons.cap.DemonArrowStuckProvider;
import flu.kitten.adorablearmory.network.NetworkHandler;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

public class TrueDemonArrow extends Arrow {
    private static final EntityDataAccessor<Boolean> COLLAPSING = SynchedEntityData.defineId(TrueDemonArrow.class, EntityDataSerializers.BOOLEAN);
    @OnlyIn(Dist.CLIENT) private float trailVisibleLen = 0;
    @OnlyIn(Dist.CLIENT) private final Deque<TrailSample> trail = new ArrayDeque<>();
    @OnlyIn(Dist.CLIENT) private static final int TRAIL_MAX_AGE = 30; // 拖尾寿命
    @OnlyIn(Dist.CLIENT) private static final float TRAIL_LEN_MIN = 0.156f;
    @OnlyIn(Dist.CLIENT) private static final float TRAIL_LEN_MAX = 16; // 可见长度
    @OnlyIn(Dist.CLIENT) private static final float SPEED_MIN = 0.56f;
    @OnlyIn(Dist.CLIENT) private static final float SPEED_MAX = 10;
    // 控制撞击后的拖尾收拢动画
    @OnlyIn(Dist.CLIENT) private boolean trailIsCollapsing = false;
    @OnlyIn(Dist.CLIENT) private int trailCollapseTicks = 0;
    @OnlyIn(Dist.CLIENT) private static final int TRAIL_COLLAPSE_DURATION = 60; // 动画时长
    @OnlyIn(Dist.CLIENT) private Vec3 impactPosition = null; // 记录撞击点的精确位置
    // Client trail sampling (arc-length)
    @OnlyIn(Dist.CLIENT) private Vec3 lastSamplePos = null;
    @OnlyIn(Dist.CLIENT) private double sampleRemainder = 0.0;
    @OnlyIn(Dist.CLIENT) private static final int TRAIL_MAX_SAMPLES = 72;
    @OnlyIn(Dist.CLIENT) private float trailVisibleLenO = 0f;
    public static final float DEMON_ARROW_SCALE = 1.24f; // entity size
    // Ultra fast config
    private static final float  LAUNCH_SPEED_BASE = 5;  // 初速基值-格/tick
    private static final float  LAUNCH_SPEED_PER_PULL = 3;  // 满蓄力额外速度
    private static final double AIR_EXTRA_ACCEL = 1.0125; // 额外每tick加速 2%
    private static final double MAX_FLY_SPEED = 12;  // 最高飞行速度上限
    private static final float  HOMING_STRENGTH = 0.80f;   // 基础转向 leap 强度
    private static final float  TURN_RATE_MAX_DEG = 35;  // 每 tick 最大转角
    // Semi-homing config (half tracking)
    private static final int HOMING_DURATION_TICKS = 25; // 追踪持续时间-越小越“半追踪”
    private static final int HOMING_UPDATE_INTERVAL = 2; // 每2tick刷新一次预判方向
    private static final double HOMING_MAX_DIST = 32; // 太远不追踪
    private static final float HOMING_CONE_DEG = 100; // 追踪锥角-越小越不“拐弯追”
    private static final float HOMING_STRENGTH_MIN = 0.10f; // 最弱追踪强度
    private static final float HOMING_STRENGTH_MAX = 0.50f; // 最强追踪强度
    private int homingAge = 0;
    private Vec3 cachedDesiredDir = null;
    private LivingEntity targetEntity = null;
    private static final int COLLAPSE_DURATION = 60; // 服务端收拢总时长
    private int collapseLeft = 0;

    @OnlyIn(Dist.CLIENT)
    public static class TrailSample {
        public double x, y, z;
        public int age;
        public TrailSample(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.age = 0;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public float getTrailVisibleLen(float partialTicks) {
        return Mth.lerp(partialTicks, trailVisibleLenO, trailVisibleLen);
    }

    @OnlyIn(Dist.CLIENT)
    private static float mapSpeedToLen(double sp) {
        float s = (float)sp;
        float k = Mth.clamp((s - SPEED_MIN) / (SPEED_MAX - SPEED_MIN), 0f, 1f);
        return Mth.lerp(k, TRAIL_LEN_MIN, TRAIL_LEN_MAX);
    }

    @OnlyIn(Dist.CLIENT)
    private static double computeSampleStep(double speed) {
        double step = speed * 0.08; // 经验值
        return Mth.clamp(step, 0.07, 0.28);
    }

    @OnlyIn(Dist.CLIENT)
    public Deque<TrailSample> getTrail() {
        return trail;
    }

    @OnlyIn(Dist.CLIENT)
    private void clientTickTrail() {

        if (trailIsCollapsing) {
            for (TrailSample sample : trail) sample.age++;
            return;
        }

        Vec3 headNow = this.position();

        if (lastSamplePos == null) {
            lastSamplePos = headNow;
            trail.clear();
            trail.addFirst(new TrailSample(headNow.x, headNow.y, headNow.z));
            trailVisibleLen = 0f;
            return;
        }

        Vec3 delta = headNow.subtract(lastSamplePos);
        double dist = delta.length();
        if (dist < 1.0e-6) {
            for (TrailSample sample : trail) sample.age++;
            return;
        }

        double speed = this.getDeltaMovement().length();
        double step = computeSampleStep(speed);

        Vec3 dir = delta.scale(1.0 / dist);
        double remaining = dist;

        double carry = sampleRemainder;
        Vec3 pos = lastSamplePos;

        while (carry + remaining >= step) {
            double need = step - carry;
            pos = pos.add(dir.scale(need));
            trail.addFirst(new TrailSample(pos.x, pos.y, pos.z));
            carry = 0.0;
            remaining -= need;

            if (trail.size() > TRAIL_MAX_SAMPLES) trail.removeLast();
        }

        sampleRemainder = carry + remaining;
        lastSamplePos = headNow;

        for (TrailSample sample : trail) sample.age++;
        trail.removeIf(sample -> sample.age > TRAIL_MAX_AGE);

        float target = mapSpeedToLen(speed);
        float leapK = 0.24f; // 越大响应越快-0.15~0.35
        trailVisibleLen = Mth.lerp(leapK, trailVisibleLen, target);
        if (trailVisibleLen < 0f) trailVisibleLen = 0f;
    }

    @OnlyIn(Dist.CLIENT)
    private void beginCollapse(Vec3 hitPos) {
        trailIsCollapsing = true;
        trailCollapseTicks = TRAIL_COLLAPSE_DURATION;
        impactPosition = hitPos;
    }

    public TrueDemonArrow(EntityType<? extends TrueDemonArrow> type, Level level) {
        super(type,level);
    }

    public TrueDemonArrow(Level level, ScarletLoraAlysia boss) {
        this(AdorableArmoryRegister.TRUE_DEMON_ARROW_ENTITY.get(), level);
        this.setOwner(boss);
        this.setYRot(boss.getYRot());
        this.setXRot(boss.getXRot());
        this.refreshDimensions();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        return super.getDimensions(pose).scale(DEMON_ARROW_SCALE);
    }

    @Override
    public void tick() {
        if (this.entityData.get(COLLAPSING)) {
            if (collapseLeft > 0) collapseLeft--;

            super.tick();

            if (this.level().isClientSide) {
                this.trailVisibleLenO = this.trailVisibleLen;
                if (!this.trailIsCollapsing) beginCollapse(this.position());

                this.trailCollapseTicks = Math.min(this.trailCollapseTicks, collapseLeft);

                Vec3 center = (this.impactPosition != null) ? this.impactPosition : this.position();
                float p = 1.0f - (collapseLeft / (float)COLLAPSE_DURATION);
                float k = 0.10f + 0.35f * p;

                for (TrailSample sample : this.trail) {
                    Vec3 sp = new Vec3(sample.x, sample.y, sample.z);
                    Vec3 np = sp.lerp(center, k);
                    sample.x = np.x; sample.y = np.y; sample.z = np.z;
                }

                this.trailVisibleLen *= 0.88f;
                for (TrailSample sample : trail) sample.age++;
                trail.removeIf(sample -> sample.age > TRAIL_MAX_AGE);

                if (collapseLeft <= 0) this.trailVisibleLen = 0f;
                return;
            }

            if (!this.level().isClientSide && collapseLeft <= 0) {
                this.discard();
            }

            return;
        }

        super.tick();

        if (!this.level().isClientSide && this.targetEntity != null && this.targetEntity.isAlive() && !this.inGround) {
            homingAge++;

            Vec3 movement = this.getDeltaMovement();
            double speed = Math.max(movement.length(), 1.0e-3);

            Vec3 toTarget = this.targetEntity.getEyePosition().subtract(this.position());

            if (toTarget.dot(movement) <= 0.0) {
                cachedDesiredDir = null;
            } else if (homingAge <= HOMING_DURATION_TICKS) {

                double dist = toTarget.length();
                if (dist <= HOMING_MAX_DIST) {

                    if (cachedDesiredDir == null || (this.tickCount % HOMING_UPDATE_INTERVAL) == 0) {
                        cachedDesiredDir = computeAdvancedLeadDirection(this, this.targetEntity, speed);
                    }

                    float strengthMul = computeHomingMultiplier(movement, cachedDesiredDir, dist, homingAge);
                    if (strengthMul > 0.001f) {
                        Vec3 newV = enhancedSteer(movement, cachedDesiredDir, speed, strengthMul);
                        this.setDeltaMovement(newV);
                        updateRotationSmooth(newV);
                    }
                }
            }
        }

        Vec3 vec3 = this.getDeltaMovement();

        if (!this.isInWater() && !this.isInLava()) {
            double currentSpeed = vec3.length();
            double targetSpeed = Math.min(MAX_FLY_SPEED, currentSpeed * AIR_EXTRA_ACCEL);
            double accelRate = 0.95;
            double newSpeed = currentSpeed + (targetSpeed - currentSpeed) * accelRate;

            if (currentSpeed > 1e-6) {
                vec3 = vec3.scale(newSpeed / currentSpeed);
            }
        }

        this.setDeltaMovement(vec3);

        if (this.level().isClientSide) {
            this.trailVisibleLenO = this.trailVisibleLen;

            if (this.trailIsCollapsing) {
                this.trailCollapseTicks--;

                if (this.trailCollapseTicks <= 0) {
                    this.trailVisibleLen = 0;
                    return;
                }

                Vec3 center = (this.impactPosition != null) ? this.impactPosition : this.position();
                float p = 1.0f - (this.trailCollapseTicks / (float)TRAIL_COLLAPSE_DURATION);
                float k = 0.10f + 0.35f * p;

                for (TrailSample sample : this.trail) {
                    Vec3 sp = new Vec3(sample.x, sample.y, sample.z);
                    Vec3 np = sp.lerp(center, k);
                    sample.x = np.x; sample.y = np.y; sample.z = np.z;
                }

                this.trailVisibleLen *= 0.88f;
                for (TrailSample sample : trail) sample.age++;
                trail.removeIf(sample -> sample.age > TRAIL_MAX_AGE);

            } else {
                clientTickTrail();
            }
        }

        /*
        if (this.level().isClientSide) {
            Vec3 movement = this.getDeltaMovement();

            double back = 0.10D;
            double px = this.getX() - movement.x * back;
            double py = this.getY() - movement.y * back;
            double pz = this.getZ() - movement.z * back;

            double pvx = movement.x * 0.05D;
            double pvy = movement.y * 0.05D;
            double pvz = movement.z * 0.05D;

            this.level().addParticle(AdorableArmoryRegister.TRUE_DEMON_PARTICLE.get(), px, py, pz, pvx, pvy, pvz);
        }
        */
    }

    @Override
    protected boolean canHitEntity(@NotNull Entity entity) {
        if (this.getOwner() != null && entity == this.getOwner()) return false; // 不命中Owner
        if (this.entityData.get(COLLAPSING)) return false;
        return super.canHitEntity(entity);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult hitResult) {
        if (this.getOwner() != null && hitResult.getEntity() == this.getOwner()) return;

        LivingEntity victim = (hitResult.getEntity() instanceof LivingEntity entity) ? entity : null;

        int count = -1;
        if (!this.level().isClientSide && victim != null) {
            count = victim.getArrowCount();
        }

        // SERVER
        if (!this.level().isClientSide) {
            Entity entity = hitResult.getEntity();
            if (entity instanceof LivingEntity target) {
                Entity owner = this.getOwner();
                if (owner instanceof ScarletLoraAlysia boss && boss.getTarget() == target) {
                    target.invulnerableTime = 0;
                }
            }
        }
        super.onHitEntity(hitResult);

        if (!this.level().isClientSide && victim != null && count >= 0) {
            int after = victim.getArrowCount();
            if (after > count) victim.setArrowCount(count);

            victim.getCapability(DemonArrowStuckProvider.CAPABILITY).ifPresent(cap -> {
                cap.add(1);
                NetworkHandler.syncStuck(victim);
            });
        }
    }

    @Override
    protected void onHitBlock(@NotNull BlockHitResult hitResult) {
        super.onHitBlock(hitResult);
        if (this.level().isClientSide && !this.trailIsCollapsing) {
            this.trailIsCollapsing = true;
            this.trailCollapseTicks = TRAIL_COLLAPSE_DURATION;
            this.impactPosition = hitResult.getLocation();
        }

        if (!this.level().isClientSide) {
            startCollapseServer();
        } else {
            if (!this.trailIsCollapsing) beginCollapse(hitResult.getLocation());
        }
    }

    @Override
    protected float getWaterInertia() {
        return 1;
    }

    @Override
    public boolean shouldBeSaved() {
        return !this.inGround && !this.entityData.get(COLLAPSING);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(COLLAPSING, false);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (this.level().isClientSide && key == COLLAPSING) {
            if (this.entityData.get(COLLAPSING)) {
                this.collapseLeft = COLLAPSE_DURATION;
                if (!this.trailIsCollapsing) beginCollapse(this.position());
            }
        }
    }

    private Vec3 computeAdvancedLeadDirection(Entity projectile, LivingEntity target, double projSpeed) {
        Vec3 targetPos = target.getEyePosition();
        Vec3 targetVel = target.getDeltaMovement();
        Vec3 relativePos = targetPos.subtract(projectile.position());

        Vec3 predictedAccel;
        predictedAccel = targetVel.scale(-0.1);

        double a = targetVel.lengthSqr() - projSpeed * projSpeed;
        double b = 2.0 * relativePos.dot(targetVel);
        double c = relativePos.lengthSqr();

        double interceptTime = 0.0;
        if (Math.abs(a) < 1e-8) {
            interceptTime = (b >= 0) ? 0.0 : -c / Math.max(b, -1e-8);
        } else {
            double discriminant = b * b - 4 * a * c;
            if (discriminant >= 0) {
                double sqrtDisc = Math.sqrt(discriminant);
                double t1 = (-b - sqrtDisc) / (2 * a);
                double t2 = (-b + sqrtDisc) / (2 * a);
                interceptTime = Math.min(t1, t2);
                if (interceptTime < 0) interceptTime = Math.max(t1, t2);
                if (interceptTime < 0) interceptTime = 0.0;
            }
        }

        Vec3 interceptPoint = targetPos.add(targetVel.scale(interceptTime)).add(predictedAccel.scale(0.5 * interceptTime * interceptTime));

        Vec3 aimDirection = interceptPoint.subtract(projectile.position());
        return aimDirection.lengthSqr() > 1e-12 ? aimDirection.normalize() : relativePos.normalize();
    }

    private float computeHomingMultiplier(Vec3 curV, Vec3 desiredDir, double dist, int age) {
        if (desiredDir == null) return 0f;

        Vec3 curDir = (curV.lengthSqr() > 1.0e-9) ? curV.normalize() : desiredDir.normalize();
        Vec3 desDir = desiredDir.normalize();

        double dot = Mth.clamp(curDir.dot(desDir), -1.0, 1.0);
        double cosCone = Math.cos(Math.toRadians(HOMING_CONE_DEG));
        if (dot < cosCone) return 0f;

        float alignK = (float)Mth.clamp((dot - cosCone) / (1.0 - cosCone), 0.0, 1.0);
        float distK = (float)Mth.clamp(1.0 - dist / HOMING_MAX_DIST, 0.0, 1.0);

        float timeK = 1.0f - (age / (float)HOMING_DURATION_TICKS);
        timeK = Mth.clamp(timeK, 0f, 1f);

        float alpha = distK * timeK * (0.35f + 0.65f * alignK); // 0..1
        return Mth.lerp(alpha, HOMING_STRENGTH_MIN, HOMING_STRENGTH_MAX);
    }

    private Vec3 enhancedSteer(Vec3 currentVel, Vec3 desiredDir, double speed, float strengthMul) {
        if (speed < 1.0e-6) return Vec3.ZERO;
        if (desiredDir == null || desiredDir.lengthSqr() < 1.0e-12) return currentVel;

        Vec3 curDir = currentVel.normalize();
        Vec3 desiredDirNorm = desiredDir.normalize();

        double dot = Mth.clamp(curDir.dot(desiredDirNorm), -1.0, 1.0);
        double angleDiff = Math.acos(dot);

        float adaptiveStrength = (float)(HOMING_STRENGTH * (1.0 + angleDiff * 0.3));
        adaptiveStrength = Mth.clamp(adaptiveStrength * strengthMul, 0.02f, 0.85f);

        Vec3 blended = curDir.scale(1.0 - adaptiveStrength).add(desiredDirNorm.scale(adaptiveStrength)).normalize();

        double maxTurnRad = Math.toRadians(TURN_RATE_MAX_DEG * strengthMul);
        maxTurnRad = Math.max(maxTurnRad, Math.toRadians(2.0));

        double actualAngle = Math.acos(Mth.clamp(curDir.dot(blended), -1.0, 1.0));
        if (actualAngle > maxTurnRad) {
            double turnRatio = maxTurnRad / actualAngle;
            float easeOut = (float)(1.0 - Math.pow(1.0 - turnRatio, 2.0));
            blended = curDir.scale(1.0 - easeOut).add(blended.scale(easeOut)).normalize();
        }

        return blended.scale(speed);
    }

    private void updateRotationSmooth(Vec3 velocity) {
        if (velocity.lengthSqr() < 1e-6) return;

        float targetYaw = (float)(Mth.atan2(velocity.x, velocity.z) * (180.0/Math.PI));
        float targetPitch = (float)(Mth.atan2(velocity.y, velocity.horizontalDistance()) * (180.0/Math.PI));

        float yawDiff = Mth.wrapDegrees(targetYaw - this.getYRot());
        float pitchDiff = Mth.wrapDegrees(targetPitch - this.getXRot());

        float maxRotSpeed = 45.0f;
        yawDiff = Mth.clamp(yawDiff, -maxRotSpeed, maxRotSpeed);
        pitchDiff = Mth.clamp(pitchDiff, -maxRotSpeed, maxRotSpeed);

        this.setYRot(this.getYRot() + yawDiff * 0.8f);
        this.setXRot(this.getXRot() + pitchDiff * 0.8f);

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    public static TrueDemonArrow shoot(Level level, ScarletLoraAlysia boss, LivingEntity target, float pullProgress) {
        TrueDemonArrow arrow = new TrueDemonArrow(level, boss);
        arrow.setTargetEntity(target);

        Vec3 subtractPos = target.getEyePosition().subtract(arrow.position());

        float speed = (LAUNCH_SPEED_BASE + LAUNCH_SPEED_PER_PULL * pullProgress);

        arrow.shoot(subtractPos.x, subtractPos.y, subtractPos.z, speed, 0); // 精准无随机散布

        Vec3 direction = subtractPos.normalize();
        arrow.setDeltaMovement(direction.scale(speed));

        arrow.setBaseDamage(10 * Math.max(0.2f, pullProgress));

        // 30%
        if (arrow.level().random.nextFloat() < 0.30) {
            arrow.setCritArrow(true);
            arrow.setKnockback(5);
        } else if (arrow.level().random.nextFloat() < 0.60) {
            arrow.setPierceLevel((byte) 5);
        }

        return arrow;
    }

    private void startCollapseServer() {
        if (this.entityData.get(COLLAPSING)) return;
        this.entityData.set(COLLAPSING, true);
        this.collapseLeft = COLLAPSE_DURATION;
        this.setNoGravity(false);
        this.targetEntity = null;
    }

    public void setTargetEntity(LivingEntity target) {
        this.targetEntity = target;
    }
}
