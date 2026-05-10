package flu.kitten.adorablearmory.client.compat.oculus;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import flu.kitten.adorablearmory.client.model.CosmicBakeModel;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class CosmicItemLateRenderQueue {
    private static final List<Entry> ENTRIES = new ArrayList<>();

    public static void enqueue(CosmicBakeModel renderer, ItemStack stack, ItemDisplayContext context, PoseStack poseStack, int packedLight, int packedOverlay, BakedModel model, RenderType renderType) {
        PoseStack.Pose pose = poseStack.last();
        ENTRIES.add(new Entry(renderer, stack.copy(), context, new Matrix4f(pose.pose()), new Matrix3f(pose.normal()), new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()), packedLight, packedOverlay, model, renderType));
    }

    public static void renderBeforeHand() {
        renderMatchingEntries(false, false);
    }

    public static void renderAfterHand() {
        renderMatchingEntries(true, true);
    }

    public static void renderAfterLevel() {
        renderAfterHand();
    }

    private static void renderMatchingEntries(boolean includeFirstPersonHand, boolean clearSkippedEntries) {
        if (ENTRIES.isEmpty()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ENTRIES.clear();
            return;
        }

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        PoseStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushPose();
        try {
            LateShaderLayerState.prepareMainTargetPass();
            Iterator<Entry> iterator = ENTRIES.iterator();
            while (iterator.hasNext()) {
                Entry entry = iterator.next();
                if (isFirstPersonHandContext(entry.context()) && !includeFirstPersonHand) {
                    if (clearSkippedEntries) {
                        iterator.remove();
                    }
                    continue;
                }

                modelViewStack.last().pose().set(entry.modelView());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f(entry.projection()), VertexSorting.DISTANCE_TO_ORIGIN);

                PoseStack poseStack = new PoseStack();
                poseStack.last().pose().set(entry.pose());
                poseStack.last().normal().set(entry.normal());
                entry.renderer().renderShaderLayer(entry.stack(), entry.context(), poseStack, buffers, entry.packedLight(), entry.packedOverlay(), entry.model(), entry.renderType(), true);

                buffers.endBatch(AdorableArmoryShaders.COSMIC_ITEM_AFTER_LEVEL_RENDER_TYPE);
                buffers.endBatch(AdorableArmoryShaders.COSMIC_HAND_AFTER_LEVEL_RENDER_TYPE);
                buffers.endBatch(AdorableArmoryShaders.SKY_ITEM_AFTER_LEVEL_RENDER_TYPE);
                buffers.endBatch(AdorableArmoryShaders.SKY_ITEM_HAND_AFTER_LEVEL_RENDER_TYPE);
                iterator.remove();
            }
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            LateShaderLayerState.finishMainTargetPass();
            if (clearSkippedEntries) {
                ENTRIES.clear();
            }
        }
    }

    private static boolean isFirstPersonHandContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
    }

    private record Entry(CosmicBakeModel renderer, ItemStack stack, ItemDisplayContext context, Matrix4f pose, Matrix3f normal, Matrix4f modelView, Matrix4f projection, int packedLight, int packedOverlay, BakedModel model, RenderType renderType) {}

    private CosmicItemLateRenderQueue() {}
}
