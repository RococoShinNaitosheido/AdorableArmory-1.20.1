package flu.kitten.adorablearmory.entity.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public abstract class AlysiaCustomHealthMonster extends Monster {

    protected static final EntityDataAccessor<Float> CUSTOM_HEALTH = SynchedEntityData.defineId(AlysiaCustomHealthMonster.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Integer> CUSTOM_INVULNERABLE_TIME = SynchedEntityData.defineId(AlysiaCustomHealthMonster.class, EntityDataSerializers.INT);

    protected AlysiaCustomHealthMonster(EntityType<? extends Monster> type, Level level) {
        super(type, level);
    }

    public abstract float getCustomMaxHealth(); // 自定义最大生命值
    public abstract float getMaxDamagePerHit(); // 单次最多允许扣多少血
    public abstract int getCustomInvulnerableTime(); // 每次成功受伤后给多少 tick 无敌时间

    public float getCustomHealth() {
        return this.entityData.get(CUSTOM_HEALTH);
    }

    protected final void setCustomHealth(float value) {
        float clamped = Mth.clamp(value, 0.0f, this.getCustomMaxHealth());
        this.entityData.set(CUSTOM_HEALTH, clamped);
    }

    public float getCustomHealthPercent() {
        float max = this.getCustomMaxHealth();
        if (max <= 0.0f) return 0.0f;
        return Mth.clamp(this.getCustomHealth() / max, 0.0f, 1.0f);
    }

    public int getInvulnerableUntilTick() {
        return this.entityData.get(CUSTOM_INVULNERABLE_TIME);
    }

    public void setInvulnerableUntilTick(int tick) {
        this.entityData.set(CUSTOM_INVULNERABLE_TIME, Math.max(0, tick));
    }

    public int getRemainingInvulnerableTime() {
        return Math.max(0, this.getInvulnerableUntilTick() - this.tickCount);
    }

    public boolean hasInvulnerableTime() {
        return this.tickCount < this.getInvulnerableUntilTick();
    }

    public boolean isCustomDead() {
        return this.getCustomHealth() <= 0.0f;
    }

    public boolean applyCustomDamage(@NotNull DamageSource source, float damage) {
        if (damage <= 0.0f) return false;
        if (this.hasInvulnerableTime()) return false;
        if (this.isDeadOrDying()) return false;

        float cappedDamage = Math.min(damage, Math.max(0.0f, this.getMaxDamagePerHit()));
        if (cappedDamage <= 0.0f) return false;

        float newHealth = this.getCustomHealth() - cappedDamage;
        this.setCustomHealth(newHealth);

        if (this.isCustomDead()) {
            this.setCustomHealth(0.0f);
            this.setInvulnerableUntilTick(0);
            this.onCustomDeath(source);
            return true;
        }
        return true;
    }

    protected void onCustomDeath(@NotNull DamageSource source) {
        if (this.isRemoved() || super.isDeadOrDying() || this.deathTime > 0) {
            return;
        }

        this.setCustomHealth(0.0f);
        this.setInvulnerableUntilTick(0);

        this.setHealth(0.0f);
        this.die(source);
    }

    public void healCustom(float amount) {
        if (amount <= 0.0f) return;
        if (this.isCustomDead()) return;
        this.setCustomHealth(this.getCustomHealth() + amount);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CUSTOM_HEALTH, 0.0f);
        this.entityData.define(CUSTOM_INVULNERABLE_TIME, 0);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            if (this.isCustomDead() && !super.isDeadOrDying()) {
                this.onCustomDeath(this.damageSources().generic());
            }
        }
    }

    @Override
    public void heal(float amount) {
        this.healCustom(amount);
    }

    @Override
    public boolean isAlive() {
        return super.isAlive() && !this.isCustomDead();
    }

    @Override
    public boolean isDeadOrDying() {
        return super.isDeadOrDying() || this.isCustomDead();
    }

    @Override
    protected void tickDeath() {
        super.tickDeath();
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("CustomHealth", this.getCustomHealth());
        tag.putInt("CustomInvulnerableTime", this.getRemainingInvulnerableTime());
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("CustomHealth")) {
            this.setCustomHealth(tag.getFloat("CustomHealth"));
        } else {
            this.setCustomHealth(this.getCustomMaxHealth());
        }

        int remain = 0;
        if (tag.contains("CustomInvulnerableTime")) {
            remain = Math.max(0, tag.getInt("CustomInvulnerableTime"));
        }
        this.setInvulnerableUntilTick(this.tickCount + remain);

        if (this.isCustomDead()) {
            this.setHealth(0.0f);
        } else {
            this.setHealth(this.getMaxHealth());
        }
    }

    @Override
    protected void actuallyHurt(@NotNull DamageSource source, float amount) {
        if (this.isCustomDead()) return;

        float cappedDamage = Math.min(amount, Math.max(0.0f, this.getMaxDamagePerHit()));
        if (cappedDamage <= 0.0f) {
            return;
        }

        float newHealth = this.getCustomHealth() - cappedDamage;
        this.setCustomHealth(newHealth);

        if (this.isCustomDead()) {
            this.setCustomHealth(0.0f);
            this.setHealth(0.0f);
            this.onCustomDeath(source);
        }

        int invulnerableTime = this.getCustomInvulnerableTime();
        if (invulnerableTime > 0) {
            this.setInvulnerableUntilTick(this.tickCount + invulnerableTime);
        }

        this.setHealth(this.getMaxHealth());
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.isCustomDead()) return false;
        if (this.hasInvulnerableTime()) return false;
        return super.hurt(source, amount);
    }
}
