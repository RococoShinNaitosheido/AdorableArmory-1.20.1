package flu.kitten.adorablearmory.entity.boss;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.List;

public class AlwaysFacePlayerGoal extends Goal {

    private final ScarletLoraAlysia boss;
    private Player cached;

    public AlwaysFacePlayerGoal(ScarletLoraAlysia boss) {
        this.boss = boss;
        this.setFlags(EnumSet.of(Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = boss.getTarget();
        if (boss.isCharging()) return false;
        if (target instanceof Player player && isValid(player)) {
            cached = player;
            return true;
        }
        cached = findNearestValidPlayer();
        return cached != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (boss.isCharging()) return false;
        return cached != null && cached.isAlive() && !cached.isSpectator() && !cached.isCreative();
    }

    @Override
    public void stop() {
        cached = null;
    }

    @Override
    public void tick() {
        if (cached == null) return;
        boss.getLookControl().setLookAt(cached.getX(), cached.getEyeY(), cached.getZ(), 30.0F, 30.0F);
    }

    private boolean isValid(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.isCreative() && player.getHealth() > 0;
    }

    private Player findNearestValidPlayer() {
        double range = boss.getAttributeValue(Attributes.FOLLOW_RANGE);
        List<Player> list = boss.level().getEntitiesOfClass(Player.class, boss.getBoundingBox().inflate(range)); // old new AABB(boss.blockPosition()).inflate(range)
        Player best = null;
        double d2 = Double.MAX_VALUE;
        for (Player player : list) {
            if (!isValid(player)) continue;
            double distance = boss.distanceToSqr(player);
            if (distance < d2) {
                d2 = distance; best = player;
            }
        }
        return best;
    }
}
