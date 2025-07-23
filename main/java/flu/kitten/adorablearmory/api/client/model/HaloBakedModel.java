package flu.kitten.adorablearmory.api.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.client.render.buffer.AlphaOverrideVertexConsumer;
import flu.kitten.adorablearmory.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;
import java.util.Random;

@SuppressWarnings("unused")
public class HaloBakedModel extends WrappedItemModel {

    private final Random random;
    private final BakedQuad haloQuad;
    private final boolean pulse;
    private final boolean pulseInWorld;
    private final int haloColor;
    private final int haloSize;
    private final TransformUtils.ModelType detectedModelType;

    private static final EnumSet<ItemDisplayContext> ALL_CONTEXTS = EnumSet.of(
            ItemDisplayContext.GUI,
            ItemDisplayContext.GROUND,
            ItemDisplayContext.FIXED,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
            ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
            ItemDisplayContext.HEAD
    );

    public HaloBakedModel(BakedModel wrapped, TextureAtlasSprite sprite, int color, int size, boolean pulse) {
        this(wrapped, sprite, color, size, pulse, true);
    }

    public HaloBakedModel(BakedModel wrapped, TextureAtlasSprite sprite, int color, int size, boolean pulse, boolean pulseInWorld) {
        super(wrapped);
        this.random = new Random();
        this.haloColor = color;
        this.haloSize = size;
        this.pulse = pulse;
        this.pulseInWorld = pulseInWorld;
        this.haloQuad = generateHaloQuad(sprite, size, color);
        this.detectedModelType = detectModelType(wrapped);
    }

    private TransformUtils.ModelType detectModelType(BakedModel model) {
        if (model.toString().toLowerCase().contains("block")) return TransformUtils.ModelType.BLOCK_ITEM;
        return TransformUtils.ModelType.ITEM;
    }

    static BakedQuad generateHaloQuad(final TextureAtlasSprite sprite, final int size, final int color) {
        final float[] colors = new ColourARGB(color).getRGBA();
        final double spread = size / 16.0;
        final double offsetX = -0.03;
        final double offsetY = 0.03;
        final double minX = 0.0 - spread + offsetX;
        final double maxX = 1.0 + spread + offsetX;
        final double minY = 0.0 - spread + offsetY;
        final double maxY = 1.0 + spread + offsetY;
        final float minU = sprite.getU0();
        final float maxU = sprite.getU1();
        final float minV = sprite.getV0();
        final float maxV = sprite.getV1();
        final Quad quad = new Quad();
        quad.reset(CachedFormat.BLOCK);
        quad.setTexture(sprite);
        putVertex(quad.vertices[0], maxX, maxY, 0.0, maxU, minV);
        putVertex(quad.vertices[1], minX, maxY, 0.0, minU, minV);
        putVertex(quad.vertices[2], minX, minY, 0.0, minU, maxV);
        putVertex(quad.vertices[3], maxX, minY, 0.0, maxU, maxV);
        for (int i = 0; i < 4; ++i) {
            System.arraycopy(colors, 0, quad.vertices[i].color, 0, 4);
        }
        quad.calculateOrientation(true);
        return quad.bake();
    }

    @SuppressWarnings("all")
    static void putVertex(final Quad.Vertex vx, final double x, final double y, final double z, final double u, final double v) {
        vx.vec[0] = (float) x;vx.vec[1] = (float) y;vx.vec[2] = (float) z;vx.uv[0] = (float) u;vx.uv[1] = (float) v;
    }

    @Override
    public void renderItem(ItemStack stack, ItemDisplayContext transformType, PoseStack pStack, MultiBufferSource source, int packedLight, int packedOverlay) {
        if (transformType == ItemDisplayContext.GUI) {
            renderHalo(stack, pStack, source, packedLight, packedOverlay);
            this.renderWrapped(stack, pStack, source, packedLight, packedOverlay, true);
            if (this.pulse) {
                renderPulse(stack, ItemDisplayContext.GUI, pStack, source, packedLight, packedOverlay);
            }
        } else {
            this.renderWrapped(stack, pStack, source, packedLight, packedOverlay, true);
            if (ALL_CONTEXTS.contains(transformType) && this.pulse && this.pulseInWorld) {
                renderPulseEffectOnly(stack, transformType, pStack, source, packedLight, packedOverlay);
            }
        }
    }

