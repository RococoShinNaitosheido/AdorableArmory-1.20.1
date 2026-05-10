package flu.kitten.adorablearmory.client.compat.oculus;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import flu.kitten.adorablearmory.block.LolaBlockEntity;
import flu.kitten.adorablearmory.client.render.ShaderBlockRenderHelper;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.register.ShaderRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LolaCosmicBlockLateRenderQueue {
    private static final float SURFACE_SCALE = 1.0011123400F;
    private static final Map<BlockPos, Entry> ENTRIES = new LinkedHashMap<>();

    public static void enqueue(LolaBlockEntity blockEntity, PoseStack poseStack, int packedLight, int packedOverlay) {
        BlockPos pos = blockEntity.getBlockPos().immutable();
        PoseStack.Pose pose = poseStack.last();
        ENTRIES.put(pos, new Entry(pos, new Matrix4f(pose.pose()), new Matrix3f(pose.normal()), new Matrix4f(RenderSystem.getModelViewMatrix()), new Matrix4f(RenderSystem.getProjectionMatrix()), packedLight, packedOverlay));
    }

    public static void renderAfterLevel() {
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
            for (Entry entry : ENTRIES.values()) {
                BlockState currentState = mc.level.getBlockState(entry.pos());
                if (!ShaderRendererRegistry.hasCosmicLayer(currentState)) {
                    continue;
                }
                ItemStack stack = new ItemStack(currentState.getBlock().asItem());

                modelViewStack.last().pose().set(entry.modelView());
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f(entry.projection()), VertexSorting.DISTANCE_TO_ORIGIN);

                PoseStack poseStack = new PoseStack();
                poseStack.last().pose().set(entry.pose());
                poseStack.last().normal().set(entry.normal());
                poseStack.pushPose();
                poseStack.translate(0.5D, 0.5D, 0.5D);
                poseStack.scale(SURFACE_SCALE, SURFACE_SCALE, SURFACE_SCALE);
                poseStack.translate(-0.5D, -0.5D, -0.5D);
                ShaderBlockRenderHelper.renderCosmicBlock(currentState, poseStack, buffers, entry.packedLight(), entry.packedOverlay(), stack, AdorableArmoryShaders.COSMIC_BLOCK_AFTER_LEVEL_RENDER_TYPE);
                poseStack.popPose();
            }

            buffers.endBatch(AdorableArmoryShaders.COSMIC_BLOCK_AFTER_LEVEL_RENDER_TYPE);
        } finally {
            RenderSystem.setProjectionMatrix(previousProjection, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.popPose();
            RenderSystem.applyModelViewMatrix();
            LateShaderLayerState.finishMainTargetPass();
            ENTRIES.clear();
        }
    }

    private record Entry(BlockPos pos, Matrix4f pose, Matrix3f normal, Matrix4f modelView, Matrix4f projection, int packedLight, int packedOverlay) {}

    private LolaCosmicBlockLateRenderQueue() {}
}
