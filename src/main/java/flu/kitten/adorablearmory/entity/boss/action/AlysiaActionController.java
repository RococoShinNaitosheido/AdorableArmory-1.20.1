package flu.kitten.adorablearmory.entity.boss.action;

import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import net.minecraft.network.syncher.EntityDataAccessor;

public class AlysiaActionController {
    private final ScarletLoraAlysia owner;
    private final EntityDataAccessor<Integer> actionIdData;
    private final EntityDataAccessor<Integer> actionTickData;
    private AlysiaAction current;
    private int tick;

    public AlysiaActionController(ScarletLoraAlysia owner, EntityDataAccessor<Integer> idData, EntityDataAccessor<Integer> tickData) {
        this.owner = owner;
        this.actionIdData = idData;
        this.actionTickData = tickData;
    }

    public void tick() {
        if (current != null) {
            tick++;

            if (!owner.level().isClientSide) {
                owner.getEntityData().set(actionIdData, current.id());
                owner.getEntityData().set(actionTickData, tick);
            }

            current.onTick(owner, tick);
            int max = current.duration();
            if (max > 0 && tick >= max) {
                stop();
            }
        } else {
            if (!owner.level().isClientSide) {
                owner.getEntityData().set(actionIdData, 0);
                owner.getEntityData().set(actionTickData, 0);
            }
        }
    }

    public void start(AlysiaAction next) {
        if (next == null) return;
        if (current != null && current.id() == next.id()) return; // 幂等
        stop();
        current = next;
        tick = 0;
        current.onStart(owner);
        owner.getEntityData().set(actionIdData, current.id());
        owner.getEntityData().set(actionTickData, 0);
    }

    public void stop() {
        if (current != null) {
            current.onEnd(owner, tick);
            current = null;
            tick = 0;
            owner.getEntityData().set(actionIdData, 0);
            owner.getEntityData().set(actionTickData, 0);
        }
    }

    public int getCurrentId() {
        return owner.getEntityData().get(actionIdData);
    }

    public int getCurrentTick() {
        return owner.getEntityData().get(actionTickData);
    }

    public boolean isBlockingMovement() {
        return current != null && current.blocksMovement();
    }
}