    private void renderHalo(ItemStack stack, PoseStack pStack, MultiBufferSource source, int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            RenderType haloRenderType = ItemBlockRenderTypes.getRenderType(stack, true);
            minecraft.getItemRenderer().renderQuadList(pStack, source.getBuffer(haloRenderType), List.of(this.haloQuad), stack, packedLight, packedOverlay
            );
        } catch (Exception e) {
            System.err.println("Failed to render halo in GUI: " + e.getMessage());
        }

        if (this.pulse) {
            renderPulse(stack, ItemDisplayContext.GUI, pStack, source, packedLight, packedOverlay);
        }
    }

    private void renderPulseEffectOnly(ItemStack stack, ItemDisplayContext transformType, PoseStack pStack, MultiBufferSource source, int packedLight, int packedOverlay) {
        renderPulse(stack, transformType, pStack, source, packedLight, packedOverlay);
    }

    private void renderPulse(ItemStack stack, ItemDisplayContext transformType, PoseStack pStack, MultiBufferSource source, int packedLight, int packedOverlay) {
        pStack.pushPose();
        try {
            double pulseIntensity = getPulseIntensityForContext(transformType);
            double scale = random.nextDouble() * 0.15D + 0.95D;
            double trans = (1.0D - scale) / 2.0D;
            pStack.translate(trans, trans, 0.0D);
            pStack.scale((float) scale, (float) scale, 1.0001F);
            this.renderWrapped(stack, pStack, source, packedLight, packedOverlay, true, (buffer) -> new AlphaOverrideVertexConsumer(buffer, pulseIntensity));
        } catch (Exception e) {
            System.err.println("Failed to render pulse effect: " + e.getMessage());
        } finally {
            pStack.popPose();
        }
    }

    @SuppressWarnings("all")
    private double getPulseIntensityForContext(ItemDisplayContext context) {
        return switch (context) {
            case GUI -> 0.6D;
            case GROUND -> 0.4D;
            case FIXED -> 0.35D;
            case THIRD_PERSON_RIGHT_HAND, THIRD_PERSON_LEFT_HAND -> 0.3D;
            case FIRST_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND -> 0.25D;
            case HEAD -> 0.45D;
            default -> 0.3D;
        };
    }

    @Override
    public PerspectiveModelState getModelState() {
        return TransformUtils.getAppropriateModelState(null, this.detectedModelType);
    }

    public PerspectiveModelState getModelState(ItemDisplayContext context) {
        return TransformUtils.getAppropriateModelState(context, this.detectedModelType);
    }

    public static boolean isBlockItem(ItemStack stack) {
        return stack.getItem() instanceof BlockItem;
    }

    public HaloBakedModel createBlockVariant(TextureAtlasSprite sprite) {
        return new HaloBakedModel(this.wrapped, sprite, this.haloColor, this.haloSize, this.pulse) {
            @Override
            public PerspectiveModelState getModelState() {
                return TransformUtils.DEFAULT_BLOCK;
            }
        };
    }

    public boolean isPulseInWorldEnabled() {
        return this.pulseInWorld;
    }

    public boolean isPulseEnabled() {
        return this.pulse;
    }

    public int getHaloColor() {
        return this.haloColor;
    }

    public int getHaloSize() {
        return this.haloSize;
    }

    public enum ModelType {
        ITEM(TransformUtils.ModelType.ITEM),
        TOOL(TransformUtils.ModelType.TOOL),
        BLOCK(TransformUtils.ModelType.BLOCK),
        BLOCK_ITEM(TransformUtils.ModelType.BLOCK_ITEM);

        private final TransformUtils.ModelType transformType;

        ModelType(TransformUtils.ModelType transformType) {
            this.transformType = transformType;
        }

        public TransformUtils.ModelType getTransformType() {
            return transformType;
        }
    }
}
