package flu.kitten.adorablearmory.entity.boss.abilitymanager;

import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;

public final class AlysiaFlightSystem {

    private final ScarletLoraAlysia boss;
    public static final EntityDataAccessor<Boolean> AERIAL_COMBAT_ENABLED = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.BOOLEAN);
    private RangedBowAttackGoal<ScarletLoraAlysia> fastBowGoal;
    private static final boolean DEFAULT_AERIAL_ENABLED = false; // Default false
    private boolean fastBowAdded = false;
    // 悬停追随参数
    private static final double HOVER_MIN_DIST = 5.0D; // Boss 与目标的最小理想水平距离下限
    private static final double HOVER_MAX_DIST = 24.0D; // 最大追踪/射击距离门槛
    private static final double HOVER_MAX2 = HOVER_MAX_DIST * HOVER_MAX_DIST;
    private static final double HOVER_IDEAL_DIST = 12.0D;
    private static final double HOVER_HEIGHT_OFFSET = 2.30D; // Boss 悬停高度参考
    private static final double HOVER_MAX_SPEED = 5.0; // 水平最大速度上限-XZ
    private static final double HOVER_STOP_EPS = 0.60D;
    private static final int HOVER_GRACE_TICKS = 100; // 目标丢失后的“继续悬停保留”时间
    private int hoverGraceLeft = 0;
    // 视线丢失处理
    private static final int NO_LOS_REPOSITION_AFTER = 10;  // 丢 LOS 超过 N tick 才开始主动换位
    private static final int NO_LOS_DROP_TARGET_AFTER = 80; // 丢 LOS 太久直接清目标
    // 丢 LOS 时的“侧移量/爬高量”
    private static final double LOS_ORBIT_OFFSET = 5.0D; // seekLOS 时额外侧移
    private static final double LOS_ASCEND_RATE = 1.D; // seekLOS 时每 tick 上升增量
    private static final double LOS_ASCEND_MAX_EXTRA = 12.0D; // seekLOS 时允许额外爬升的最大高度
    private static final int LOS_RECALL_INTERVAL = 10; // seekLOS 状态下每 N tick 重算候选点/种子
    private static final double LOS_GOAL_REACHED_SQR = 3D; // 到达 losGoal 的距离阈值
    private int orbitDir = 1;
    private int orbitSwapTicks = 0;
    private int noLosTicks = 0;
    // 手持弓状态
    private static final int KEEP_BOW_TICKS = 15; // Boss 在非持续射击时 仍保持弓在手的时间
    private int bowHoldTicks = 0;
    private static final ItemStack IDLE_HAND_ITEM = ItemStack.EMPTY;
    private static final ItemStack BOW_TEMPLATE = new ItemStack(AdorableArmoryRegister.TRUE_DEMON_BOW.get());
    private double hoverY = Double.NaN;
    private Vec3 losGoal = null;
    private int goalRecalculation = 0;
    private double combatAnchorY = Double.NaN;
    private int lastTargetId = -1;
    private static final double COMBAT_MAX_UP = 24.0D; // 允许向上追多少
    private static final double COMBAT_MAX_DOWN = 12.0D; // 允许向下多少
    // LiDAR 雷达扫描
    private static final boolean RADAR_ENABLED = true;
    private static final double RADAR_RANGE = 32.0D; // 雷达射线长度
    private static final int RADAR_RAYS_PER_TICK_MIN = 6;
    private static final int RADAR_RAYS_PER_TICK_MAX = 26;
    private static final int RADAR_FOCUSED_RAYS = 24;
    private static final int RADAR_MAX_HITS = 120; // 最多记多少个Block命中
    private static final int RADAR_TTL_TICKS = 20;
    private static final int RADAR_YAW_SAMPLES = 32; // 全向扫描采样密度
    private static final double[] RADAR_PITCH_SAMPLES = new double[] { -0.35, -0.22, -0.10, 0.0, 0.12, 0.24, 0.35 };
    private static final double[] RADAR_FOCUS_YAW_OFF = new double[] { 0.0, 0.22, -0.22, 0.42, -0.42, 0.62 };
    private static final double[] RADAR_FOCUS_PITCH_OFF = new double[] { 0.0, 0.12, -0.12 };
    // 雷达命中轻量结构
    private record RadarHit(long key, BlockPos pos, Direction face, Vec3 hitLoc) {}
    private final Vec3[] radarDirs;
    private int radarCursor = 0;
    private int radarTtl = 0;
    private final ArrayDeque<RadarHit> radarHits = new ArrayDeque<>(RADAR_MAX_HITS);
    private final HashSet<Long> radarHitSeen = new HashSet<>(RADAR_MAX_HITS * 4);
    private static final double VIS_GATE_NORMAL = 0.10;
    private static final double VIS_GATE_BREACH = 0.02;
    private static final int BREACH_AFTER_TICKS = 10; // 丢 LOS 一段时间后进入“找洞口/贴近掩体”模式
    private static final double[] LOS_RADII_CLOSE = new double[] { 2.4D, 4.0D, 5.5D };
    // clip budget config
    private static final int CLIP_BUDGET_SOFT = 60;
    private static final int CLIP_BUDGET_HARD = 120;
    private static final int AUX_CLIP_SOFT = 14;
    private static final int AUX_CLIP_HARD = 26;
    private final ClipBudget tickBudget = new ClipBudget(CLIP_BUDGET_SOFT, CLIP_BUDGET_HARD);
    private final ClipBudget auxBudget = new ClipBudget(AUX_CLIP_SOFT, AUX_CLIP_HARD);
    private int auxBudgetTick = -1;
    // Smarter collision avoidance (tunable)
    private static final double AVOID_LOOKAHEAD_TICKS = 1.25; // 预测多少tick后的扫掠
    private static final int AVOID_SWEEP_STEPS = 5; // 扫掠采样步数
    private static final double AVOID_TRIAL_SCALE = 1.15; // 试探向量额外放大检测
    private static final double AVOID_ARC_SPEED_SCALE = 0.85; // 绕行时水平降速比例
    private static final double AVOID_MAX_VY = 0.50;
    private static final double[] AVOID_YAW_OFFSETS = new double[] { 0.35, -0.35, 0.70, -0.70, 1.05, -1.05, 1.40, -1.40 };
    private static final double[] AVOID_Y_OFFSETS = new double[] { 0.0, 0.22, -0.22, 0.38, -0.38 };
    private int lastCanShootTick = -999999;
    private int lastCanShootTargetIdCache = -1;
    private boolean lastCanShootValue = false;
    // Stuck-teleport (tunable)
    private static final boolean STUCK_TELEPORT_ENABLED = true;
    private static final int STUCK_TELEPORT_TRIGGER_NOLOS = 50;
    private static final int STUCK_TELEPORT_MAX_STAY_TICKS = 100; // 传送模式最多维持多久
    private static final int STUCK_TELEPORT_COOLDOWN_TICKS = 65; // 传送结束后的冷却
    private static final int STUCK_TELEPORT_REPIN_INTERVAL = 5; // 每隔多少 tick 重新校验/刷新一次“贴脸位置”
    private static final double[] STUCK_TELEPORT_Y_OFFSETS = new double[] { 0.05D, 0.85D, 1.65D, -0.35D };
    // Stuck-teleport state
    private boolean stuckTeleporting = false;
    private Vec3 stuckReturnPos = null;
    private float stuckReturnYaw = 0.0F;
    private float stuckReturnPitch = 0.0F;
    private double stuckReturnAnchorY = Double.NaN;
    private int stuckTargetId = -1;
    private int stuckStayTicks = 0;
    private int stuckCooldown = 0;
    private Vec3 stuckOffset = Vec3.ZERO;
    private int stuckRepinTick = 0;
    private static final int LOS_GOAL_STALL_TRIGGER = 47; // 1.25s-20tps
    private static final double LOS_GOAL_PROGRESS_EPS_SQR = 0.32D; // 距离平方的“进展阈值”
    private static final int STUCK_TELEPORT_HARD_TIMEOUT = 100; // 强制tick传送
    private Vec3 lastLosGoalRef = null;
    private double lastLosGoalDist2 = Double.NaN;
    private int losGoalStallTicks = 0;
    private static final int FAST_DRAW_TICKS = 5; // 弓箭射击速度
    private int lastFastShotTick = -999999;

    public AlysiaFlightSystem(ScarletLoraAlysia boss) {
        this.boss = boss;
        this.radarDirs = buildRadarDirs();
    }

    private ClipBudget auxBudgetThisTick() {
        int count = boss.tickCount;
        if (auxBudgetTick != count) {
            if (auxBudgetTick != -1) auxBudget.endTick();
            auxBudgetTick = count;
            auxBudget.beginTick();
        }
        return auxBudget;
    }

    private long radarKey(BlockPos blockPos, Direction face) {
        return (blockPos.asLong() << 3) | (long)(face.ordinal() & 7);
    }

    public @NotNull PathNavigation createNavigation(@NotNull Level level) {
        FlyingPathNavigation pathNavigation = new FlyingPathNavigation(boss, level);
        pathNavigation.setCanOpenDoors(false);
        pathNavigation.setCanPassDoors(true);
        pathNavigation.setCanFloat(true);
        return pathNavigation;
    }

    public static void defineSynchedData(ScarletLoraAlysia boss) {
        boss.getEntityData().define(AERIAL_COMBAT_ENABLED, DEFAULT_AERIAL_ENABLED);
    }

    private LivingEntity findBestVisibleTarget() {
        double range = boss.getAttributeValue(Attributes.FOLLOW_RANGE);
        AABB box = boss.getBoundingBox().inflate(range, range, range);

        LivingEntity best = null;
        double bestD2 = Double.MAX_VALUE;

        for (LivingEntity entity : boss.level().getEntitiesOfClass(LivingEntity.class, box, boss::isValidTarget)) {
            if (entity == null || !entity.isAlive()) continue;
            if (!canShootTarget(entity)) continue;

            double distance = boss.distanceToSqr(entity);
            if (distance < bestD2) {
                bestD2 = distance;
                best = entity;
            }
        }
        return best;
    }

    private LivingEntity reacquireNearestTarget() {
        double range = boss.getAttributeValue(Attributes.FOLLOW_RANGE);
        TargetingConditions cond = TargetingConditions.forCombat().range(range).ignoreLineOfSight().ignoreInvisibilityTesting().selector(boss::isValidTarget);
        AABB box = boss.getBoundingBox().inflate(range, range, range);
        return boss.level().getNearestEntity(boss.level().getEntitiesOfClass(LivingEntity.class, box, boss::isValidTarget), cond, boss, boss.getX(), boss.getY(), boss.getZ());
    }

    public void setAerialCombatEnabled(boolean enabled) {
        boolean prev = boss.isAerialCombatEnabled();
        boss.setAerialEnabledData(enabled);

        if (prev != enabled) {
            applyMovementStackFor(enabled);
            updateAerialSystemGoals();

            if (!enabled) {
                ensureHovering(false);
                this.bowHoldTicks = 0;
                if (boss.isUsingItem()) boss.stopUsingItem();
                stowBowIfIdle();
                combatAnchorY = Double.NaN;
                lastTargetId = -1;
            } else {
                this.hoverGraceLeft = HOVER_GRACE_TICKS;
            }
        }
    }

    private void applyMovementStackFor(boolean aerial) {
        if (aerial) {
            boss.setMoveAndNavigation(new FlyingMoveControl(boss, 10, true), new FlyingPathNavigation(boss, boss.level()));
            boss.setNoGravity(true);
        } else {
            boss.setMoveAndNavigation(new MoveControl(boss), new GroundPathNavigation(boss, boss.level()));
            boss.setNoGravity(false);

            this.hoverY = Double.NaN;
            Vec3 movement = boss.getDeltaMovement();
            boss.setDeltaMovement(movement.x, 0.0D, movement.z);
        }
    }

    public void registerGoals(GoalSelector goalSelector) {
        if (this.fastBowGoal == null) {
            this.fastBowGoal = new RangedBowAttackGoal<>(boss, 1.0D, FAST_DRAW_TICKS, (float) HOVER_MAX_DIST) {
                @Override
                public boolean canUse() {
                    LivingEntity target = boss.getTarget();
                    if (target == null || !target.isAlive()) return false;
                    if (boss.getCurrentPhase() != 1) return false;
                    return canShootTarget(target);
                }

                @Override
                public boolean canContinueToUse() {
                    LivingEntity target = boss.getTarget();
                    if (target == null || !target.isAlive()) return false;
                    if (boss.getCurrentPhase() != 1) return false;
                    return canShootTarget(target);
                }

                @Override
                public void start() {
                    equipBowIfNeeded();
                    bowHoldTicks = KEEP_BOW_TICKS;
                    super.start();
                }

                @Override
                public void tick() {
                    LivingEntity target = boss.getTarget();
                    if (target == null || !target.isAlive() || !canShootTarget(target)) {
                        if (boss.isUsingItem()) boss.stopUsingItem();
                        return;
                    }

                    super.tick();
                    bowHoldTicks = KEEP_BOW_TICKS;

                    if (!(boss.getMainHandItem().getItem() instanceof BowItem)) {
                        equipBowIfNeeded();
                    }
                }

                @Override
                public void stop() {
                    super.stop();
                }
            };

            setRangedBowAttackTime(this.fastBowGoal);
        }

        updateAerialSystemGoals();
    }

    public void serverTick() {
        if (boss.level().isClientSide) return;
        LivingEntity target = boss.getTarget();

        if (stuckTeleporting) {
            if (target != null && target.isAlive()) tryFastShoot(target);
            return;
        }

        updateAerialSystemGoals();
    }

    public void customServerAiStepPre() {
        if (boss.isAerialCombatEnabled() && boss.getTarget() != null && boss.getTarget().isAlive() && !(boss.getMainHandItem().getItem() instanceof BowItem)) {
            equipBowIfNeeded();
        }
    }

    public void customServerAiStepPost() {
        if (boss.level().isClientSide) return;

        tickBudget.beginTick();
        if (stuckCooldown > 0) stuckCooldown--; // stuck-teleport cool

        try {
            if (RADAR_ENABLED && radarTtl > 0) {
                radarTtl--;
                if (radarTtl == 0) radarClear();
            }
            LivingEntity target = boss.getTarget();

            // If we are in stuck-teleport mode, keep pinned to target and shoot
            if (stuckTeleporting) {
                if (target == null || !target.isAlive() || target.getId() != stuckTargetId) {
                    if (boss.isUsingItem() && boss.getUseItem().getItem() instanceof BowItem) boss.stopUsingItem();
                    boss.setChargingBowFlag(false);
                    boss.setTarget(null);
                    endStuckTeleport();
                } else {
                    stuckStayTicks++;
                    if (stuckStayTicks > STUCK_TELEPORT_MAX_STAY_TICKS) {
                        endStuckTeleport();
                    } else {
                        stuckRepinTick++;
                        if (stuckRepinTick >= STUCK_TELEPORT_REPIN_INTERVAL) {
                            stuckRepinTick = 0;

                            Vec3 desired = target.position().add(stuckOffset);
                            if (!isTeleportPosSafe(desired) || !isTeleportCellEmpty(desired) || intersectsTargetAt(desired, target) || !canShootFromFeetNoBudget(desired, target)) {
                                Vec3 safe = findSafePosNearTarget(target);
                                if (safe != null) {
                                    stuckOffset = safe.subtract(target.position());
                                    desired = safe;
                                }
                            }

                            if (isTeleportPosSafe(desired) && isTeleportCellEmpty(desired) && !intersectsTargetAt(desired, target)) {
                                boss.teleportTo(desired.x, desired.y, desired.z);
                                boss.setDeltaMovement(Vec3.ZERO);
                                boss.getNavigation().stop();
                                boss.hasImpulse = true;
                            }
                        }

                        ensureHovering(true);
                        noLosTicks = 0;

                        equipBowIfNeeded();
                        bowHoldTicks = KEEP_BOW_TICKS;
                        boss.getLookControl().setLookAt(target, 90.0F, 90.0F);

                        if (!boss.isUsingItem()) {
                            boss.startUsingItem(InteractionHand.MAIN_HAND);
                        }

                        return;
                    }
                }
            }

            if (boss.isAerialCombatEnabled()) {
                if (target != null && target.isAlive()) {

                    int tid = target.getId();
                    if (tid != lastTargetId) {
                        lastTargetId = tid;
                        combatAnchorY = boss.getY();
                        radarClear();
                        planner.reset();
                        losGoal = null;
                    }
                    if (Double.isNaN(combatAnchorY)) combatAnchorY = boss.getY();

                    hoverGraceLeft = HOVER_GRACE_TICKS;

                    boolean canShoot = canShootTarget(target, tickBudget);
                    if (canShoot) {
                        noLosTicks = 0;
                        clearLosGoal();
                        ensureHovering(true);
                        serverHoverChaseAndHoldSmart(target, false);

                        if (!boss.isUsingItem()) boss.startUsingItem(InteractionHand.MAIN_HAND);
                        tryFastShoot(target); // 正常战斗也走快速射击
                    } else {
                        noLosTicks++;

                        if (boss.isUsingItem()) boss.stopUsingItem();

                        LivingEntity visibleAlt = findBestVisibleTarget();
                        if (visibleAlt != null) {
                            boss.setTarget(visibleAlt);
                            target = visibleAlt;
                            noLosTicks = 0;
                            clearLosGoal();
                            ensureHovering(true);
                            serverHoverChaseAndHoldSmart(target, false);
                        } else {
                            ensureHovering(true);

                            if (noLosTicks >= NO_LOS_REPOSITION_AFTER) {
                                if (RADAR_ENABLED) {
                                    int rays = Mth.clamp(RADAR_RAYS_PER_TICK_MIN + (noLosTicks / 8), RADAR_RAYS_PER_TICK_MIN, RADAR_RAYS_PER_TICK_MAX);
                                    radarTickScanBudgeted(target, rays, tickBudget);
                                }

                                updateLosGoalBudgeted(target, tickBudget);

                                tickLosGoalStall();
                                if (losGoal != null && losGoalStallTicks >= LOS_GOAL_STALL_TRIGGER) {
                                    clearLosGoal();
                                }

                                if (losGoal != null && losGoal.distanceToSqr(boss.position()) < LOS_GOAL_REACHED_SQR) {
                                    goalRecalculation = 0;
                                }

                                // ultimate fallback: stuck-teleport to target
                                if (shouldStuckTeleport(target)) {
                                    beginStuckTeleport(target);
                                    if (stuckTeleporting) return;
                                }

                                serverHoverChaseAndHoldSmart(target, true);
                            } else {
                                serverHoverChaseAndHoldSmart(target, false);
                            }

                            if (noLosTicks >= NO_LOS_DROP_TARGET_AFTER) {
                                boss.setTarget(null);
                                noLosTicks = 0;
                                lastTargetId = -1;
                                clearLosGoal();
                            }
                        }
                    }
                } else {
                    noLosTicks = 0;
                    if (hoverGraceLeft > 0) {
                        hoverGraceLeft--;
                        ensureHovering(true);
                        serverHoverIdle();
                        LivingEntity react = reacquireNearestTarget();
                        if (react != null) boss.setTarget(react);
                    } else {
                        ensureHovering(false);
                    }
                }
            } else {
                ensureHovering(false);
                if (boss.isUsingItem()) boss.stopUsingItem();
            }

            if (this.bowHoldTicks > 0) {
                this.bowHoldTicks--;
                if (this.bowHoldTicks == 0) {
                    if (boss.getTarget() == null && this.hoverGraceLeft == 0 && !boss.isUsingItem()) {
                        stowBowIfIdle();
                    } else {
                        this.bowHoldTicks = 10;
                    }
                }
            }

            if (boss.getChargingBowFlag() && boss.getTarget() == null) {
                boss.setChargingBowFlag(false);
            }
        } finally {
            tickBudget.endTick();
        }
    }

    private void radarTickScanBudgeted(LivingEntity target, int raysPerTick, ClipBudget budget) {
        Level level = boss.level();
        if (level.isClientSide) return;
        if (radarDirs == null || radarDirs.length == 0) return;

        Vec3 from = boss.getEyePosition();

        int casted = 0;

        int n = Math.max(0, raysPerTick);
        for (int k = 0; k < n; k++) {
            HitResult hit = clipBudgeted(from, from.add(radarDirs[radarCursor++ % radarDirs.length].scale(RADAR_RANGE)), budget);
            if (hit == null) break;
            if (hit.getType() == HitResult.Type.BLOCK) {
                radarAddHit((BlockHitResult) hit);
            }
            casted++;
        }

        if (target != null && target.isAlive() && budget.left > 0) {
            Vec3 aim = target.getEyePosition();
            Vec3 v = aim.subtract(from);
            double len2 = v.lengthSqr();
            if (len2 > 1.0E-6) {
                Vec3 nv = v.normalize();
                double baseYaw = Math.atan2(-nv.x, nv.z);
                double basePitch = Math.asin(Mth.clamp(nv.y, -1.0, 1.0));

                int focused = 0;
                for (int i = 0; i < RADAR_FOCUS_YAW_OFF.length && focused < RADAR_FOCUSED_RAYS; i++) {
                    double yaw = baseYaw + RADAR_FOCUS_YAW_OFF[i];
                    for (int j = 0; j < RADAR_FOCUS_PITCH_OFF.length && focused < RADAR_FOCUSED_RAYS; j++) {
                        HitResult hit = clipBudgeted(
                                from,
                                from.add(dirFromYawPitch(yaw, Mth.clamp(basePitch + RADAR_FOCUS_PITCH_OFF[j], -1.25, 1.25)).scale(RADAR_RANGE)),
                                budget
                        );
                        if (hit == null) break;
                        if (hit.getType() == HitResult.Type.BLOCK) {
                            radarAddHit((BlockHitResult) hit);
                        }
                        focused++;
                    }
                    if (budget.left <= 0) break;
                }
            }
        }

        if (casted > 0) radarTtl = RADAR_TTL_TICKS;
    }

    public void onFinalizeSpawn() {
        if (boss.isAerialCombatEnabled()) {
            boss.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            boss.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
            if (!boss.level().isClientSide) boss.setGuaranteedDrop(EquipmentSlot.MAINHAND);
        } else {
            boss.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    public void markBowHold() {
        this.bowHoldTicks = KEEP_BOW_TICKS;
    }

    private void updateAerialSystemGoals() {
        if (this.fastBowGoal == null) return;

        boolean wantBowGoal = boss.isAerialCombatEnabled() && !stuckTeleporting;

        if (wantBowGoal) {
            if (!fastBowAdded) {
                boss.goalSelector.addGoal(4, fastBowGoal);
                fastBowAdded = true;
            }
        } else {
            if (fastBowAdded) {
                boss.goalSelector.removeGoal(fastBowGoal);
                fastBowAdded = false;
            }

            if (!stuckTeleporting) {
                if (boss.isUsingItem() && boss.getUseItem().getItem() instanceof BowItem) {
                    boss.stopUsingItem();
                }
            }
        }
    }

    private static void setRangedBowAttackTime(RangedBowAttackGoal<?> goal) {
        try {
            Field attackTime = ObfuscationReflectionHelper.findField(RangedBowAttackGoal.class, "f_25786_"); // attackTime
            attackTime.setAccessible(true);
            attackTime.setInt(goal, 5);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ensureHovering(boolean hovering) {
        boss.setNoGravity(hovering);
        if (hovering) {
            LivingEntity target = boss.getTarget();
            if (target != null && target.isAlive()) {
                double distance = boss.distanceToSqr(target);
                if (distance <= (HOVER_MAX2 * 1.05)) {
                    boss.getNavigation().stop();
                }
            } else {
                boss.getNavigation().stop();
            }
            boss.fallDistance = 0.0F;
        } else {
            this.hoverY = Double.NaN;
        }
    }

    private void serverHoverChaseAndHoldSmart(LivingEntity target, boolean seekLOS) {
        // 高度
        final double ALTITUDE_LEAP = 0.10D;
        final double ALTITUDE_MAX_RATE = 0.10D;  // 每tick最大高度变量
        final double ALTITUDE_DEADLINE = 0.01D;   // 高度误差死区
        // 水平
        final double MAX_SPEED_XZ = HOVER_MAX_SPEED;  // 水平最大速度
        final double GAIN_SPEED = 0.20D;  // 误差 -> 期望水平速度
        final double GAIN_ACCEL = 0.30D;  // 速度误差 -> 水平加速度
        // 垂直
        final double MAX_ASCENT = 0.50D;  // 竖直最大速度-上/下
        final double GAIN_VY = 0.24D; // 高度误差 -> 期望竖直速度比例
        final double GAIN_AY = 0.24D; // 速度误差 -> 竖直加速度
        // 阻尼
        final double DAMP_XZ = 0.65D;  // 水平阻尼
        final double DAMP_Y = 0.65D;  // 竖直阻尼

        if (Double.isNaN(this.hoverY)) this.hoverY = boss.getY();

        final double refY = clampCombatY(target.getEyeY() + HOVER_HEIGHT_OFFSET);
        double blendedY = Mth.lerp(ALTITUDE_LEAP, this.hoverY, refY);
        double dySet = Mth.clamp(blendedY - this.hoverY, -ALTITUDE_MAX_RATE, ALTITUDE_MAX_RATE);
        this.hoverY += dySet;

        int minY = boss.level().getMinBuildHeight() + 1;
        int maxY = boss.level().getMaxBuildHeight() - 3;
        this.hoverY = Mth.clamp(this.hoverY, minY, maxY);

        final double anchorX = target.getX();
        final double anchorZ = target.getZ();

        Vec3 horiz = new Vec3(boss.getX() - anchorX, 0.0D, boss.getZ() - anchorZ);
        if (horiz.lengthSqr() < 1.0E-4D) {
            Vec3 look = target.getLookAngle();
            horiz = new Vec3(-look.z, 0.0D, look.x); // 近用目标朝向的法向量当切线
        }

        //double horizDist = Math.sqrt(horiz.lengthSqr());
        double desiredDist = Mth.clamp(HOVER_IDEAL_DIST, HOVER_MIN_DIST, HOVER_MAX_DIST); // 理想距离
        Vec3 dirH = horiz.normalize();

        Vec3 tangent = new Vec3(-dirH.z, 0.0D, dirH.x); // 环绕切线
        if (seekLOS) {
            orbitSwapTicks++;
            if (orbitSwapTicks >= 40) {
                orbitSwapTicks = 0;
                orbitDir = -orbitDir;
            }
        }

        Vec3 desiredPos;

        if (seekLOS && losGoal != null) {
            desiredPos = losGoal;
            this.hoverY = desiredPos.y;
        } else {
            desiredPos = new Vec3(anchorX + dirH.x * desiredDist, this.hoverY, anchorZ + dirH.z * desiredDist);

            if (seekLOS) {
                desiredPos = desiredPos.add(tangent.scale(LOS_ORBIT_OFFSET * orbitDir));

                double maxSeekY = clampCombatY(target.getEyeY() + HOVER_HEIGHT_OFFSET + LOS_ASCEND_MAX_EXTRA);
                this.hoverY = Math.min(this.hoverY + LOS_ASCEND_RATE, maxSeekY);
                desiredPos = new Vec3(desiredPos.x, this.hoverY, desiredPos.z);
            }
        }

        Vec3 delta = desiredPos.subtract(boss.position());
        Vec3 v = boss.getDeltaMovement();

        Vec3 deltaH = new Vec3(delta.x, 0.0D, delta.z);
        Vec3 velH = new Vec3(v.x, 0.0D, v.z);
        double errH = deltaH.length();

        if (errH > HOVER_STOP_EPS) {
            double desiredSpeed = Math.min(MAX_SPEED_XZ, errH * GAIN_SPEED);

            Vec3 desiredVelH = deltaH.normalize().scale(desiredSpeed);
            Vec3 accelH = desiredVelH.subtract(velH).scale(GAIN_ACCEL);
            v = v.add(accelH.x, 0.0D, accelH.z);
        }

        Vec3 vH = new Vec3(v.x, 0.0D, v.z);
        double spdH = vH.length();
        if (spdH > MAX_SPEED_XZ) {
            vH = vH.scale(MAX_SPEED_XZ / spdH);
            v = new Vec3(vH.x, v.y, vH.z);
        }

        double yErr = delta.y;
        if (Math.abs(yErr) > ALTITUDE_DEADLINE) {
            double desiredVy = Mth.clamp(yErr * GAIN_VY, -MAX_ASCENT, MAX_ASCENT);
            double ay = (desiredVy - v.y) * GAIN_AY;
            v = v.add(0.0D, ay, 0.0D);
        } else {
            v = v.add(0.0D, -v.y * 0.2D, 0.0D);
        }

        if (v.y >  MAX_ASCENT) v = new Vec3(v.x,  MAX_ASCENT, v.z);
        if (v.y < -MAX_ASCENT) v = new Vec3(v.x, -MAX_ASCENT, v.z);

        v = new Vec3(v.x * DAMP_XZ, v.y * DAMP_Y, v.z * DAMP_XZ);

        v = steerAvoidCollisions(v);
        boss.setDeltaMovement(v);
        boss.hasImpulse = true;

        Vec3 vel = boss.getDeltaMovement();
        Vec3 velH2 = new Vec3(vel.x, 0.0D, vel.z);
        Vec3 toTargetH = new Vec3(target.getX() - boss.getX(), 0.0D, target.getZ() - boss.getZ());
        double distH = toTargetH.length();
        final double RETREAT_FACE_LOCK_DIST = (HOVER_MIN_DIST + HOVER_MAX_DIST) / 2;

        boolean movingFast = velH2.lengthSqr() > 1.0E-4D;
        boolean retreatFaceLock = distH < RETREAT_FACE_LOCK_DIST;

        if (!retreatFaceLock && movingFast && !boss.isUsingItem()) {
            float yaw = (float)(Mth.atan2(velH2.x, velH2.z) * (180F / Math.PI));
            boss.setYRot(yaw);
            boss.setYBodyRot(yaw);
            boss.setYHeadRot(yaw);

            boss.getLookControl().setLookAt(boss.getX() + velH2.x, boss.getEyeY(), boss.getZ() + velH2.z, 90.0F, 90.0F);
        } else {
            boss.getLookControl().setLookAt(target, 90.0F, 90.0F);
            boss.setYBodyRot(boss.getYHeadRot());
        }
    }

    private Vec3 steerAvoidCollisions(Vec3 desiredVel) {
        boss.level();
        if (desiredVel.lengthSqr() < 1e-6) return desiredVel;

        if (sweptNoCollision(desiredVel, AVOID_LOOKAHEAD_TICKS)) return desiredVel;

        Vec3 vH = new Vec3(desiredVel.x, 0.0, desiredVel.z);
        double spd = vH.length();
        if (spd < 1e-4) {
            Vec3 vY = new Vec3(0.0, Mth.clamp(desiredVel.y, -AVOID_MAX_VY, AVOID_MAX_VY), 0.0);
            if (sweptNoCollision(vY, AVOID_LOOKAHEAD_TICKS)) return vY;
            return desiredVel.scale(0.35);
        }

        Vec3 dir = vH.scale(1.0 / spd);

        for (double yawOff : AVOID_YAW_OFFSETS) {
            double ca = Math.cos(yawOff), sa = Math.sin(yawOff);
            Vec3 rotDir = new Vec3(dir.x * ca - dir.z * sa, 0.0, dir.x * sa + dir.z * ca);

            Vec3 rotH = rotDir.scale(spd * AVOID_ARC_SPEED_SCALE);

            for (double yOff : AVOID_Y_OFFSETS) {
                double vy = Mth.clamp(desiredVel.y + yOff, -AVOID_MAX_VY, AVOID_MAX_VY);
                Vec3 trial = new Vec3(rotH.x, vy, rotH.z);

                if (sweptNoCollision(trial.scale(AVOID_TRIAL_SCALE), AVOID_LOOKAHEAD_TICKS)) {
                    return trial;
                }
            }
        }

        Vec3 up = new Vec3(desiredVel.x * 0.15, Math.max(desiredVel.y, 0.28), desiredVel.z * 0.15);
        if (sweptNoCollision(up.scale(AVOID_TRIAL_SCALE), AVOID_LOOKAHEAD_TICKS)) return up;

        Vec3 down = new Vec3(desiredVel.x * 0.15, Math.min(desiredVel.y, -0.28), desiredVel.z * 0.15);
        if (sweptNoCollision(down.scale(AVOID_TRIAL_SCALE), AVOID_LOOKAHEAD_TICKS)) return down;

        // 强制减速
        return desiredVel.scale(0.35);
    }

    private void clearLosGoal() {
        losGoal = null;
        goalRecalculation = 0;
        planner.reset();
        radarClear();

        lastLosGoalRef = null;
        lastLosGoalDist2 = Double.NaN;
        losGoalStallTicks = 0;
    }

    private Vec3 findSafePosNearTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return null;

        Vec3 base = target.position();
        Vec3 best = null;
        double bestScore = -1e18;

        Direction[] dirs = new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};

        for (Direction dir : dirs) {
            if (dir.getAxis() != Direction.Axis.Y) {

                final double cx = base.x + dir.getStepX();
                final double cz = base.z + dir.getStepZ();

                for (double yOff : STUCK_TELEPORT_Y_OFFSETS) {
                    Vec3 pos = new Vec3(cx, base.y + yOff, cz);

                    if (!isTeleportCellEmpty(pos)) continue;
                    if (!isTeleportPosSafe(pos)) continue;
                    if (intersectsTargetAt(pos, target)) continue;

                    boolean los = canShootFromFeetNoBudget(pos, target);
                    double score = (los ? 1200.0 : 0.0) - pos.distanceToSqr(boss.position()) * 0.02 - Math.abs(pos.y - base.y) * 0.25;

                    if (score > bestScore) { bestScore = score; best = pos; }
                    if (los) return pos;
                }
            } else {

                Vec3 pos = new Vec3(base.x, base.y + dir.getStepY(), base.z);

                if (!isTeleportCellEmpty(pos)) continue;
                if (!isTeleportPosSafe(pos)) continue;
                if (intersectsTargetAt(pos, target)) continue;

                boolean los = canShootFromFeetNoBudget(pos, target);
                double score = (los ? 1200.0 : 0.0) - pos.distanceToSqr(boss.position()) * 0.02 - 0.5; // tiny penalty so we still prefer horizontal if possible

                if (score > bestScore) { bestScore = score; best = pos; }
                if (los) return pos;
            }
        }

        return best; // may be null -> stuck-teleport cancels cleanly
    }

    private void beginStuckTeleport(LivingEntity target) {
        if (target == null || !target.isAlive()) return;

        // 保存“原来的位置”
        stuckReturnPos = boss.position();
        stuckReturnYaw = boss.getYRot();
        stuckReturnPitch = boss.getXRot();
        stuckReturnAnchorY = combatAnchorY;

        stuckTeleporting = true;
        updateAerialSystemGoals(); // 立刻移除 fastBowGoal
        stuckStayTicks = 0;
        stuckTargetId = target.getId();
        stuckRepinTick = 0;

        // 停止当前动作-避免拉弓状态异常
        if (boss.isUsingItem()) boss.stopUsingItem();
        boss.getNavigation().stop();
        boss.setDeltaMovement(Vec3.ZERO);
        boss.hasImpulse = true;

        Vec3 safe = findSafePosNearTarget(target);
        Vec3 from = boss.position();
        if (safe == null) {
            stuckTeleporting = false;
            stuckReturnPos = null;
            stuckTargetId = -1;
            updateAerialSystemGoals();
            return;
        }

        stuckOffset = safe.subtract(target.position());

        boss.teleportTo(safe.x, safe.y, safe.z);
        boss.level().playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 1.2f);
        boss.setDeltaMovement(Vec3.ZERO);
        boss.getNavigation().stop();
        boss.hasImpulse = true;

        ensureHovering(true);
        noLosTicks = 0;
        clearLosGoal();

        equipBowIfNeeded();
        bowHoldTicks = KEEP_BOW_TICKS;
        boss.startUsingItem(InteractionHand.MAIN_HAND);
    }

    private void endStuckTeleport() {
        if (boss.isUsingItem() && boss.getUseItem().getItem() instanceof BowItem) {
            boss.stopUsingItem();
        }
        if (boss.getChargingBowFlag()) boss.setChargingBowFlag(false);

        stuckTeleporting = false;
        updateAerialSystemGoals();
        stuckTargetId = -1;
        stuckStayTicks = 0;
        stuckOffset = Vec3.ZERO;
        stuckRepinTick = 0;

        stuckCooldown = STUCK_TELEPORT_COOLDOWN_TICKS;
        if (stuckReturnPos == null) return;

        Vec3 from = boss.position();
        Vec3 back = stuckReturnPos;
        Vec3 best = null;

        if (isTeleportPosSafe(back)) {
            best = back;
        } else {
            BlockPos containing = BlockPos.containing(back);
            if (boss.level().hasChunkAt(containing)) {
                double[] radii = new double[] { 0.5D, 1.0D, 1.8D, 2.6D };
                int samples = 12;
                for (double r : radii) {
                    for (int i = 0; i < samples; i++) {
                        double a = (i / (double)samples) * (Math.PI * 2.0);
                        for (double yOff : STUCK_TELEPORT_Y_OFFSETS) {
                            Vec3 p = new Vec3(back.x + Math.cos(a) * r, back.y + yOff, back.z + Math.sin(a) * r);
                            if (isTeleportPosSafe(p)) { best = p; break; }
                        }
                        if (best != null) break;
                    }
                    if (best != null) break;
                }
            }
        }

        if (best != null) {
            boss.teleportTo(best.x, best.y, best.z);
            boss.level().playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 1.2f);
            boss.setYRot(stuckReturnYaw);
            boss.setXRot(stuckReturnPitch);
            boss.setYHeadRot(stuckReturnYaw);
            boss.setYBodyRot(stuckReturnYaw);
            boss.setDeltaMovement(Vec3.ZERO);
            boss.getNavigation().stop();
            boss.hasImpulse = true;
        }

        combatAnchorY = stuckReturnAnchorY;
        stuckReturnPos = null;
        stuckReturnAnchorY = Double.NaN;
    }

    private void tickLosGoalStall() {
        if (losGoal == null) {
            lastLosGoalRef = null;
            lastLosGoalDist2 = Double.NaN;
            losGoalStallTicks = 0;
            return;
        }

        if (lastLosGoalRef == null || lastLosGoalRef.distanceToSqr(losGoal) > 0.25D) {
            lastLosGoalRef = losGoal;
            lastLosGoalDist2 = boss.position().distanceToSqr(losGoal);
            losGoalStallTicks = 0;
            return;
        }

        double distance = boss.position().distanceToSqr(losGoal);
        if (!Double.isNaN(lastLosGoalDist2)) {
            if (distance > lastLosGoalDist2 - LOS_GOAL_PROGRESS_EPS_SQR) {
                losGoalStallTicks++;
            } else {
                losGoalStallTicks = 0;
            }
        }
        lastLosGoalDist2 = distance;
        lastLosGoalRef = losGoal;
    }

    private Vec3 chooseBestAimPointCheap(LivingEntity target, ClipBudget budget) {
        Vec3 from = boss.getEyePosition();
        Vec3[] aims = getAimPoints(target);

        Vec3 best = aims[0];
        double bestSeen = -1;

        for (Vec3 aim : aims) {
            HitResult hr = clipBudgeted(from, aim, budget);
            if (hr == null) break;
            double total = from.distanceTo(aim);
            double seen = (hr.getType() == HitResult.Type.MISS) ? total : from.distanceTo(hr.getLocation());
            double ratio = (total < 1e-4) ? 1.0 : (seen / total);
            if (ratio > bestSeen) {
                bestSeen = ratio;
                best = aim;
            }
            if (bestSeen > 0.999) break;
        }
        return best;
    }

    private void updateLosGoalBudgeted(LivingEntity target, ClipBudget budget) {
        if (target == null || !target.isAlive()) return;

        if (planner.lockTicks > 0) {
            planner.lockTicks--;
            return;
        }

        if (goalRecalculation > 0) {
            goalRecalculation--;
        } else {
            goalRecalculation = LOS_RECALL_INTERVAL;

            planner.bestPos = null;
            planner.bestScore = -1e18;

            Vec3 from = boss.getEyePosition();
            Vec3 aim = chooseBestAimPointCheap(target, budget);
            HitResult hr = clipBudgeted(from, aim, budget);
            if (hr instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                planner.lastPrimaryBlocker = bhr;
                seedFromBlockerLight(bhr); // peek seed
            }

            seedFromRadialLOD(target);
            seedFromRadarBudgeted(8); // 每次最多处理 8 个 hit
        }

        int evalMax = Math.min(28, Math.max(8, budget.left / 2));
        for (int i = 0; i < evalMax && !planner.queue.isEmpty(); i++) {
            Vec3 first = planner.queue.removeFirst();

            if (isCandidateBlockedByAabb(first)) continue;

            double vis = visibilityRatioFromBudgeted(first, target.getEyePosition(), budget);
            if (vis < (isBreachMode() ? VIS_GATE_BREACH : VIS_GATE_NORMAL)) continue;

            double travel = 0;
            if (vis >= 0.65) {
                travel = aabbTravelFraction(first);
                if (travel < 0.32) continue;
            }

            Vec3 bossPos = boss.position();
            double moveCost = first.distanceToSqr(bossPos) * 0.05;
            double yCost = Math.abs(first.y - bossPos.y) * 1.5;
            double score = (vis * 130.0) + (travel * 25.0) - moveCost - yCost;

            if (vis > 0.999) score += 220.0;

            if (score > planner.bestScore) {
                planner.bestScore = score;
                planner.bestPos = first;
            }

            if (vis > 0.999 && travel > 0.85) break;
        }

        if (planner.bestPos != null) {
            losGoal = planner.bestPos;
            planner.lockTicks = 12;
        }
    }

    private void radarClear() {
        radarHits.clear();
        radarHitSeen.clear();
        radarTtl = 0;
        radarCursor = 0;
    }

    private Vec3[] buildRadarDirs() {
        ArrayList<Vec3> list = new ArrayList<>(RADAR_YAW_SAMPLES * RADAR_PITCH_SAMPLES.length);
        for (double pitch : RADAR_PITCH_SAMPLES) {
            double cp = Math.cos(pitch);
            double sp = Math.sin(pitch);
            for (int i = 0; i < RADAR_YAW_SAMPLES; i++) {
                double yaw = (i / (double) RADAR_YAW_SAMPLES) * (Math.PI * 2.0);
                double x = -Math.sin(yaw) * cp;
                double z =  Math.cos(yaw) * cp;
                list.add(new Vec3(x, sp, z));
            }
        }
        return list.toArray(new Vec3[0]);
    }

    private static Vec3 dirFromYawPitch(double yaw, double pitch) {
        double cp = Math.cos(pitch);
        double sp = Math.sin(pitch);
        double x = -Math.sin(yaw) * cp;
        double z =  Math.cos(yaw) * cp;
        return new Vec3(x, sp, z);
    }

    private void radarAddHit(BlockHitResult bhr) {
        BlockPos blockPos = bhr.getBlockPos();
        Direction face = bhr.getDirection();
        long key = radarKey(blockPos, face);

        if (!radarHitSeen.add(key)) return;

        radarHits.addLast(new RadarHit(key, blockPos, face, bhr.getLocation()));

        while (radarHits.size() > RADAR_MAX_HITS) {
            RadarHit old = radarHits.removeFirst();
            radarHitSeen.remove(old.key());
        }
    }

    private Vec3[] getAimPoints(LivingEntity t) {
        Vec3 base = t.position();
        double h = Math.max(0.01, t.getBbHeight());
        return new Vec3[] {t.getEyePosition(), base.add(0.0, h * 0.70, 0.0), base.add(0.0, h * 0.45, 0.0), base.add(0.0, h * 0.20, 0.0)};
    }

    private void seedFromBlockerLight(BlockHitResult bhr) {
        Vec3 facePoint = bhr.getLocation();
        Direction face = bhr.getDirection();

        final double[] PUSH = new double[] { 0.75 };
        final double[] SIDE = new double[] { -0.70, 0.0, 0.70 };
        final double[] UP = new double[] { 0.0, 0.90 };

        Vec3 n = new Vec3(face.getStepX(), face.getStepY(), face.getStepZ());
        double eyeH = boss.getEyeHeight();

        Vec3 lateral = (face.getAxis() == Direction.Axis.X) ? new Vec3(0,0,1) : new Vec3(1,0,0);
        Vec3 vertical = new Vec3(0,1,0);

        for (double push : PUSH) {
            Vec3 baseEye = facePoint.add(n.scale(push));
            for (double s : SIDE) {
                for (double u : UP) {
                    Vec3 eyePos = baseEye.add(lateral.scale(s)).add(vertical.scale(u));
                    Vec3 feet = new Vec3(eyePos.x, eyePos.y - eyeH, eyePos.z);
                    plannerEnqueue(feet);
                }
            }
        }
    }

    private void seedFromRadialLOD(LivingEntity target) {
        int ang = (noLosTicks < 35) ? 8 : (noLosTicks < 70 ? 12 : 16);

        double[] radii = (isBreachMode() ? LOS_RADII_CLOSE : new double[]{10D, 14D, 18D});
        double baseY = target.getEyeY() + HOVER_HEIGHT_OFFSET;
        double[] hs = (noLosTicks < 60) ? new double[]{0, 4, 8} : new double[]{0, 2, 4, 6, 10};

        for (double r0 : radii) {
            double r = Mth.clamp(r0, HOVER_MIN_DIST, HOVER_MAX_DIST);
            for (int i = 0; i < ang; i++) {
                double a = (i / (double)ang) * (Math.PI * 2.0);
                double cx = target.getX() + Math.cos(a) * r;
                double cz = target.getZ() + Math.sin(a) * r;
                for (double h : hs) {
                    double cy = clampCombatY(baseY + h);
                    plannerEnqueue(new Vec3(cx, cy, cz));
                }
            }
        }
    }

    private void seedFromRadarBudgeted(int maxHitsThisSeed) {
        if (!RADAR_ENABLED) return;
        if (radarHits.isEmpty()) return;

        int n = 0;
        for (RadarHit rh : radarHits) {
            if (n++ < planner.seedCursor) continue;
            BlockHitResult bhr = new BlockHitResult(rh.hitLoc(), rh.face(), rh.pos(), true);
            seedFromBlockerLight(bhr);

            if (n - planner.seedCursor >= maxHitsThisSeed) break;
        }
        planner.seedCursor += maxHitsThisSeed;
        if (planner.seedCursor > radarHits.size()) planner.seedCursor = 0;
    }

    private void tryFastShoot(LivingEntity target) {
        if (boss.level().isClientSide) return;
        if (target == null || !target.isAlive()) return;

        // 必须正在使用弓
        if (!boss.isUsingItem()) return;
        if (!(boss.getUseItem().getItem() instanceof BowItem)) return;

        int count = boss.tickCount;
        if (count == lastFastShotTick) return;

        // 拉弓未到阈值-不放
        if (boss.getTicksUsingItem() < FAST_DRAW_TICKS) return;

        if (!canShootTarget(target)) {
            boss.stopUsingItem();
            return;
        }

        boss.stopUsingItem();
        boss.performRangedAttack(target, 1); // 满蓄力=1
        lastFastShotTick = count;
        bowHoldTicks = KEEP_BOW_TICKS;
    }

    private double aabbTravelFraction(Vec3 toPos) {
        Level level = boss.level();
        Vec3 from = boss.position();
        Vec3 delta = toPos.subtract(from);
        double len2 = delta.lengthSqr();
        if (len2 < 1e-6) return 1.0;

        int steps = Mth.clamp((int)(Math.sqrt(len2) / 3.0), 3, 8);
        AABB bb = boss.getBoundingBox();

        for (int i = 1; i <= steps; i++) {
            double f = i / (double) steps;
            Vec3 step = delta.scale(f);
            if (!level.noCollision(boss, bb.move(step))) {
                return (i - 1) / (double) steps;
            }
        }
        return 1.0;
    }

    private double visibilityRatioFromBudgeted(Vec3 fromFeet, Vec3 targetEye, ClipBudget budget) {
        Vec3 fromEye = fromFeet.add(0.0D, boss.getEyeHeight(), 0.0D);
        HitResult hr = clipBudgeted(fromEye, targetEye, budget);
        if (hr == null) return 0.0;
        double total = fromEye.distanceTo(targetEye);
        if (total < 1e-4) return 1.0;
        double seen = (hr.getType() == HitResult.Type.MISS) ? total : fromEye.distanceTo(hr.getLocation());
        return Mth.clamp(seen / total, 0.0, 1.0);
    }

    private double clampCombatY(double y) {
        Level level = boss.level();
        double floor = combatAnchorY - COMBAT_MAX_DOWN;
        double ceil  = combatAnchorY + COMBAT_MAX_UP;

        floor = Math.max(floor, level.getMinBuildHeight() + 2);
        ceil  = Math.min(ceil,  level.getMaxBuildHeight() - 4);

        return Mth.clamp(y, floor, ceil);
    }

    private boolean intersectsTargetAt(Vec3 pos, LivingEntity target) {
        if (target == null) return false;
        // Approx check: move boss AABB to candidate position and test against target AABB
        AABB moved = boss.getBoundingBox().move(pos.subtract(boss.position()));
        return moved.intersects(target.getBoundingBox());
    }

    private boolean isTeleportCellEmpty(Vec3 pos) {
        Level level = boss.level();
        BlockPos bp = BlockPos.containing(pos);
        if (!level.hasChunkAt(bp)) return false;
        // "No block here" = no collision shape (air, water, plants...). If you want STRICT air-only, use state.isAir().
        return level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
    }

    private boolean sweptNoCollision(Vec3 delta, double lookaheadTicks) {
        Level level = boss.level();
        AABB boundingBox = boss.getBoundingBox();

        Vec3 scale = delta.scale(lookaheadTicks);
        for (int i = 1; i <= AVOID_SWEEP_STEPS; i++) {
            double f = i / (double) AVOID_SWEEP_STEPS;
            Vec3 step = scale.scale(f);
            if (!level.noCollision(boss, boundingBox.move(step))) return false;
        }
        return true;
    }

    private boolean isCandidateBlockedByAabb(Vec3 pos) {
        Level level = boss.level();
        BlockPos bp = BlockPos.containing(pos);
        if (!level.hasChunkAt(bp)) return true;

        AABB movedBB = boss.getBoundingBox().move(pos.subtract(boss.position()));
        return !level.noCollision(boss, movedBB);
    }

    private boolean isBreachMode() {
        return noLosTicks >= (NO_LOS_REPOSITION_AFTER + BREACH_AFTER_TICKS);
    }

    private boolean canShootTarget(LivingEntity target) {
        return canShootTarget(target, auxBudgetThisTick());
    }

    private boolean canShootTarget(LivingEntity target, ClipBudget budget) {
        if (target == null || !target.isAlive()) return false;
        if (boss.distanceToSqr(target) > (HOVER_MAX2 * 1.10)) return false;

        int count = boss.tickCount;
        int targetId = target.getId();

        Vec3 from = boss.getEyePosition();
        Vec3 pointCheap = chooseBestAimPointCheap(target, budget);
        HitResult hitResult = clipBudgeted(from, pointCheap, budget);

        if (hitResult == null) {
            if (targetId == lastCanShootTargetIdCache && (count == lastCanShootTick || count == lastCanShootTick + 1)) {
                return lastCanShootValue;
            }
            return false;
        }

        boolean miss = (hitResult.getType() == HitResult.Type.MISS);
        lastCanShootTick = count;
        lastCanShootTargetIdCache = targetId;
        lastCanShootValue = miss;
        return miss;
    }

    private boolean shouldStuckTeleport(LivingEntity target) {
        if (!STUCK_TELEPORT_ENABLED) return false;
        if (stuckTeleporting) return false;
        if (stuckCooldown > 0) return false;
        if (target == null || !target.isAlive()) return false;

        if (noLosTicks < STUCK_TELEPORT_TRIGGER_NOLOS) return false;

        boolean stalledGoal = (losGoal != null && losGoalStallTicks >= LOS_GOAL_STALL_TRIGGER);
        boolean plannerNoSolution = (losGoal == null && planner.bestPos == null);

        boolean hardTimeout = (noLosTicks >= STUCK_TELEPORT_HARD_TIMEOUT);
        return stalledGoal || plannerNoSolution || hardTimeout;
    }

    private boolean isTeleportPosSafe(Vec3 pos) {
        Level level = boss.level();
        BlockPos bp = BlockPos.containing(pos);
        if (!level.hasChunkAt(bp)) return false;

        AABB moved = boss.getBoundingBox().move(pos.subtract(boss.position()));
        return level.noCollision(boss, moved);
    }

    private boolean isTeleportPosSafe(Vec3 pos, LivingEntity targetToAvoidOverlap) {
        if (!isTeleportPosSafe(pos)) return false;
        return targetToAvoidOverlap == null || !tooCloseHoriz(pos, targetToAvoidOverlap);
    }

    private boolean tooCloseHoriz(Vec3 pos, LivingEntity target) {
        double dx = pos.x - target.getX();
        double dz = pos.z - target.getZ();
        return (dx * dx + dz * dz) < minHorizSepSqr(target);
    }

    private boolean canShootFromFeetNoBudget(Vec3 fromFeet, LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        Vec3 fromEye = fromFeet.add(0.0D, boss.getEyeHeight(), 0.0D);

        Vec3[] aims = getAimPoints(target);
        Level level = boss.level();

        for (Vec3 aim : aims) {
            HitResult hr = level.clip(new ClipContext(fromEye, aim, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
            if (hr.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    private double minHorizSepSqr(LivingEntity target) {
        double min = (boss.getBbWidth() * 0.5D) + (target.getBbWidth() * 0.5D) + 0.35D;
        return min * min;
    }

    private void serverHoverIdle() {
        Vec3 v = boss.getDeltaMovement().scale(0.9D);
        if (v.length() < 0.01D) v = Vec3.ZERO;
        boss.setDeltaMovement(v);
        boss.hasImpulse = true;
    }

    private void equipBowIfNeeded() {
        if (boss.level().isClientSide) return;
        if (!(boss.getMainHandItem().getItem() instanceof BowItem)) {
            boss.setItemInHand(InteractionHand.MAIN_HAND, BOW_TEMPLATE.copy());
            boss.setDropChance(EquipmentSlot.MAINHAND, 0);
        }
    }

    private void stowBowIfIdle() {
        if (boss.level().isClientSide) return;
        if (!boss.isUsingItem() && this.bowHoldTicks <= 0) {
            boss.setItemInHand(InteractionHand.MAIN_HAND, IDLE_HAND_ITEM.copy());
        }
    }

    public void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("AerialCombatEnabled", boss.isAerialCombatEnabled());
    }

    public void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("AerialCombatEnabled")) {
            setAerialCombatEnabled(tag.getBoolean("AerialCombatEnabled"));
        } else {
            setAerialCombatEnabled(DEFAULT_AERIAL_ENABLED);
        }
    }

    // Budget
    private static final class ClipBudget {
        final int soft;
        final int hard;
        final int burstCap;
        int burst;
        int left;
        int used;

        ClipBudget(int soft, int hard) {
            this.soft = soft;
            this.hard = hard;
            this.burstCap = Math.max(0, hard - soft);
            this.burst = this.burstCap;
        }

        void beginTick() {
            this.used = 0;
            int allow = soft + burst;
            this.left = Math.min(hard, allow);
        }

        boolean tryUse(int n) {
            if (left >= n) { left -= n; used += n; return true; }
            return false;
        }

        void endTick() {
            int delta = soft - used;
            burst = Mth.clamp(burst + delta, 0, burstCap);
        }
    }

    // Planner state
    private static final class LosPlanner {
        final ArrayDeque<Vec3> queue = new ArrayDeque<>(500);
        Vec3 bestPos = null;
        double bestScore = -1e18;
        int lockTicks = 0;
        int seedCursor = 0;
        BlockHitResult lastPrimaryBlocker = null;

        void reset() {
            queue.clear();
            bestPos = null;
            bestScore = -1e18;
            lockTicks = 0;
            seedCursor = 0;
            lastPrimaryBlocker = null;
        }
    }

    private final LosPlanner planner = new LosPlanner();

    private void plannerEnqueue(Vec3 p) {
        if (planner.queue.size() >= 500) return;
        planner.queue.addLast(p);
    }

    private HitResult clipBudgeted(Vec3 from, Vec3 to, ClipBudget budget) {
        if (!budget.tryUse(1)) return null;
        return boss.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, boss));
    }
}
