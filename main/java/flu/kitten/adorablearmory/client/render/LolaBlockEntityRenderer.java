package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.block.LolaBlockEntity;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class LolaBlockEntityRenderer implements BlockEntityRenderer<LolaBlockEntity> {

    public LolaBlockEntityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(@NotNull LolaBlockEntity lolaTntBlockEntity, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource source, int packedLight, int packedOverlay) {
        BlockState blockState = lolaTntBlockEntity.getBlockState();
        ItemStack stack = new ItemStack(AdorableArmoryRegister.LOLA_ITEM.get());
        poseStack.pushPose();
        poseStack.translate(0.5,0.5,0.5);
        poseStack.scale(1.0011123400F,1.0011123400F,1.0011123400F);
        poseStack.translate(-0.5,-0.5,-0.5);
        CosmicBlockRenderHelper.renderBlockQuads(blockState, poseStack, source, packedLight, packedOverlay, stack);
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull LolaBlockEntity lolaBlockEntity) {
        return true;
    }
}
