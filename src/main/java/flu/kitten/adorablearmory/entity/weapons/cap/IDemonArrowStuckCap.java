package flu.kitten.adorablearmory.entity.weapons.cap;

public interface IDemonArrowStuckCap {
    int getCount();
    void setCount(int value);

    default void add(int delta) {
        setCount(getCount() + delta);
    }

    int getDropCooling(); // 距离下一次减少还剩多少tick
    void setDropCooling(int ticks);
}
