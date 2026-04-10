package flu.kitten.adorablearmory.api.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.List;

public abstract class PerspectiveModel implements BakedModel {

    @Nullable public abstract PerspectiveModelState getModelState();

    public void renderItem(ItemStack stack, ItemDisplayContext context, PoseStack poseStack, MultiBufferSource source, int packedLight, int packedOverlay) {}

    @Override
    public @NotNull List<BakedQuad> getQuads(@Nullable BlockState blockState, @Nullable Direction direction, @NotNull RandomSource source) {
        return Collections.emptyList();
    }

    @Override
    @NotNull
    public BakedModel applyTransform(@NotNull ItemDisplayContext context, @NotNull PoseStack poseStack, boolean leftFlip) {
        PerspectiveModelState modelState = getModelState();
        if (modelState != null) {
            Transformation transform = getModelState().getTransform(context);
            Vector3f translation = transform.getTranslation();
            Vector3f scale = transform.getScale();
            poseStack.translate(translation.x(), translation.y(), translation.z());
            poseStack.mulPose(transform.getLeftRotation());
            poseStack.scale(scale.x(), scale.y(), scale.z());
            poseStack.mulPose(transform.getRightRotation());

            if (leftFlip) {
                poseStack.mulPose(Axis.YN.rotationDegrees(180.0f));
            }
            return this;
        }
        return BakedModel.super.applyTransform(context, poseStack, leftFlip);
    }

    @Override public boolean useAmbientOcclusion() {
        return false;
    }

    @Override public boolean isGui3d() {
        return false;
    }
    @Override public boolean usesBlockLight() {
        return false;
    }

    @Override public boolean isCustomRenderer() {
        return true;
    }

    @Override public @NotNull TextureAtlasSprite getParticleIcon() {
        return null;
    }

    @Override public @NotNull ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
