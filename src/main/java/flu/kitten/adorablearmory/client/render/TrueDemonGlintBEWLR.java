package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class TrueDemonGlintBEWLR extends BlockEntityWithoutLevelRenderer {

    public TrueDemonGlintBEWLR() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        var minecraft = Minecraft.getInstance();
        var itemRenderer = minecraft.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, null, 0);
        final int light = 15728880;

        boolean fabulous = minecraft.options.graphicsMode().get() == GraphicsStatus.FABULOUS;
        MultiBufferSource.BufferSource source = (bufferSource instanceof MultiBufferSource.BufferSource multiBufferSource) ? multiBufferSource : null;

        for (RenderType type : model.getRenderTypes(stack, fabulous)) {
            itemRenderer.renderModelLists(model, stack, light, packedOverlay, poseStack, bufferSource.getBuffer(type));
            if (source != null) source.endBatch(type);
        }

        if (stack.hasFoil()) {
            RenderType glintType = TrueDemonGlintRenderTypes.itemGlintDirect();

            float hue = (Util.getMillis() % 8000L) / 8000.0f;
            int rgb = Mth.hsvToRgb(hue, 1.0f, 1.0f);
            float r = ((rgb >> 16) & 255) / 255f;
            float g = ((rgb >> 8) & 255) / 255f;
            float b = (rgb & 255) / 255f;

            RenderSystem.setShaderColor(r, g, b, 1.0f);
            RenderSystem.setShaderGlintAlpha(0.9f);

            itemRenderer.renderModelLists(model, stack, light, packedOverlay, poseStack, bufferSource.getBuffer(glintType));
            if (source != null) source.endBatch(glintType);

            RenderSystem.setShaderGlintAlpha(1.0f);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
    }
}
