package flu.kitten.adorablearmory.api.client.model;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.util.TransformUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class WrappedItemModel extends PerspectiveModel {

    private static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    protected PerspectiveModelState modelState;
    protected BakedModel wrapped;
    protected ModelState parentState;
    @Nullable protected LivingEntity entity;
    @Nullable protected ClientLevel world;
    protected ItemOverrides overrideList;

    public WrappedItemModel (BakedModel wrapped) {
        this.overrideList = new ItemOverrides() {
            @Nullable
            @Override
            public BakedModel resolve(@NotNull BakedModel model, @NotNull ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                WrappedItemModel.this.entity = entity;
                WrappedItemModel.this.world = ((world == null) ? ((entity == null) ? null : ((ClientLevel) entity.level())) : null);
                if (WrappedItemModel.this.isCos()) {
                    return WrappedItemModel.this.wrapped.getOverrides().resolve(model, stack, world, entity, seed);
                }
                return model;
            }
        };
        this.wrapped = wrapped;
        this.parentState = TransformUtils.stateFromItemTransforms(wrapped.getTransforms());
    }

    @SuppressWarnings("unused")
    public static List<BakedQuad> bakeItem(final List<TextureAtlasSprite> sprites) {
        final LinkedList<BakedQuad> quads = new LinkedList<>();
        for (final TextureAtlasSprite sprite : sprites) {
            final List<BlockElement> unbaked = ITEM_MODEL_GENERATOR.processFrames(sprites.indexOf(sprite), "layer" + sprites.indexOf(sprite), sprite.contents());
            for (final BlockElement element : unbaked) {
                for (final Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
                    quads.add(FACE_BAKERY.bakeQuad(element.from, element.to, entry.getValue(), sprite, entry.getKey(), new PerspectiveModelState(ImmutableMap.of()), element.rotation, element.shade, AdorableArmory.path("dynamic")));
                }
            }
        }
        return quads;
    }

    @SuppressWarnings("unused")
    public static <E> void checkArgument(final E argument, final Predicate<E> predicate) {
        if (predicate.test(argument)) {
            throw new RuntimeException("");
        }
    }

    @SuppressWarnings("unused")
    public static <T> boolean isNullOrContainsNull(final T[] input) {
        if (input != null) {
            for (final T t : input) {
                if (t == null) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

    public boolean isCos() {
        return false;
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon() {
        return this.wrapped.getParticleIcon();
    }

    @Override
    public @NotNull TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return this.wrapped.getParticleIcon(data);
    }

    @Override
    public @NotNull ItemOverrides getOverrides() {
        return this.overrideList;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return this.wrapped.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return this.wrapped.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return this.wrapped.usesBlockLight();
    }


    protected void renderWrapped(ItemStack stack, PoseStack pStack, MultiBufferSource buffers, int packedLight, int packedOverlay, boolean fabulous) {
        renderWrapped(stack, pStack, buffers, packedLight, packedOverlay, fabulous, Function.identity());
    }

    protected void renderWrapped(ItemStack stack, PoseStack pStack, MultiBufferSource buffers, int packedLight, int packedOverlay, boolean fabulous, Function<VertexConsumer, VertexConsumer> consOverride) {
        BakedModel model = this.wrapped.getOverrides().resolve(this.wrapped, stack, this.world, this.entity, 0);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        assert model != null;
        for (BakedModel bakedModel : model.getRenderPasses(stack, true)) {
            for (RenderType rendertype : bakedModel.getRenderTypes(stack, true)) {
                itemRenderer.renderModelLists(bakedModel, stack, packedLight, packedOverlay, pStack, consOverride.apply(buffers.getBuffer(rendertype)));
            }
        }
    }

    @Override
    public @Nullable PerspectiveModelState getModelState() {
        return this.modelState;
    }
}
