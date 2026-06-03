package flu.kitten.adorablearmory.client.render.dimensional;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.VisualToggle;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.entity.boss.abilitymanager.EarthquakeVisualsManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DimensionalSlashClientEffects {
    private static final int MAX_ACTIVE_EFFECTS = 4;
    private static final List<DimensionalSlashEffect> EFFECTS = new ArrayList<>();
    private static List<DimensionalSlashLine> pendingScreenBreakWorldLines = List.of();

    private DimensionalSlashClientEffects() {}

    public static void trigger(Vec3 center, int slashCount, float length, float radius, long seed) {
        if (!VisualToggle.areEffectsEnabled()) return;
        while (EFFECTS.size() >= MAX_ACTIVE_EFFECTS) {
            EFFECTS.remove(0);
        }
        EFFECTS.add(DimensionalSlashEffect.create(center, slashCount, length, radius, seed));
    }

    static void triggerFinalScreenBreak(long seed, List<DimensionalSlashLine> lines) {
        if (!VisualToggle.areEffectsEnabled()) return;
        pendingScreenBreakWorldLines = lines == null ? List.of() : List.copyOf(lines);
        DimensionalSlashScreenShader.trigger(seed);
        EarthquakeVisualsManager.triggerExtreme(4.2f, 22);
    }

    @SubscribeEvent
    public static void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !VisualToggle.areEffectsEnabled()) {
            EFFECTS.clear();
            DimensionalSlashBloomRenderer.clear();
            DimensionalSlashWorldShardRenderer.clear();
            DimensionalSlashScreenShader.clear();
            pendingScreenBreakWorldLines = List.of();
            return;
        }

        Iterator<DimensionalSlashEffect> iterator = EFFECTS.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().tick(level)) iterator.remove();
        }

        DimensionalSlashScreenShader.tick();
    }

    @SubscribeEvent
    public static void renderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (EFFECTS.isEmpty() || !VisualToggle.areEffectsEnabled()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        var mainTarget = mc.getMainRenderTarget();
        configurePendingScreenBreakLines(poseStack, camera, mainTarget.width, mainTarget.height);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        DimensionalSlashBloomRenderer.beginFrame(mainTarget);
        poseStack.pushPose();
        for (DimensionalSlashEffect effect : EFFECTS) {
            effect.renderBloomMask(poseStack, buffers, camera, event.getPartialTick());
        }
        buffers.endBatch();
        poseStack.popPose();
        DimensionalSlashBloomRenderer.finishAndComposite(mainTarget);

        poseStack.pushPose();
        for (DimensionalSlashEffect effect : EFFECTS) {
            effect.renderBody(poseStack, buffers, camera, event.getPartialTick());
        }
        buffers.endBatch(AdorableArmoryShaders.DIMENSIONAL_SLASH_CORE);
        for (DimensionalSlashEffect effect : EFFECTS) {
            effect.renderWhiteCores(poseStack, buffers, camera, event.getPartialTick());
        }
        buffers.endBatch(AdorableArmoryShaders.DIMENSIONAL_SLASH_CORE);
        poseStack.popPose();

        DimensionalSlashScreenShader.capturePending(mainTarget);

        poseStack.pushPose();
        DimensionalSlashWorldShardRenderer.render(poseStack, EFFECTS, camera, event.getPartialTick(), mainTarget);
        poseStack.popPose();
    }

    public static void renderScreenOverlay(GuiGraphics graphics, float partialTick, int width, int height) {
        DimensionalSlashScreenShader.renderPost(partialTick);
    }

    private static void configurePendingScreenBreakLines(PoseStack poseStack, Vec3 camera, int width, int height) {
        if (pendingScreenBreakWorldLines.isEmpty() || width <= 0 || height <= 0) return;

        List<DimensionalSlashScreenShader.ScreenBreakLine> screenLines = new ArrayList<>();
        for (DimensionalSlashLine line : pendingScreenBreakWorldLines) {
            DimensionalSlashScreenShader.ScreenBreakLine screenLine = projectLineToScreen(poseStack, camera, line, width, height);
            if (screenLine == null) continue;

            float dx = (screenLine.x1() - screenLine.x0()) * width;
            float dy = (screenLine.y1() - screenLine.y0()) * height;
            if (dx * dx + dy * dy < 24.0f * 24.0f) continue;

            screenLines.add(screenLine);
        }

        if (!screenLines.isEmpty()) {
            DimensionalSlashScreenShader.configurePendingBreakLines(screenLines);
        }
    }

    private static DimensionalSlashScreenShader.ScreenBreakLine projectLineToScreen(PoseStack poseStack, Vec3 camera, DimensionalSlashLine line, int width, int height) {
        ScreenPoint first = null;
        ScreenPoint last = null;
        int samples = 9;
        for (int i = 0; i < samples; i++) {
            float t = i / (float) (samples - 1);
            ScreenPoint point = projectToScreen(poseStack, camera, line.start().lerp(line.end(), t), width, height);
            if (point == null) continue;
            if (first == null) first = point;
            last = point;
        }

        if (first == null || last == null) return null;
        return new DimensionalSlashScreenShader.ScreenBreakLine(first.x, first.y, last.x, last.y);
    }

    private static ScreenPoint projectToScreen(PoseStack poseStack, Vec3 camera, Vec3 worldPoint, int width, int height) {
        Matrix4f modelView = poseStack.last().pose();
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        Vector4f view = new Vector4f(
                (float) (worldPoint.x - camera.x),
                (float) (worldPoint.y - camera.y),
                (float) (worldPoint.z - camera.z),
                1.0f
        );
        modelView.transform(view);

        Vector4f clip = new Vector4f(view);
        projection.transform(clip);
        if (clip.w <= 0.00001f) return null;

        float invW = 1.0f / clip.w;
        float ndcX = clip.x * invW;
        float ndcY = clip.y * invW;
        if (!Float.isFinite(ndcX) || !Float.isFinite(ndcY)) return null;

        return new ScreenPoint(
                ndcX * 0.5f + 0.5f,
                1.0f - (ndcY * 0.5f + 0.5f)
        );
    }

    private record ScreenPoint(float x, float y) {}
}
