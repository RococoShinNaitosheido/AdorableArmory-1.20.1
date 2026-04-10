package flu.kitten.adorablearmory.entity.effect;

import flu.kitten.adorablearmory.entity.damagetype.TrueDemonDamageSource;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AnemiaSpecialEffect extends Entity {

    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(AnemiaSpecialEffect.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DISTORTION_INTENSITY = SynchedEntityData.defineId(AnemiaSpecialEffect.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> IS_MAGNETIZED = SynchedEntityData.defineId(AnemiaSpecialEffect.class, EntityDataSerializers.BOOLEAN);
    private static final int DEFAULT_LIFETIME = -1; // 无限生命期
    private static final float MAGNETIZATION_RANGE = 12.0f; // 检测范围
    private static final float MAX_DISTORTION_INTENSITY = 0.1f; // 最大扭曲强度
    private int currentLifetime = 0;
    private float pullStrength = 0.5f;
    private float distortionPhase = 0.0f;
    private float flickerIntensity = 0.0f;

    private static final EntityDimensions CUBE_DIMENSIONS = EntityDimensions.scalable(5.0F, 5.0F);

    public AnemiaSpecialEffect(EntityType<?> type, Level level) {
        super(type, level);
        this.refreshDimensions();
    }

    @Override
    public @NotNull EntityDimensions getDimensions(@NotNull Pose pose) {
        return CUBE_DIMENSIONS;
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getLifetime() > 0) {
            this.currentLifetime++;

            if (this.currentLifetime >= this.getLifetime()) {
                if (!level().isClientSide) {
                    this.discard();
                }
                return;
            }
        }

        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.64, 0.0));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.78, 0.78, 0.78));

        updateMagnetizationEffect();

        if (level().isClientSide) {
            updateClientDistortion();
        }
    }

    // test
    private void updateMagnetizationEffect() {
        List<Player> nearbyPlayers = level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(MAGNETIZATION_RANGE));

        float closestDistance = Float.MAX_VALUE;
        boolean hasMagnetization = false;

        if (!nearbyPlayers.isEmpty()) {
            for (Player player : nearbyPlayers) {
                float distance = this.distanceTo(player);
                if (distance < MAGNETIZATION_RANGE) {
                    closestDistance = Math.min(closestDistance, distance);
                    hasMagnetization = true;
                }
            }
        }

        setMagnetized(hasMagnetization);

        if (hasMagnetization) {
            float normalizedDistance = Math.max(0, closestDistance - 2.0f) / (MAGNETIZATION_RANGE - 2.0f);
            float intensity = (1.0f - normalizedDistance) * MAX_DISTORTION_INTENSITY;
            setDistortionIntensity(intensity);
        } else {
            setDistortionIntensity(0.0f);
        }

        if (level().isClientSide) return;

        List<LivingEntity> targets = level().getEntitiesOfClass(LivingEntity.class, this.getBoundingBox().inflate(MAGNETIZATION_RANGE));

        for (LivingEntity entity : targets) {
            float distance = this.distanceTo(entity);
            if (distance >= MAGNETIZATION_RANGE) continue;

            // 只对玩家执行code kill可去掉instanceof限制对所有生物生效
            if (entity instanceof Player player) {
                // 创造/旁观/无敌
                boolean blockedByMode = player.isCreative() || player.isSpectator();
                if (blockedByMode) continue;

                if (entity.getHealth() > 0) {
                    level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_HURT, player.getSoundSource(), 1.0f, 1.0f);
                    TrueDemonDamageSource.armDeathGuard(entity);
                    TrueDemonTypes.TrueDemonDamageUtil.trueDemonMechanismKill(entity, null);
                }
                if (entity.getHealth() <= 0) break;
            }
        }
    }

    private void updateClientDistortion() {
        if (isMagnetized()) {
            distortionPhase += 0.1f + (getDistortionIntensity() * 0.2f);
            flickerIntensity = (float) (Math.sin(distortionPhase * 3.0f) * 0.5f + 0.5f);
            flickerIntensity += (random.nextFloat() - 0.5f) * 0.3f * getDistortionIntensity();
            flickerIntensity = Math.max(0.0f, Math.min(1.0f, flickerIntensity));
        } else {
            distortionPhase *= 0.95f;
            flickerIntensity *= 0.9f;
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(LIFETIME, DEFAULT_LIFETIME);
        this.entityData.define(DISTORTION_INTENSITY,0.0f);
        this.entityData.define(IS_MAGNETIZED, false);
    }

    public int getLifetime() {
        return this.entityData.get(LIFETIME);
    }

    public void setLifetime(int lifetime) {
        this.entityData.set(LIFETIME, lifetime);
    }

    public float getDistortionIntensity() {
        return this.entityData.get(DISTORTION_INTENSITY);
    }

    public void setDistortionIntensity(float intensity) {
        this.entityData.set(DISTORTION_INTENSITY, Math.max(0.0f, Math.min(1.0f, intensity)));
    }

    public boolean isMagnetized() {
        return this.entityData.get(IS_MAGNETIZED);
    }

    public void setMagnetized(boolean magnetized) {
        this.entityData.set(IS_MAGNETIZED, magnetized);
    }

    public float getDistortionPhase() {
        return distortionPhase;
    }

    public float getFlickerIntensity() {
        return flickerIntensity;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        this.setLifetime(tag.getInt("Lifetime"));
        this.currentLifetime = tag.getInt("CurrentLifetime");
        this.pullStrength = tag.getFloat("PullStrength");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putInt("Lifetime", this.getLifetime());
        tag.putInt("CurrentLifetime", this.currentLifetime);
        tag.putFloat("PullStrength", this.pullStrength);
    }

    @Override
    public boolean isNoGravity() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double renderDistance = 180;
        return distance < renderDistance * renderDistance;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        return super.getBoundingBox();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isOnFire() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
