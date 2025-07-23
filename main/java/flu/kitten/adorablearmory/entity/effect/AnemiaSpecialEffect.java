package flu.kitten.adorablearmory.entity.effect;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

public class AnemiaSpecialEffect extends Entity {

    private static final EntityDataAccessor<Float> MASS = SynchedEntityData.defineId(AnemiaSpecialEffect.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EVENT_HORIZON_RADIUS = SynchedEntityData.defineId(AnemiaSpecialEffect.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ACCRETION_DISK_RADIUS = SynchedEntityData.defineId(AnemiaSpecialEffect.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ROTATION_SPEED = SynchedEntityData.defineId(AnemiaSpecialEffect.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(AnemiaSpecialEffect.class, EntityDataSerializers.INT);
    private static final float DEFAULT_MASS = 100.0f;
    private static final float DEFAULT_EVENT_HORIZON = 3.0f;
    private static final float DEFAULT_ACCRETION_DISK = 15.0f;
    private static final float DEFAULT_ROTATION_SPEED = 0.5f;
    private static final int DEFAULT_LIFETIME = -1; // 表示无限生命周期
    private int currentLifetime = 0;
    private float pullStrength = 0.5f;

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

        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0.0, -0.64, 0.0));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
        this.setDeltaMovement(this.getDeltaMovement().multiply(0.78, 0.78, 0.78));

        if (this.getLifetime() > 0) {
            this.currentLifetime++;
        }
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MASS, DEFAULT_MASS);
        this.entityData.define(EVENT_HORIZON_RADIUS, DEFAULT_EVENT_HORIZON);
        this.entityData.define(ACCRETION_DISK_RADIUS, DEFAULT_ACCRETION_DISK);
        this.entityData.define(ROTATION_SPEED, DEFAULT_ROTATION_SPEED);
        this.entityData.define(LIFETIME, DEFAULT_LIFETIME);
    }

    public float getMass() {
        return this.entityData.get(MASS);
    }

    public void setMass(float mass) {
        this.entityData.set(MASS, mass);
    }

    public float getEventHorizonRadius() {
        return this.entityData.get(EVENT_HORIZON_RADIUS);
    }

    public void setEventHorizonRadius(float radius) {
        this.entityData.set(EVENT_HORIZON_RADIUS, radius);
    }

    public float getAccretionDiskRadius() {
        return this.entityData.get(ACCRETION_DISK_RADIUS);
    }

    public void setAccretionDiskRadius(float radius) {
        this.entityData.set(ACCRETION_DISK_RADIUS, radius);
    }

    public float getRotationSpeed() {
        return this.entityData.get(ROTATION_SPEED);
    }

    public void setRotationSpeed(float speed) {
        this.entityData.set(ROTATION_SPEED, speed);
    }

    public int getLifetime() {
        return this.entityData.get(LIFETIME);
    }

    public void setLifetime(int lifetime) {
        this.entityData.set(LIFETIME, lifetime);
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
        this.setMass(tag.getFloat("Mass"));
        this.setEventHorizonRadius(tag.getFloat("EventHorizon"));
        this.setAccretionDiskRadius(tag.getFloat("AccretionDisk"));
        this.setRotationSpeed(tag.getFloat("RotationSpeed"));
        this.setLifetime(tag.getInt("Lifetime"));
        this.currentLifetime = tag.getInt("CurrentLifetime");
        this.pullStrength = tag.getFloat("PullStrength");
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
        tag.putFloat("Mass", this.getMass());
        tag.putFloat("EventHorizon", this.getEventHorizonRadius());
        tag.putFloat("AccretionDisk", this.getAccretionDiskRadius());
        tag.putFloat("RotationSpeed", this.getRotationSpeed());
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
        return this.getBoundingBox(); // 或直接 return super.getBoundingBox()
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
