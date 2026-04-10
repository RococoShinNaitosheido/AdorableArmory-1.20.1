package flu.kitten.adorablearmory.entity.boss;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.entity.boss.abilitymanager.AlysiaFlightSystem;
import flu.kitten.adorablearmory.entity.boss.action.AlysiaActionController;
import flu.kitten.adorablearmory.entity.boss.action.TeleportationGrabAction;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonDamageSource;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonTypes;
import flu.kitten.adorablearmory.entity.weapons.TrueDemonArrow;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.*;

public class ScarletLoraAlysia extends AlysiaCustomHealthMonster implements PowerableMob, RangedAttackMob {

    @OnlyIn(Dist.CLIENT)
    public record GhostTrail(Vec3 pos, float yRot, float xRot, long creationTick) {}
    @OnlyIn(Dist.CLIENT)
    public final List<GhostTrail> ghostTrails = new LinkedList<>();
    @OnlyIn(Dist.CLIENT)
    private Vec3 prevClientPos = null;
    @OnlyIn(Dist.CLIENT)
    private float prevClientYRot = 0.0f, prevClientXRot = 0.0f;
    @OnlyIn(Dist.CLIENT)
    private long lastTrailTick = -1L;
    @OnlyIn(Dist.CLIENT)
    private static final int GHOST_TRAIL_MAX_AGE_TICKS = 12; // 残影持续tick
    @OnlyIn(Dist.CLIENT)
    private static final int GHOST_TRAIL_INTERVAL_TICKS = 3; // 每tick创建一个残影
    static final Set<MobEffect> ALL_EFFECTS = Set.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SLOWDOWN, MobEffects.HARM, MobEffects.CONFUSION, MobEffects.BLINDNESS, MobEffects.HUNGER, MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER, MobEffects.LEVITATION, MobEffects.UNLUCK, MobEffects.DARKNESS);
    private final ServerBossEvent bossEvent = new ServerBossEvent(this.getDisplayName(), BossEvent.BossBarColor.PINK, BossEvent.BossBarOverlay.PROGRESS);
    private static final EntityDataAccessor<Integer> CURRENT_PHASE = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_INVULNERABLE = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> PHASE_TIMER = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_TELEPORTING = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.BOOLEAN);
    private static final float MAX_HEALTH = 300; // MAX HP TEST
    private static final float STAGE_1_THRESHOLD = 0.75F; // 75% health
    private static final float STAGE_2_THRESHOLD = 0.50F; // 50% health
    private static final float STAGE_3_THRESHOLD = 0.25F; // 25% health
    private static final int MIN_TELEPORT_INTERVAL = 40; // 2 seconds
    private static final int MAX_TELEPORT_INTERVAL = 200; // 10 seconds
    private Player pendingStrikeTarget = null;
    private int strikeDelayTicks = 0;
    private static final int STRIKE_DELAY_DURATION = 18; // tick
    private boolean hasTriggeredStage1 = false;
    private boolean hasTriggeredStage2 = false;
    private boolean hasTriggeredStage3 = false;
    private int lastDamageTick = 0;
    private int nextTeleportTick = 0;
    public boolean isTeleportOnCool = false;
    public int teleportCoolTick = 0;
    private int lastRegenerationTick = 0;
    private int InvincibleCount = 0;
    private int requiredHitsForInvulnerability;
    private Player teleportGrabbedPlayer = null;
    public boolean isPerformingTeleportGrab = false;
    private int teleportGrabDuration = 0;
    private Vec3 playerLockPosition = null;
    private static final int TELEPORT_GRAB_DURATION = 40; // 2 seconds total grab duration
    private static final EntityDataAccessor<Boolean> IS_TELEPORT_GRABBING = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_CHARGING_BOW = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.BOOLEAN);
    private static final double PRE_STRIKE_MIN_DIST = 4.24;
    private static final double PRE_STRIKE_MAX_DIST = 4.24;
    @Nullable private UUID preTeleportTargetId = null;
    private boolean isChargeCool = false;
    private boolean isCharging = false;
    private int chargeCool = 0;
    private static final int CHARGE_COOL_TICKS = 80; // 冲刺冷却
    private static final EntityDataAccessor<Integer> ACTION_ID = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ACTION_TICK = SynchedEntityData.defineId(ScarletLoraAlysia.class, EntityDataSerializers.INT);
    private final AlysiaActionController actionController = new AlysiaActionController(this, ACTION_ID, ACTION_TICK);
    public final AnimationState teleportGrabAnimation = new AnimationState();
    private AlysiaFlightSystem flightSystem;

    public static double getMinDistance() {
        return PRE_STRIKE_MIN_DIST;
    }

    public static double getMaxDistance() {
        return PRE_STRIKE_MAX_DIST;
    }

    public boolean isCharging() {
        return isCharging;
    }

    public boolean isInChargeCool() {
        return isChargeCool;
    }

    public ScarletLoraAlysia(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.scheduleNextTeleport();
        this.setPersistenceRequired();
        this.requiredHitsForInvulnerability = 5 + this.random.nextInt(6); // 5-11 hits
    }

    @Override
    public float getCustomMaxHealth() {
        return MAX_HEALTH;
    }

    @Override
    public float getMaxDamagePerHit() {
        return 5;
    }

    @Override
    public int getCustomInvulnerableTime() {
        return 25;
    }

    private AlysiaFlightSystem flight() {
        if (this.flightSystem == null) this.flightSystem = new AlysiaFlightSystem(this);
        return this.flightSystem;
    }

    public void setMoveAndNavigation(MoveControl moveControl, PathNavigation navigation) {
        this.moveControl = moveControl;
        this.navigation = navigation;
    }

    public void setAerialEnabledData(boolean enabled) {
        this.entityData.set(AlysiaFlightSystem.AERIAL_COMBAT_ENABLED, enabled);
    }

    public boolean getChargingBowFlag() {
        return this.entityData.get(IS_CHARGING_BOW);
    }

    public void setChargingBowFlag(boolean v) {
        this.entityData.set(IS_CHARGING_BOW, v);
    }

    public void setAerialCombatEnabled(boolean enabled) {
        flight().setAerialCombatEnabled(enabled);
    }

    static {
        Field health = ObfuscationReflectionHelper.findField(RangedAttribute.class, "f_22308_"); // maxValue f_22308_
        health.setAccessible(true);
        try {
            health.setDouble(Attributes.MAX_HEALTH, 10000.0d);
        } catch (IllegalAccessException e) {
            AdorableArmory.LOGGER.error("The MAX_HEALTH limit cannot be increased", e);
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.isDeadOrDying()) {
            if (!this.level().isClientSide && this.bossEvent != null) {
                this.bossEvent.setProgress(0.0f);
            }
            return;
        }

        this.actionController.tick(); // 自定义动作驱动

        if (level().isClientSide) {
            clientTick();
        }

        if (chargeCool > 0) {
            chargeCool--;
            if (chargeCool == 0) {
                isChargeCool = false;
            }
        }

        if (this.level().isClientSide()) return;

        flight().serverTick();

        if (this.isTeleporting() && !this.level().isClientSide()) {
            final ServerLevel serverLevel = (ServerLevel) this.level();
            final AABB boundingBox = this.getBoundingBox();
            final RandomSource rand = this.getRandom();

            final int count = 16; // 每tick颗
            for (int i = 0; i < count; i++) {
                final double px = Mth.lerp(rand.nextDouble(), boundingBox.minX, boundingBox.maxX);
                final double py = Mth.lerp(rand.nextDouble(), boundingBox.minY, boundingBox.maxY);
                final double pz = Mth.lerp(rand.nextDouble(), boundingBox.minZ, boundingBox.maxZ);
                serverLevel.sendParticles(AdorableArmoryRegister.TRUE_DEMON_PARTICLE.get(), px, py, pz, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    private void clientTick() {
        final Vec3 currPos = this.position();
        final long gameTime = this.level().getGameTime();
        final float currY = this.getYRot();
        final float currX = this.getXRot();

        if (prevClientPos == null) {
            prevClientPos = currPos;
            prevClientYRot = currY;
            prevClientXRot = currX;
            lastTrailTick = gameTime;
        }

        if (this.isTeleporting() && (gameTime - lastTrailTick) >= GHOST_TRAIL_INTERVAL_TICKS) {
            final double d2 = prevClientPos.distanceToSqr(currPos);
            final double jumpThresh2 = 0.64D;

            if (d2 > jumpThresh2) {
                final int N = 2;
                for (int i = 1; i <= N; i++) {
                    final double t = i / (double) (N + 1);
                    final Vec3 p = prevClientPos.lerp(currPos, t);
                    final float y = Mth.rotLerp((float) t, prevClientYRot, currY);
                    final float x = Mth.lerp((float) t, prevClientXRot, currX);

                    final long creation = gameTime - (N + 1 - i);
                    ghostTrails.add(new GhostTrail(p, y, x, creation));
                }
            }

            ghostTrails.add(new GhostTrail(currPos, currY, currX, gameTime));

            prevClientPos = currPos;
            prevClientYRot = currY;
            prevClientXRot = currX;
            lastTrailTick = gameTime;
        }

        if (!ghostTrails.isEmpty()) {
            ghostTrails.removeIf(trail -> gameTime - trail.creationTick() > GHOST_TRAIL_MAX_AGE_TICKS);
        }
    }

    // 飞行导航
    @Override
    protected @NotNull PathNavigation createNavigation(@NotNull Level level) {
        return flight().createNavigation(level);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CURRENT_PHASE, 0); // 0 = stage1 / 1 = stage2 / 2 = stage3
        this.entityData.define(IS_INVULNERABLE, false);
        this.entityData.define(PHASE_TIMER, 0);
        this.entityData.define(IS_TELEPORTING, false);
        this.entityData.define(IS_TELEPORT_GRABBING, false);
        this.entityData.define(IS_CHARGING_BOW, false);
        AlysiaFlightSystem.defineSynchedData(this); // AlysiaFlightSystem Accessor
        this.entityData.define(ACTION_ID, 0);
        this.entityData.define(ACTION_TICK, 0);
    }

    public boolean isValidTarget(LivingEntity entity) {
        if (entity == null || entity == this) return false;
        if (!entity.isAlive()) return false;
        if (entity.isAlliedTo(this)) return false;
        return !(entity instanceof Player player) || (!player.isCreative() && !player.isSpectator());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new TeleportPreStrikeGoal(this)); // 大招

        this.goalSelector.addGoal(2, new ChargeChaseAttackGoal(this, 1.24D));

        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.84D, false) {
            @Override
            public boolean canUse() {
                final LivingEntity target = ScarletLoraAlysia.this.getTarget();
                if (target == null || !target.isAlive()) return false;
                if (ScarletLoraAlysia.this.getCurrentPhase() != 0) return false;

                if (ScarletLoraAlysia.this.isTeleporting()) return false;
                if (ScarletLoraAlysia.this.isCharging()) return false;

                if (ScarletLoraAlysia.this.distanceTo(target) >= ChargeChaseAttackGoal.START_MIN_DIST) {
                    return false;
                }
                return super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                if (ScarletLoraAlysia.this.getCurrentPhase() != 0) return false;
                if (ScarletLoraAlysia.this.isTeleporting()) return false;
                if (ScarletLoraAlysia.this.isCharging()) return false;
                return super.canContinueToUse();
            }

            @Override
            public boolean isInterruptable() {
                return true;
            }
        });

        flight().registerGoals(this.goalSelector); // 飞行目标注册

        NearestAttackableTargetGoal<LivingEntity> anyNearest = new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, false, false, this::isValidTarget);
        anyNearest.setUnseenMemoryTicks(60);
        this.targetSelector.addGoal(5, anyNearest);

        this.goalSelector.addGoal(6, new AlwaysFacePlayerGoal(this));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }

    public boolean isGoalRunning(Class<? extends Goal> goalClass) {
        return this.goalSelector.getRunningGoals().anyMatch(wrappedGoal -> goalClass.isInstance(wrappedGoal.getGoal()));
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, 8)
                .add(Attributes.MOVEMENT_SPEED, 0.26)
                .add(Attributes.FOLLOW_RANGE, 68)
                .add(Attributes.ARMOR, 10)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.25)
                .add(Attributes.FLYING_SPEED, 0.60); // 飞行速度
    }

    @Override
    public boolean doHurtTarget(@NotNull Entity target) {
        if (!(target instanceof LivingEntity livingTarget)) {
            return false;
        }
        float attackDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource damageSource = this.getMeleeDamageSource();
        return livingTarget.hurt(damageSource, attackDamage);
    }

    private DamageSource getMeleeDamageSource() {
        float healthPercent = this.getCustomHealthPercent();
        float randomValue = this.random.nextFloat();

        float magicChance = (1.0F - healthPercent) * 0.3F; // 0% to 30% chance
        float indirectMagicChance = magicChance + (1.0F - healthPercent) * 0.2F; // +20% chance
        float genericChance = indirectMagicChance + (1.0F - healthPercent) * 0.25F; // +25% chance

        if (randomValue < magicChance) {
            return this.damageSources().magic();
        } else if (randomValue < indirectMagicChance) {
            return this.damageSources().indirectMagic(this, this);
        } else if (randomValue < genericChance) {
            return this.damageSources().generic();
        } else {
            return this.damageSources().mobAttack(this);
        }
    }

    public void scheduleNextTeleport() {
        int randomInterval = MIN_TELEPORT_INTERVAL + this.random.nextInt(MAX_TELEPORT_INTERVAL - MIN_TELEPORT_INTERVAL);
        this.nextTeleportTick = this.tickCount + randomInterval;
    }

    private void attemptTeleportAttack() {
        if (this.level().isClientSide || this.isInvulnerable() || this.isTeleportOnCool) return;

        Player targetPlayer = this.findBestTeleportTarget();
        if (targetPlayer == null) {
            this.scheduleNextTeleport();
            return;
        }

        this.setPreTeleportTarget(targetPlayer);
        this.setTeleporting(true);
    }

    public void onTeleportCompleted(boolean executed) {
        this.setTeleporting(false);

        int base = 500;
        if (executed) {
            this.isTeleportOnCool = true;
            this.teleportCoolTick = this.tickCount + base;
            this.nextTeleportTick = this.tickCount + base;
        } else {
            this.isTeleportOnCool = true;
            this.teleportCoolTick = this.tickCount + 60;
            this.nextTeleportTick = this.tickCount + 60 + this.getRandom().nextInt(40);
        }
    }

    private Player findBestTeleportTarget() {
        List<Player> nearbyPlayers = this.level().getEntitiesOfClass(Player.class, new AABB(this.blockPosition()).inflate(68.0));
        Player closestPlayer = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : nearbyPlayers) {
            if (player.isCreative() || player.isSpectator() || player.isSleeping() || (player.getHealth() == 0)) continue;
            double distance = this.distanceToSqr(player);
            double maxRange = 64; // Max block range
            if (distance < closestDistance && distance <= maxRange * maxRange) {
                closestDistance = distance;
                closestPlayer = player;
            }
        }
        return closestPlayer;
    }

    public boolean executeTeleportAttack(Player target) {

        // Attempt to pull the player and check if it was successful on the server
        boolean pullSuccessful = this.pullPlayerToBoss(target);

        if (pullSuccessful) {
            this.setTeleportGrabbing(true);
            this.lookAt(target, 30.0F, 30.0F);

            this.teleportGrabbedPlayer = target;
            this.isPerformingTeleportGrab = true;
            this.teleportGrabDuration = TELEPORT_GRAB_DURATION;
            this.strikeDelayTicks = STRIKE_DELAY_DURATION;

            this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.24F, 0.82F);
            target.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.24F, 1.24F);

            this.actionController.start(new TeleportationGrabAction()); // 瞬移抓取动作

            return true;
        }
        return false;
    }

    private boolean pullPlayerToBoss(Player player) {
        if (this.level().isClientSide || !(player instanceof ServerPlayer serverPlayer)) return false;

        // 在Boss面朝方向0.5格处
        Vec3 bossPos = this.position();
        Vec3 bossLook = this.getLookAngle().normalize();
        Vec3 preferred = bossPos.add(bossLook.scale(0.6));
        Vec3 base = this.ensureSafePullPosition(preferred);

        double playerEyeHeight = player.getEyeY() - player.getY();
        double desiredY = this.getEyeY() - playerEyeHeight;
        Vec3 eyeAligned = new Vec3(base.x, desiredY, base.z);

        if (!this.isValidPullPosition(eyeAligned)) {
            eyeAligned = base;
        }

        // 固定的“锁位”写入
        this.playerLockPosition = eyeAligned;

        // 只按XZ计算
        double dx = this.getX() - eyeAligned.x;
        double dz = this.getZ() - eyeAligned.z;
        float yaw = (float)(Mth.atan2(dz, dx) * (180D/Math.PI)) - 90.0F;
        float pitch = 0.0F;

        serverPlayer.connection.teleport(eyeAligned.x, eyeAligned.y, eyeAligned.z, yaw, pitch);
        serverPlayer.setYHeadRot(yaw);
        serverPlayer.setYBodyRot(yaw);

        // 立即强制Boss旋转以面向玩家
        this.lookAt(serverPlayer, 360.0F, 360.0F);
        this.setYBodyRot(this.getYRot());

        // 立即清空速度
        serverPlayer.setDeltaMovement(Vec3.ZERO);
        serverPlayer.hurtMarked = true; // 客户端立即同步位置/朝向

        return !(serverPlayer.position().distanceToSqr(eyeAligned) > 2.0D);
    }

    private Vec3 ensureSafePullPosition(Vec3 preferredPos) {
        if (this.isValidPullPosition(preferredPos)) return preferredPos;
        Vec3 bossPos = this.position();
        Vec3[] alternatives = {
                bossPos.add(1.5, 0, 0),
                bossPos.add(-1.5, 0, 0),
                bossPos.add(0, 0, 1.5),
                bossPos.add(0, 0, -1.5),
                bossPos.add(1.0, 0, 1.0),
                bossPos.add(-1.0, 0, -1.0)
        };

        for (Vec3 alt : alternatives) {
            Vec3 safeAlt = this.findSafeYPosition(alt);
            if (this.isValidPullPosition(safeAlt)) {
                return safeAlt;
            }
        }
        return bossPos.add(0, 0, 1.0);
    }

    private boolean isValidPullPosition(Vec3 pos) {
        AABB checkBox = new AABB(pos.x - 0.3, pos.y, pos.z - 0.3, pos.x + 0.3, pos.y + 1.8, pos.z + 0.3);
        return this.level().noCollision(checkBox);
    }

    private Vec3 findSafeYPosition(Vec3 pos) {
        BlockPos basePos = BlockPos.containing(pos);
        int top = Math.min(level().getMaxBuildHeight() - 2, (int) pos.y + 5);
        int bottom = Math.max(level().getMinBuildHeight(), (int) pos.y - 10);

        for (int y = top; y >= bottom; --y) {
            BlockPos base = BlockPos.containing(pos.x, y, pos.z);
            BlockState ground = level().getBlockState(base);
            if (!ground.isAir() && ground.isCollisionShapeFullBlock(level(), base)) {
                if (level().isEmptyBlock(base.above()) && level().isEmptyBlock(base.above(2))) {
                    return new Vec3(pos.x, y + 1.0, pos.z);
                }
            }
        }

        // clamp within build height with two-block headroom
        int minY = level().getMinBuildHeight() + 1;
        int maxY = level().getMaxBuildHeight() - 3;
        double clampedY = Mth.clamp(pos.y, minY, maxY);
        return new Vec3(basePos.getX() + 0.5, clampedY, basePos.getZ() + 0.5);
    }

    // 随机瞬移
    private boolean teleportDodge() {
        if (this.level().isClientSide()) {
            return false;
        }
        double d0 = this.getX();
        double d1 = this.getY();
        double d2 = this.getZ();

        for(int i = 0; i < 16; ++i) {
            double d3 = this.getX() + (this.random.nextDouble() - 0.5D) * 16.0D;
            double d4 = Mth.clamp(this.getY() + (double)(this.random.nextInt(16) - 8), this.level().getMinBuildHeight(), this.level().getMinBuildHeight() + ((ServerLevel)this.level()).getLogicalHeight() - 1);
            double d5 = this.getZ() + (this.random.nextDouble() - 0.5D) * 16.0D;
            if (this.isPassenger()) {
                this.stopRiding();
            }

            if (this.randomTeleport(d3, d4, d5, false)) {
                this.level().playSound(null, d0, d1, d2, SoundEvents.ENDERMAN_TELEPORT, this.getSoundSource(), 1.2F, 1.2F);
                this.playSound(SoundEvents.ENDERMAN_TELEPORT, 1.2F, 1.2F);
                return true;
            }
        }
        return false;
    }

    private void performTeleportAttack(Player target) {
        if (this.level().isClientSide) return;
        if (target != this.teleportGrabbedPlayer) return;

        final double dx = target.getX() - this.getX();
        final double dz = target.getZ() - this.getZ();
        final double horizDist = Math.sqrt(dx * dx + dz * dz);

        final double edgeHorizDist = horizDist - (this.getBbWidth() * 0.5) - (target.getBbWidth() * 0.5);

        final double metric = Math.max(0.0, edgeHorizDist);
        // final double metric = horizDist; // 中心距

        if (metric > 1.056) {
            this.releaseTeleportGrab();
            return;
        }

        boolean canTryFatal = this.random.nextFloat() < 0.80;

        boolean attack;
        if (canTryFatal) {
            //attack = target.hurt(TrueDemonDamageSource.causeTrueDemonDamage(target.level(), this), Integer.MAX_VALUE);
            attack = TrueDemonTypes.TrueDemonDamageUtil.trueDemonMechanismKill(target, this);
        } else {
            attack = target.hurt(this.damageSources().mobAttack(this), 8.5f);
        }

        if (!attack) {
            this.releaseTeleportGrab();
            return;
        }

        float healthPercent = this.getCustomHealthPercent();
        float healthScaledKnock = 2.5F * (2.0F - healthPercent); // 2.5 to 5.0 range
        float finalKnock = Math.min(healthScaledKnock, 18.0F);

        Vec3 direction = target.position().subtract(this.position()).normalize();
        target.knockback(finalKnock, -direction.x, -direction.z);

        double distance = Math.sqrt(this.distanceToSqr(target));
        double yBoost = 0.56D + Math.min(0.186D, distance * 0.0246D); // 随距离轻微增强-可将0.28D增至0.34D-把0.12D放宽至0.18D
        Vec3 vel = target.getDeltaMovement();
        target.setDeltaMovement(vel.x, vel.y + yBoost, vel.z);
        target.hurtMarked = true;

        if (this.random.nextFloat() < 0.32F) {
            FoodData foodData = target.getFoodData();
            int hungerReduction = 2 + this.random.nextInt(4);
            int newFoodLevel = Math.max(0, foodData.getFoodLevel() - hungerReduction);
            foodData.setFoodLevel(newFoodLevel);
        }

        this.playSound(SoundEvents.PLAYER_HURT, 1.56F, 0.6F);
        target.playSound(SoundEvents.GENERIC_HURT, 1.56F, 0.6F);

        String message = canTryFatal ? "§4" + target.getName().getString() + "遭受一次致命的瞬移攻击(测试)" : "§c" + target.getName().getString() + "遭受一次瞬移攻击(测试)";
        target.sendSystemMessage(Component.literal(message));

        this.releaseTeleportGrab();
    }

    private void releaseTeleportGrab() {
        if (this.teleportGrabbedPlayer != null) {
            Player player = this.teleportGrabbedPlayer;
            player.sendSystemMessage(Component.literal("§a" + player.getName().getString() + "挣脱了§l" + this.getName().getString() + "(测试)")); // Bold
            player.playSound(SoundEvents.ITEM_BREAK, 1.1F, 1.56F);
        }

        this.teleportGrabbedPlayer = null;
        this.isPerformingTeleportGrab = false;
        this.teleportGrabDuration = 0;
        this.setTeleportGrabbing(false);
        this.setTeleporting(false);
        this.playerLockPosition = null;
        this.actionController.stop();
    }

    @Override
    public boolean hurt(@NotNull DamageSource damageSource, float damage) {
        if (this.isInvulnerable()) return false;
        if (damageSource.getDirectEntity() instanceof TrueDemonArrow arrow && arrow.getOwner() == this) return false;
        if (damageSource.is(DamageTypes.THORNS)) return false;

        Entity entity = damageSource.getEntity();
        if (entity instanceof LivingEntity living) {
            ItemStack main = living.getMainHandItem();
            if (!main.isEmpty() && main.isEnchanted()) {
                float bonus = EnchantmentHelper.getDamageBonus(main, this.getMobType());
                if (bonus > 0.0F) {
                    damage = Math.max(0.0F, damage - bonus);
                }
                if (EnchantmentHelper.getItemEnchantmentLevel(Enchantments.FIRE_ASPECT, main) > 0) {
                    this.clearFire();
                }
            }
        }

        if (damageSource.getDirectEntity() instanceof AbstractArrow arrow) {
            if (arrow.isOnFire()) this.clearFire();
        }

        /*
        if (this.getCurrentPhase() == 1) {
            if (this.random.nextFloat() < 0.80) {
                if (this.teleportDodge()) return false;
            }
        }
        */

        if (this.getCurrentPhase() == 0 && damageSource.getDirectEntity() instanceof Player) {
            // 50%躲避近战攻击 在传送抓取过程中禁用该50%躲避机制-防止玩家在被抓取时触发boss的teleportDodge
            /*if (!this.isPerformingTeleportGrab && this.teleportGrabbedPlayer == null) {
                if (this.random.nextFloat() < 0.32) {
                    if(this.teleportDodge()) return false;
                }
            }*/
        }

        // ScarletLoraAlysia 免疫的伤害类型
        if (isAnyDamageType(damageSource, DamageTypes.FALL, DamageTypes.IN_FIRE, DamageTypes.EXPLOSION, DamageTypes.FREEZE, DamageTypes.HOT_FLOOR, DamageTypes.IN_WALL, DamageTypes.LAVA, DamageTypes.LIGHTNING_BOLT, DamageTypes.ON_FIRE, TrueDemonDamageSource.TRUE_DEMON_TYPE)) return false;

        float currentHealth = this.getCustomHealth();
        float currentPercent = this.getCustomHealthPercent();

        // 阶段 0/1/2 锁血 不会被直接秒掉 至少留 1 HP
        if (this.getCurrentPhase() < 3 && currentPercent > 0.01F && damage >= currentHealth) {
            damage = Math.max(0.0F, currentHealth - 1.0F);
        }

        boolean wasHurt = super.hurt(damageSource, damage);
        if (!wasHurt) {
            return false;
        }

        // applyCustomDamage 内部已经会在 customHealth <= 0 时切入 die()
        if (this.isCustomDead()) {
            if (this.bossEvent != null) {
                this.bossEvent.setProgress(0.0F);
            }
            return true;
        }

        float newPercent = this.getCustomHealthPercent();
        boolean transition = this.shouldTriggerStageTransition(newPercent);

        this.lastDamageTick = this.tickCount;

        if (transition) {
            int nextStage = this.getCurrentPhase() + 1;
            float threshold = switch (nextStage) {
                case 1 -> STAGE_1_THRESHOLD;
                case 2 -> STAGE_2_THRESHOLD;
                case 3 -> STAGE_3_THRESHOLD;
                default -> 0.0F;
            };

            float targetHP = this.getCustomMaxHealth() * threshold;
            if (this.getCustomHealth() < targetHP) {
                this.setCustomHealth(targetHP);
            }

            this.triggerStageTransition();
        }

        if (this.getCurrentPhase() == 1) {
            this.checkConsecutiveHitProtection(damageSource);
        }

        return true;
    }

    @Override
    @SuppressWarnings("all")
    public SpawnGroupData finalizeSpawn(@NotNull ServerLevelAccessor level, @NotNull DifficultyInstance diff, @NotNull MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        var data = super.finalizeSpawn(level, diff, reason, spawnData, dataTag);
        this.setCustomHealth(this.getCustomMaxHealth());
        this.setHealth(this.getMaxHealth());
        this.setInvulnerableUntilTick(0);
        flight().onFinalizeSpawn();
        return data;
    }

    @Override
    public void performRangedAttack(@NotNull LivingEntity target, float pullProgress) {
        double projSpeed = 2.9 + 0.8 * pullProgress;

        Vec3 shooterEye = this.getEyePosition();
        Vec3 look = this.getLookAngle();
        Vec3 aimAt = predictLead(target, shooterEye, projSpeed);
        Vec3 dir = aimAt.subtract(shooterEye).normalize();

        TrueDemonArrow arrow = this.createTrueDemonArrow(target, pullProgress);
        Vec3 pos = this.position().add(0, this.getEyeHeight() - 0.1, 0).add(look.scale(0.5)); // 0.5格

        arrow.setPos(pos.x, pos.y, pos.z);
        arrow.shoot(dir.x, dir.y, dir.z, (float) projSpeed, 0.0F); // inaccuracy = 0

        this.level().addFreshEntity(arrow);
        this.playSound(SoundEvents.SKELETON_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        flight().markBowHold();
    }

    protected TrueDemonArrow createTrueDemonArrow(LivingEntity target, float pullProgress) {
        return TrueDemonArrow.shoot(this.level(), this, target, pullProgress);
    }

    private static Vec3 predictLead(LivingEntity target, Vec3 shooterPos, double projSpeed) {
        Vec3 to = target.getEyePosition().subtract(shooterPos);
        Vec3 v = target.getDeltaMovement();

        double a = v.lengthSqr() - projSpeed * projSpeed;
        double b = 2.0 * to.dot(v);
        double c = to.lengthSqr();

        double t;
        if (Math.abs(a) < 1e-6) {
            t = b != 0.0 ? -c / b : 0.0;
        } else {
            double disc = b*b - 4*a*c;
            if (disc < 0.0) {
                t = 0.0;
            } else {
                double sqrt = Math.sqrt(disc);
                double t1 = (-b - sqrt) / (2*a);
                double t2 = (-b + sqrt) / (2*a);
                t = selectPositiveMin(t1, t2);
            }
        }
        t = Mth.clamp(t, 0.0, 1.5); // 最多预判 1.5s-避免过度提前
        return target.getEyePosition().add(v.scale(t));
    }

    private static double selectPositiveMin(double... values) {
        double best = Double.POSITIVE_INFINITY;
        for (double x : values) if (x > 0.0 && x < best) best = x;
        return best == Double.POSITIVE_INFINITY ? 0.0 : best;
    }

    // 阶段过渡
    private boolean shouldTriggerStageTransition(float healthPercent) {
        int currentStage = this.getCurrentPhase();
        if (!hasTriggeredStage1 && healthPercent <= STAGE_1_THRESHOLD && currentStage == 0) return true;
        if (!hasTriggeredStage2 && healthPercent <= STAGE_2_THRESHOLD && currentStage == 1) return true;
        return !hasTriggeredStage3 && healthPercent <= STAGE_3_THRESHOLD && currentStage == 2;
    }

    // 阶段转换
    private void triggerStageTransition() {
        int currentStage = this.getCurrentPhase();
        int newStage = currentStage + 1;

        this.setInvulnerable(true);
        this.setPhaseTimer(100);

        this.setCurrentPhase(newStage);
        lastRegenerationTick = this.tickCount;

        this.executeStageTransitionEffects(newStage);

        this.InvincibleCount = 0;
        if (newStage == 1) {
            this.requiredHitsForInvulnerability = 5 + this.random.nextInt(6);
        }

        if (newStage >= 1) setAerialCombatEnabled(true);

        if (!this.level().isClientSide) {
            this.broadcastStageChange(newStage);
        }
    }

    // 阶段转换药水效果
    private void executeStageTransitionEffects(int stage) {
        if (this.level().isClientSide) return;

        switch (stage) {
            case 1 -> {
                Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).setBaseValue(10); // 护甲
                Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0.25); // 移动速度
                Objects.requireNonNull(this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(0.25); // 击退抗性
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, false)); // 伤害抗性
                this.playSound(SoundEvents.WITHER_SPAWN, 0.78F, 0.56F); // 凋零声音
            }
            case 2 -> {
                Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).setBaseValue(10 + 5);
                Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0.25 * 1.2);
                Objects.requireNonNull(this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(0.32);
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 1, false, false, false));
                this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, false));
                this.playSound(SoundEvents.WITHER_SPAWN, 1.0F, 0.85F);
            }
            case 3 -> {
                Objects.requireNonNull(this.getAttribute(Attributes.ARMOR)).setBaseValue(10 + 10);
                Objects.requireNonNull(this.getAttribute(Attributes.MOVEMENT_SPEED)).setBaseValue(0.25 * 1.5);
                Objects.requireNonNull(this.getAttribute(Attributes.KNOCKBACK_RESISTANCE)).setBaseValue(0.40);
                this.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, Integer.MAX_VALUE, 2, false, false, false));
                this.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false, false));
                this.playSound(SoundEvents.WITHER_SPAWN, 1.5F, 1.24F);
            }
        }
    }

    // 检查连续命中保护
    private void checkConsecutiveHitProtection(DamageSource damageSource) {
        if (this.getCurrentPhase() != 1) return;
        if (!(damageSource.getEntity() instanceof Player) || damageSource.is(DamageTypes.ARROW) || damageSource.is(DamageTypes.TRIDENT)) return;

        this.InvincibleCount++;

        if (this.InvincibleCount >= this.requiredHitsForInvulnerability) {
            int invulnerabilityDuration = 60 + this.random.nextInt(101); // 60-160 tick
            this.setInvulnerable(true);
            this.setPhaseTimer(invulnerabilityDuration);
            this.InvincibleCount = 0;

            this.requiredHitsForInvulnerability = 5 + this.random.nextInt(6); // 5-10 hits

            if (!this.level().isClientSide) {
                this.playSound(SoundEvents.SHIELD_BLOCK, 1.0F, 1.5F);
            }
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();

        if (this.isDeadOrDying()) {
            if (this.bossEvent != null) {
                this.bossEvent.setProgress(0.0f);
            }
            return;
        }

        flight().customServerAiStepPre();

        if (this.isTeleportOnCool && this.tickCount >= this.teleportCoolTick) {
            this.isTeleportOnCool = false;
        }

        if (this.bossEvent != null) {
            this.bossEvent.setProgress(this.getCustomHealthPercent());
        }

        if (this.isInvulnerable()) {
            int timer = this.getPhaseTimer();
            if (timer > 0) {
                this.setPhaseTimer(timer - 1);
            } else {
                this.setInvulnerable(false);
                this.setPhaseTimer(0);
            }
        }

        if (this.isPerformingTeleportGrab && this.teleportGrabbedPlayer != null) {
            this.teleportGrabDuration--;

            // 稳固的锁定
            this.maintainPlayerGrab();

            if (this.strikeDelayTicks > 0) {
                this.strikeDelayTicks--;
                if (this.strikeDelayTicks <= 0) {
                    this.performTeleportAttack(this.teleportGrabbedPlayer);
                }
            }

            if (this.teleportGrabDuration <= 0) {
                this.releaseTeleportGrab();
            }
        }

        if (this.pendingStrikeTarget != null && this.strikeDelayTicks > 0 && !this.isPerformingTeleportGrab) {
            this.strikeDelayTicks--;
            if (this.strikeDelayTicks <= 0) {
                this.performTeleportAttack(this.pendingStrikeTarget);
                this.pendingStrikeTarget = null;
            }
        }

        if (!this.isTeleportOnCool && this.tickCount >= this.nextTeleportTick && !this.isInvulnerable() && this.getCurrentPhase() == 0) {
            if (this.isGoalRunning(ChargeChaseAttackGoal.class)) {
                this.scheduleNextTeleport();
            } else {
                this.attemptTeleportAttack(); // 在阶段0
            }
        }

        this.updateStageSpecificBehavior();

        if (this.tickCount - this.lastDamageTick > 60) {
            if (this.getCurrentPhase() == 1) {
                this.InvincibleCount = 0;
            }
        }
        if (this.level().isClientSide) return;

        flight().customServerAiStepPost();

        if (this.entityData.get(IS_CHARGING_BOW) && this.getTarget() == null) {
            this.entityData.set(IS_CHARGING_BOW, false);
        }
    }

    private void maintainPlayerGrab() {
        if (this.teleportGrabbedPlayer == null || this.playerLockPosition == null || this.level().isClientSide) return;
        if (!(this.teleportGrabbedPlayer instanceof ServerPlayer serverPlayer)) return;

        double playerEyeHeight = serverPlayer.getEyeY() - serverPlayer.getY();
        double desiredY = this.getEyeY() - playerEyeHeight;
        Vec3 desiredPos = new Vec3(this.playerLockPosition.x, desiredY, this.playerLockPosition.z);
        if (!this.isValidPullPosition(desiredPos)) {
            desiredPos = this.playerLockPosition;
        }
        this.playerLockPosition = desiredPos;

        double dxToBoss = this.getX() - desiredPos.x;
        double dzToBoss = this.getZ() - desiredPos.z;
        float yawToBoss = (float)(Mth.atan2(dzToBoss, dxToBoss) * (180D/Math.PI)) - 90.0F;

        serverPlayer.connection.teleport(desiredPos.x, desiredPos.y, desiredPos.z, yawToBoss, 0.0F);
        serverPlayer.setYHeadRot(yawToBoss);
        serverPlayer.setYBodyRot(yawToBoss);
        serverPlayer.setDeltaMovement(Vec3.ZERO);
        serverPlayer.fallDistance = 0.0F;

        // 计算从 Boss 眼睛到玩家眼睛的向量
        Vec3 bossEyePos = this.getEyePosition();
        Vec3 playerEyePos = serverPlayer.getEyePosition();
        Vec3 lookVector = playerEyePos.subtract(bossEyePos);

        double horizontalDistance = lookVector.horizontalDistance();
        float desiredYaw = (float) (Mth.atan2(lookVector.z, lookVector.x) * (180D / Math.PI)) - 90.0F;
        float desiredPitch = (float) (-(Mth.atan2(lookVector.y, horizontalDistance) * (180D / Math.PI)));

        this.setYRot(desiredYaw);
        this.setXRot(desiredPitch);
        this.setYHeadRot(desiredYaw);
        this.setYBodyRot(desiredYaw);
    }

    private void updateStageSpecificBehavior() {
        int stage = this.getCurrentPhase();
        float currentHP = this.getCustomHealth();
        float maxHP = this.getCustomMaxHealth();

        switch (stage) {
            case 1 -> {
                if (this.tickCount - this.lastRegenerationTick >= 100) { // 5 seconds
                    float targetHP = maxHP * STAGE_1_THRESHOLD;
                    if (currentHP < targetHP) {
                        float healAmount = 2.42F + this.random.nextFloat() * 5.56F; // 2-7 HP
                        this.heal(Math.min(healAmount, targetHP - currentHP));
                    }
                    this.lastRegenerationTick = this.tickCount;
                }
            }
            case 2 -> {
                if (this.tickCount - this.lastRegenerationTick >= 240) { // 12 seconds
                    float targetHP = maxHP * STAGE_2_THRESHOLD;
                    if (currentHP < targetHP) {
                        float healAmount = 6.65F + this.random.nextFloat() * 12.56F; // 5-10 HP
                        this.heal(Math.min(healAmount, targetHP - currentHP));
                    }
                    this.lastRegenerationTick = this.tickCount;
                }
            }
            case 3 -> {
                if (this.tickCount - this.lastRegenerationTick >= 300) { // 15 seconds
                    float targetHP = maxHP * STAGE_3_THRESHOLD;
                    if (currentHP < targetHP) {
                        float healAmount = 10.0F + this.random.nextFloat() * 16.42F; // 10-16 hp
                        this.heal(Math.min(healAmount, targetHP - currentHP));
                    }
                    this.lastRegenerationTick = this.tickCount;
                }

                if (this.tickCount % 9 == 0) this.applyNearbyPlayerEffects();
            }
        }
    }

    private void applyNearbyPlayerEffects() {
        if (this.level().isClientSide) return;
        List<Player> nearbyPlayers = this.level().getEntitiesOfClass(Player.class, new AABB(this.blockPosition()).inflate(68.0));
        for (Player player : nearbyPlayers) {
            // 减速&虚弱
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0, false, false, false));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 0, false, false, false));
        }
    }

    private void broadcastStageChange(int newStage) {
        if (newStage == 1) {
            if (hasTriggeredStage1) return;
            hasTriggeredStage1 = true;
        } else if (newStage == 2) {
            if (hasTriggeredStage2) return;
            hasTriggeredStage2 = true;
        } else if (newStage == 3) {
            if (hasTriggeredStage3) return;
            hasTriggeredStage3 = true;
        } else return;
        String message = switch (newStage) {
            case 1 -> "§d§l" + this.getName().getString() + "§r阶段-1(测试)";
            case 2 -> "§6§l" + this.getName().getString() + "§r阶段-2(测试)";
            case 3 -> "§4§l" + this.getName().getString() + "§r阶段-3(测试)";
            default -> "";
        };
        this.broadcastMessage(Component.literal(message));
    }

    @SafeVarargs
    private boolean isAnyDamageType(DamageSource source, ResourceKey<DamageType>... types) {
        for (ResourceKey<DamageType> type : types) {
            if (source.is(type)) {
                return true;
            }
        }
        return false;
    }

    private void broadcastMessage(Component message) {
        List<Player> nearbyPlayers = this.level().getEntitiesOfClass(Player.class, new AABB(this.blockPosition()).inflate(68.0));
        for (Player player : nearbyPlayers) {
            player.sendSystemMessage(message);
        }
    }

    public void startCharge() {
        isCharging = true;
        isChargeCool = false;
        this.setSprinting(true);
    }

    public void forceEndCharge() {
        isCharging = false;
        this.setSprinting(false);
        chargeCool = CHARGE_COOL_TICKS;
        isChargeCool = true;
        this.getNavigation().stop();
    }

    public int getCurrentPhase() {
        return this.entityData.get(CURRENT_PHASE);
    }

    public void setCurrentPhase(int phase) {
        this.entityData.set(CURRENT_PHASE, phase);
    }

    public boolean isInvulnerable() {
        return this.entityData.get(IS_INVULNERABLE);
    }

    public void setInvulnerable(boolean invulnerable) {
        this.entityData.set(IS_INVULNERABLE, invulnerable);
    }

    public int getPhaseTimer() {
        return this.entityData.get(PHASE_TIMER);
    }

    public void setPhaseTimer(int timer) {
        this.entityData.set(PHASE_TIMER, timer);
    }

    public boolean isTeleporting() {
        return this.entityData.get(IS_TELEPORTING);
    }

    public void setTeleporting(boolean teleporting) {
        this.entityData.set(IS_TELEPORTING, teleporting);
    }

    public void setPreTeleportTarget(@Nullable Player player) {
        this.preTeleportTargetId = (player == null) ? null : player.getUUID();
    }

    public void setTeleportGrabbing(boolean grabbing) {
        this.entityData.set(IS_TELEPORT_GRABBING, grabbing);
    }

    public boolean isAerialCombatEnabled() {
        return this.entityData.get(AlysiaFlightSystem.AERIAL_COMBAT_ENABLED);
    }

    @Override
    public boolean canFireProjectileWeapon(@NotNull ProjectileWeaponItem weapon) {
        return weapon instanceof BowItem;
    }

    @Override
    public void startUsingItem(@NotNull InteractionHand hand) {
        ItemStack itemstack = this.getItemInHand(hand);
        if (!itemstack.isEmpty() && !this.isUsingItem()) {
            super.startUsingItem(hand);
            this.useItemRemaining = itemstack.getUseDuration();
            if (!this.level().isClientSide) {
                this.setLivingEntityFlag(1, true); // 设置使用物品标志
            }
        }
    }

    @Override
    public void stopUsingItem() {
        super.stopUsingItem();
        if (!this.level().isClientSide) {
            this.setLivingEntityFlag(1, false); // 清除使用物品标志
        }
    }

    @Nullable
    public Player getPreTeleportTarget() {
        return (preTeleportTargetId == null) ? null : this.level().getPlayerByUUID(preTeleportTargetId);
    }

    //  自定义boss死亡时掉落物
    @Override
    protected void dropCustomDeathLoot(@NotNull DamageSource source, int looting, boolean recentlyHitByPlayer) {
        super.dropCustomDeathLoot(source, looting, recentlyHitByPlayer);
        ItemStack stack = new ItemStack(AdorableArmoryRegister.TRUE_DEMON_BOW.get());
        this.spawnAtLocation(stack);

        // Test
        if (!this.level().isClientSide()) {
            int baseExperience = 200; // 经验
            if (this.level() instanceof ServerLevel serverLevel) {
                ExperienceOrb.award(serverLevel, this.position(), baseExperience);
            }
        }
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Phase", this.getCurrentPhase());
        tag.putBoolean("PhaseOne", hasTriggeredStage1);
        tag.putBoolean("PhaseTwo", hasTriggeredStage2);
        tag.putBoolean("PhaseThree", hasTriggeredStage3);
        flight().addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Phase"))this.setCurrentPhase(tag.getInt("Phase"));
        hasTriggeredStage1 = tag.getBoolean("PhaseOne");
        hasTriggeredStage2 = tag.getBoolean("PhaseTwo");
        hasTriggeredStage3 = tag.getBoolean("PhaseThree");

        flight().readAdditionalSaveData(tag);
    }

    @Override
    public void startSeenByPlayer(@NotNull ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(@NotNull ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if (this.bossEvent != null) {
            this.bossEvent.setProgress(this.getCustomHealthPercent());
        }

        if (dataAccessor.equals(ACTION_ID) && level().isClientSide) {
            int id = this.getEntityData().get(ACTION_ID);
            if (id == TeleportationGrabAction.ID) {
                this.teleportGrabAnimation.startIfStopped(this.tickCount);
            } else {
                this.teleportGrabAnimation.stop();
            }
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        if (this.bossEvent != null && name != null) {
            this.bossEvent.setName(name);
        }
    }

    @Override
    public boolean canBeAffected(@NotNull MobEffectInstance effect) {
        int stage = this.getCurrentPhase();
        if (stage >= 1 && ALL_EFFECTS.contains(effect.getEffect())) return false;
        return super.canBeAffected(effect);
    }

    @Override
    protected boolean canRide(@NotNull Entity vehicle) {
        return false;
    }

    @Override
    public @NotNull Component getName() {
        return this.hasCustomName() ? Objects.requireNonNull(this.getCustomName()) : super.getName(); // entity.adorablearmory.scarlet_lora_alysia
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (this.isPerformingTeleportGrab) this.releaseTeleportGrab();
        this.bossEvent.removeAllPlayers();
        super.remove(reason);
    }

    @Override
    public boolean isPowered() {
        return this.isInvulnerable();
    }
}
