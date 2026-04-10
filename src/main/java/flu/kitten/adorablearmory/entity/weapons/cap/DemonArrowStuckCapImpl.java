package flu.kitten.adorablearmory.entity.weapons.cap;

public class DemonArrowStuckCapImpl implements IDemonArrowStuckCap {
    private int count;
    private int dropCooling;

    @Override public int getCount() {
        return count;
    }

    @Override public void setCount(int value) {
        count = Math.max(0, value);
    }

    @Override
    public int getDropCooling() {
        return dropCooling;
    }

    @Override
    public void setDropCooling(int ticks) {
        dropCooling = Math.max(0, ticks);
    }
}
