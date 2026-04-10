package flu.kitten.adorablearmory.entity.boss.action;

import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;

public class TeleportationGrabAction implements AlysiaAction {
    public static final int ID = 1;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public int duration() {
        return 0; // 0-负数 = 持续到外部stop()
    }

    @Override
    public boolean blocksMovement() {
        return false;
    }

    @Override
    public void onStart(ScarletLoraAlysia boss) {
        boss.setTeleportGrabbing(true);
        // 启动动画
        boss.teleportGrabAnimation.startIfStopped(boss.tickCount);
    }

    @Override
    public void onTick(ScarletLoraAlysia boss, int tick) {
    }

    @Override
    public void onEnd(ScarletLoraAlysia boss, int elapsed) {
        boss.setTeleportGrabbing(false);
        // 停止并复位动画
        boss.teleportGrabAnimation.stop();
    }
}
