package flu.kitten.adorablearmory.entity.boss.action;

import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;

public interface AlysiaAction {
    int id(); // 动作唯一ID 用于网络同步/模型层识别

    int duration(); //  tick return <=0 表示无限持续 需外部stop()

    default void onStart(ScarletLoraAlysia boss) {
    }

    // tick回调 Server与Client可触发
    default void onTick(ScarletLoraAlysia boss, int tick) {
    }

    default void onEnd(ScarletLoraAlysia boss, int elapsed) {
    }

    // 是否阻塞实体常规移动
    default boolean blocksMovement() {
        return false;
    }
}
