package flu.kitten.adorablearmory.client.render.dimensional;

import flu.kitten.adorablearmory.client.render.dimensional.config.DimensionalSlashTuning;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

public final class DimensionalSlashParticleBurst {
    private DimensionalSlashParticleBurst() {}

    public static void spawnSlashShatter(ClientLevel level, DimensionalSlashLine line, Random random) {
        Vec3 start = line.start();
        Vec3 end = line.end();
        Vec3 dir = line.direction();
        Vec3 side = line.normal().cross(dir);
        if (side.lengthSqr() < 1.0e-8) side = pickSide(dir);
        side = side.normalize();

        int particleCount = Math.max(8, Math.min(28, Math.round(line.length() * 2.2f)));
        for (int i = 0; i < particleCount; i++) {
            float t = (i + random.nextFloat()) / particleCount;
            Vec3 pos = start.lerp(end, t).add(side.scale((random.nextFloat() - 0.5f) * line.width() * 1.5f));
            Vec3 vel = dir.scale((random.nextFloat() - 0.5f) * 1.9f)
                    .add(side.scale((random.nextFloat() - 0.5f) * 1.4f))
                    .add(line.normal().scale((random.nextFloat() - 0.5f) * 1.2f));
            level.addParticle(AdorableArmoryRegister.TRUE_DEMON_PARTICLE.get(), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
        }
    }

    public static void spawnFinalBurst(ClientLevel level, Vec3 center, float radius, Random random) {
        int particleCount = Math.max(40, Math.min(120, Math.round(radius * 12.0f)));
        for (int i = 0; i < particleCount; i++) {
            Vec3 dir = randomUnit(random);
            float speed = 1.8f + random.nextFloat() * 3.4f;
            Vec3 pos = center.add(dir.scale(random.nextFloat() * 0.75f));
            Vec3 vel = dir.scale(speed);
            level.addParticle(AdorableArmoryRegister.TRUE_DEMON_PARTICLE.get(), pos.x, pos.y, pos.z, vel.x, vel.y, vel.z);
        }
    }

    public static void spawnFinalWorldShards(Vec3 center, float radius, Random random, List<DimensionalSlashWorldShard> shards) {
        if (!DimensionalSlashTuning.WorldSlash.WORLD_SHARDS_ENABLED) return;

        int count = Math.max(0, DimensionalSlashTuning.WorldSlash.WORLD_SHARD_COUNT);
        int lifetime = Math.max(1, DimensionalSlashTuning.WorldSlash.WORLD_SHARD_LIFETIME_TICKS);
        for (int i = 0; i < count; i++) {
            Vec3 dir = randomUnit(random);
            Vec3 tangent = pickSide(dir);
            Vec3 bitangent = dir.cross(tangent);
            if (bitangent.lengthSqr() < 1.0e-8) bitangent = pickSide(tangent);
            bitangent = bitangent.normalize();

            float spawnRadius = DimensionalSlashTuning.WorldSlash.WORLD_SHARD_SPAWN_RADIUS;
            Vec3 origin = center.add(dir.scale(random.nextFloat() * Math.max(0.1f, spawnRadius)));
            float speed = DimensionalSlashTuning.WorldSlash.WORLD_SHARD_EXPLOSION_SPEED
                    + random.nextFloat() * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_EXPLOSION_RANDOM;
            Vec3 velocity = dir.scale(speed).add(0.0, DimensionalSlashTuning.WorldSlash.WORLD_SHARD_UPWARD_BIAS * random.nextFloat(), 0.0);
            float size = Mth.lerp(random.nextFloat(),
                    DimensionalSlashTuning.WorldSlash.WORLD_SHARD_SIZE_MIN,
                    DimensionalSlashTuning.WorldSlash.WORLD_SHARD_SIZE_MAX);
            float aspect = 0.35f + random.nextFloat() * 0.95f;
            float angularA = (random.nextFloat() - 0.5f) * 2.0f * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_TUMBLE_SPEED;
            float angularB = (random.nextFloat() - 0.5f) * 1.4f * DimensionalSlashTuning.WorldSlash.WORLD_SHARD_TUMBLE_SPEED;
            shards.add(new DimensionalSlashWorldShard(origin, velocity, tangent, bitangent, dir, size, aspect, angularA, angularB, random.nextFloat(), random.nextLong(), lifetime + random.nextInt(Math.max(1, lifetime / 3))));
        }
    }

    private static Vec3 randomUnit(Random random) {
        float yaw = random.nextFloat() * Mth.TWO_PI;
        float y = random.nextFloat() * 2.0f - 1.0f;
        float h = Mth.sqrt(Math.max(0.0f, 1.0f - y * y));
        return new Vec3(Mth.cos(yaw) * h, y, Mth.sin(yaw) * h);
    }

    private static Vec3 pickSide(Vec3 direction) {
        Vec3 up = Math.abs(direction.y) > 0.85 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 side = direction.cross(up);
        if (side.lengthSqr() < 1.0e-8) return new Vec3(1.0, 0.0, 0.0);
        return side.normalize();
    }
}
