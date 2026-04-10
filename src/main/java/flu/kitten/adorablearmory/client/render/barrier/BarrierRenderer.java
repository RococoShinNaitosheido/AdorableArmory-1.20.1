package flu.kitten.adorablearmory.client.render.barrier;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.client.render.AnemiaSpecialEffectRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.*;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BarrierRenderer {

    public record Barrier(BlockPos center, int half, int height) {}
    private static final int DEFAULT_HALF = 6;
    private static final int DEFAULT_HEIGHT = 8;
    private static final float WALL_THICKNESS = 0.00001f;
    private static final List<Barrier> BARRIERS = new ArrayList<>();

    public static Barrier addBarrier(BlockPos center) {
        return addBarrier(center, DEFAULT_HALF, DEFAULT_HEIGHT);
    }

    public static List<Barrier> getBarriersView() {
        return Collections.unmodifiableList(BARRIERS);
    }

    public static void clear() {
        BARRIERS.clear();
    }

    public static Barrier addBarrier(BlockPos center, int half, int height) {
        int h = height <= 0 ? DEFAULT_HEIGHT : height;
        int a = half <= 0 ? DEFAULT_HALF : half;
        Barrier b = new Barrier(center.immutable(), a, h);
        BARRIERS.add(b);
        return b;
    }

    public static void setBarriers(Collection<Barrier> all) {
        BARRIERS.clear();
        BARRIERS.addAll(all);
    }

    @SubscribeEvent
    public static void renderBarrier(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || BARRIERS.isEmpty()) return;

        PoseStack poseStack = event.getPoseStack();
        Vec3 camPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        float partialTicks = event.getPartialTick();

        for (Barrier barrier : BARRIERS) {
            renderBarrier(poseStack, buffers, barrier, partialTicks);
        }

        buffers.endBatch();
        poseStack.popPose();
    }

    private static void renderBarrier(PoseStack poseStack, MultiBufferSource buffers, Barrier barrier, float partialTicks) {
        double centerX = barrier.center().getX() + 0.5;
        double centerY = barrier.center().getY();
        double centerZ = barrier.center().getZ() + 0.5;

        Vec3 center = new Vec3(centerX, centerY + barrier.height() * 0.5, centerZ);

        int half = Math.max(1, barrier.half());
        int height = Math.max(1, barrier.height());

        double minX = centerX - half;
        double maxX = centerX + half;
        double minZ = centerZ - half;
        double maxZ = centerZ + half;
        double maxY = centerY + height;

        AABB front = new AABB(minX, centerY, maxZ - WALL_THICKNESS, maxX, maxY, maxZ + WALL_THICKNESS); // +Z
        AABB back = new AABB(minX, centerY, minZ - WALL_THICKNESS, maxX, maxY, minZ + WALL_THICKNESS); // -Z
        AABB right = new AABB(maxX - WALL_THICKNESS, centerY, minZ, maxX + WALL_THICKNESS, maxY, maxZ); // +X
        AABB left = new AABB(minX - WALL_THICKNESS, centerY, minZ, minX + WALL_THICKNESS, maxY, maxZ); // -X

        AnemiaSpecialEffectRender render = AnemiaSpecialEffectRender.effectRender;
        if (render == null) return;

        render.renderFaceOfAABB(poseStack, buffers, front, center, 4, partialTicks, LightTexture.FULL_BRIGHT, true, height * 0.5f); // z=maxZ
        render.renderFaceOfAABB(poseStack, buffers, back,  center, 2, partialTicks, LightTexture.FULL_BRIGHT, true, height * 0.5f); // z=minZ
        render.renderFaceOfAABB(poseStack, buffers, right, center, 3, partialTicks, LightTexture.FULL_BRIGHT, true, height * 0.5f); // x=maxX
        render.renderFaceOfAABB(poseStack, buffers, left,  center, 5, partialTicks, LightTexture.FULL_BRIGHT, true, height * 0.5f); // x=minX
    }
}
