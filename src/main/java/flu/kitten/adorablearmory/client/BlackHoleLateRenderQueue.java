package flu.kitten.adorablearmory.client;

import flu.kitten.adorablearmory.entity.effect.TrueDemonBlackHole;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;

public final class BlackHoleLateRenderQueue {
    public record Job(TrueDemonBlackHole demonBlackHole, float yaw, int light) {}
    private static final Int2ObjectOpenHashMap<Job> JOBS = new Int2ObjectOpenHashMap<>();

    public static void queue(TrueDemonBlackHole demonBlackHole, float yaw, int light) {
        JOBS.put(demonBlackHole.getId(), new Job(demonBlackHole, yaw, light));
    }

    public static void renderAll(RenderLevelStageEvent event) {
        if (JOBS.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        var dispatcher = minecraft.getEntityRenderDispatcher();
        var camera = event.getCamera();
        Vec3 camPos = camera.getPosition();
        float partialTick = event.getPartialTick();

        MultiBufferSource.BufferSource source = minecraft.renderBuffers().bufferSource();

        BlackHoleLensClient.setLatePass(true);
        try {
            for (Job job : JOBS.values()) {
                var demonBlackHole = job.demonBlackHole();
                if (demonBlackHole == null || demonBlackHole.isRemoved()) continue;

                double x = Mth.lerp(partialTick, demonBlackHole.xo, demonBlackHole.getX()) - camPos.x;
                double y = Mth.lerp(partialTick, demonBlackHole.yo, demonBlackHole.getY()) - camPos.y;
                double z = Mth.lerp(partialTick, demonBlackHole.zo, demonBlackHole.getZ()) - camPos.z;

                dispatcher.render(demonBlackHole, x, y, z, job.yaw(), partialTick, event.getPoseStack(), source, job.light());
            }
        } finally {
            BlackHoleLensClient.setLatePass(false);
            source.endBatch();
            JOBS.clear();
        }
    }

    private BlackHoleLateRenderQueue() {}
}
