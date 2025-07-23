package flu.kitten.adorablearmory.api;

import flu.kitten.adorablearmory.item.SparklingDreamIdolStar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

@SuppressWarnings("unused")
public class Handler {
    private static final List<Entity> entitiesToRemove = new ArrayList<>();

    @SubscribeEvent
    public void itemExplosion(ExplosionEvent.Detonate detonate) {
        List<Entity> entitiesToRemove = new ArrayList<>();
        for (Entity entity : detonate.getAffectedEntities()) {
            if (entity instanceof ItemEntity) {
                ItemStack stack = ((ItemEntity) entity).getItem();
                if (stack.getItem() instanceof SparklingDreamIdolStar) {
                    entitiesToRemove.add(entity);
                }
            }
        }
        detonate.getAffectedEntities().removeAll(entitiesToRemove);
    }

    @SubscribeEvent
    public void levelTick(TickEvent.LevelTickEvent tick) {
        if (tick.phase == TickEvent.Phase.END) {
            List<Entity> entitiesToRemoveCopy = new ArrayList<>(entitiesToRemove);
            for (Entity entity : entitiesToRemoveCopy) {
                entity.remove(Entity.RemovalReason.KILLED);
            }
            entitiesToRemove.clear();
            if (!entitiesToRemoveCopy.isEmpty()) {
                Entity en = tick.level.getEntity(entitiesToRemoveCopy.get(0).getId());

                if (en != null) {
                    int index = entitiesToRemoveCopy.indexOf(en);

                    if (index >= 0) {
                        try {
                            if (index < entitiesToRemove.size()) {
                                entitiesToRemove.set(index, null);
                            } else {
                                entitiesToRemove.add(null);
                            }
                        } catch (IndexOutOfBoundsException e) {
                            System.err.println("entity Index: " + e.getMessage());
                            entitiesToRemove.add(null);
                        }
                    }
                }
            }
        }
    }
}
