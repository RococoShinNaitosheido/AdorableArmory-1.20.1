package flu.kitten.adorablearmory.api;

import flu.kitten.adorablearmory.client.gui.AlysiaBossBarRenderer;
import flu.kitten.adorablearmory.entity.boss.ScarletLoraAlysia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.*;

@OnlyIn(Dist.CLIENT)
public class BossBarRenderEvent {

    private static AlysiaBossBarRenderer barRenderer;
    private static final List<ScarletLoraAlysia> nearbyBosses = new ArrayList<>();
    private static boolean enableDecorativeOverlay = true;
    private static float decorativeAlpha = 1.0f;
    private static int barsDrawnThisFrame = 0;
    private static long lastEntityCountAtMs = 0L;
    private static int lastEntityCount = 0;
    private static int vanillaBossBarBottomY = 0;
    private static final int VANILLA_DEFAULT_START_Y = 0; // BossBar 默认起始 Y

    private static int getScarletCountCached(Minecraft minecraft) {
        long now = System.currentTimeMillis();
        if (now - lastEntityCountAtMs > 100) {
            lastEntityCountAtMs = now;
            if (minecraft.level != null && minecraft.player != null) {
                final double radius = 256.0;
                Vec3 position = minecraft.player.position();
                AABB area = new AABB(position.x - radius, position.y - radius, position.z - radius, position.x + radius, position.y + radius, position.z + radius);
                lastEntityCount = minecraft.level.getEntitiesOfClass(ScarletLoraAlysia.class, area).size();
            } else {
                lastEntityCount = 0;
            }
        }
        return lastEntityCount;
    }

    @SubscribeEvent
    public static void overlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type()) return;

        barsDrawnThisFrame = 0;
        vanillaBossBarBottomY = 0;

        Minecraft mc = Minecraft.getInstance();
        nearbyBosses.clear();

        if (mc.player == null || mc.level == null) return;

        Vec3 playerPos = mc.player.position();
        final double range = 120.0;
        AABB searchArea = new AABB(playerPos.x - range, playerPos.y - range, playerPos.z - range, playerPos.x + range, playerPos.y + range, playerPos.z + range);
        nearbyBosses.addAll(mc.level.getEntitiesOfClass(ScarletLoraAlysia.class, searchArea));
    }

    @SubscribeEvent
    public static void bossBarProgress(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (!isAlysiaBossEvent(event, nearbyBosses)) {
            vanillaBossBarBottomY = Math.max(vanillaBossBarBottomY, event.getY() + event.getIncrement() - 12); // 血条间隔
            return;
        }

        event.setCanceled(true);
        event.setIncrement(0);
    }

    @SubscribeEvent
    public static void overlayPost(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.BOSS_EVENT_PROGRESS.type()) return;

        if (nearbyBosses.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        if (barRenderer == null) {
            barRenderer = new AlysiaBossBarRenderer(mc);
            configureRenderer();
        }

        int worldScarletCount = getScarletCountCached(mc);
        int startY = (vanillaBossBarBottomY > 0 ? vanillaBossBarBottomY : VANILLA_DEFAULT_START_Y);

        renderMultipleBossBars(event.getGuiGraphics(), worldScarletCount, startY);
    }

    private static void renderMultipleBossBars(GuiGraphics guiGraphics, int worldScarletCount, int startY) {
        int yOffset = startY;

        for (ScarletLoraAlysia boss : nearbyBosses) {
            if (barsDrawnThisFrame >= 3 && worldScarletCount > 3) break;

            if (boss != null && boss.isAlive() && !boss.isRemoved()) {
                guiGraphics.pose().pushPose();
                guiGraphics.pose().translate(0, yOffset, 0);

                barRenderer.render(guiGraphics, boss);

                guiGraphics.pose().popPose();

                yOffset += 40;
                barsDrawnThisFrame++;
            }
        }
    }

    private static boolean isAlysiaBossEvent(CustomizeGuiOverlayEvent.BossEventProgress event, List<ScarletLoraAlysia> nearby) {
        String barName = event.getBossEvent().getName().getString();
        for (ScarletLoraAlysia boss : nearby) {
            if (boss != null && boss.isAlive() && barName.equals(boss.getDisplayName().getString())) {
                return true;
            }
        }
        return false;
    }

    private static void configureRenderer() {
        if (barRenderer != null) {
            barRenderer.setDecorativeOverlayEnabled(enableDecorativeOverlay);
            barRenderer.setDecorativeAlpha(decorativeAlpha);
        }
    }

    @SuppressWarnings("unused")
    public static void setDecorativeOverlayEnabled(boolean enabled) {
        enableDecorativeOverlay = enabled;
        if (barRenderer != null) barRenderer.setDecorativeOverlayEnabled(enabled);
    }

    @SuppressWarnings("unused")
    public static void setDecorativeAlpha(float alpha) {
        decorativeAlpha = Math.max(0.0f, Math.min(1.0f, alpha));
        if (barRenderer != null) barRenderer.setDecorativeAlpha(decorativeAlpha);
    }

    @SuppressWarnings("unused")
    public static void reloadRenderer() {
        barRenderer = null;
    }

    public static AlysiaBossBarRenderer getRenderer() {
        return barRenderer;
    }
}
