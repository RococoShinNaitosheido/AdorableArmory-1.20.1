package flu.kitten.adorablearmory.client.model;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.mojang.math.Transformation;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.api.client.model.PerspectiveModelState;
import flu.kitten.adorablearmory.client.CosmicRenderProperties;
import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import flu.kitten.adorablearmory.register.ShaderRendererRegistry;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.*;
import java.util.List;

public final class CosmicBakeModel implements BakedModel {
    private static final ItemModelGenerator ITEM_MODEL_GENERATOR = new ItemModelGenerator();
    private static final FaceBakery FACE_BAKERY = new FaceBakery();
    private final List<ResourceLocation> maskSprite;
    private final BakedModel wrapped;
    private final ItemOverrides overrideList;
    private ModelState parentState;
    private LivingEntity entity;
    private ClientLevel world;

    public static boolean isBlockContext(ItemDisplayContext context) {
        return switch (context) {
            case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND, FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND, GROUND, FIXED, GUI -> true;
            default -> false;
        };
    }

    public CosmicBakeModel(final BakedModel wrapped, final List<ResourceLocation> maskSprite) {
        this.overrideList = new ItemOverrides() {
            @Override
            public BakedModel resolve(final @NotNull BakedModel originalModel, final @NotNull ItemStack stack, final ClientLevel world, final LivingEntity entity, final int seed) {
                CosmicBakeModel.this.entity = entity;
                CosmicBakeModel.this.world = (world != null) ? world : (entity == null ? null : (ClientLevel) entity.level());
                return CosmicBakeModel.this.wrapped.getOverrides().resolve(originalModel, stack, world, entity, seed);
            }
        };
        this.wrapped = wrapped;
        this.parentState = TransformUtils.stateFromItemTransforms(wrapped.getTransforms());
        this.maskSprite = maskSprite;
    }

    public void renderItem(ItemStack stack, ItemDisplayContext transformType, PoseStack pStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        CosmicRenderProperties properties = ShaderRendererRegistry.getPropertiesForItem(stack.getItem());
        RenderType renderType = AdorableArmoryShaders.COSMIC_RENDER_TYPE;
        this.parentState = TransformUtils.stateFromItemTransforms(wrapped.getTransforms());
        Minecraft mc = Minecraft.getInstance();

        if (properties != null) {
            this.parentState = properties.modelState();
            renderType = properties.renderType();
        }

        BakedModel model = this.wrapped.getOverrides().resolve(this.wrapped, stack, this.world, this.entity, 0);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        assert model != null;
        for (BakedModel bakedModel : model.getRenderPasses(stack, true)) {
            for (RenderType rendertype : bakedModel.getRenderTypes(stack, true)) {
                itemRenderer.renderModelLists(bakedModel, stack, packedLight, packedOverlay, pStack, buffers.getBuffer(rendertype));
            }
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch();

        float rot = 180.0F;
        float yaw = 0.0F;
        float pitch = 0.0F;
        float camYaw = 0.0F;
        float camPitch = 0.0F;
        float scale = 1.0F;
        boolean isGUIMode = AdorableArmoryShaders.inventoryRender || transformType == ItemDisplayContext.GUI;
        boolean isStarrySkyShader = (renderType == AdorableArmoryShaders.SKY_ITEM);
        boolean isCosmicShader = (renderType == AdorableArmoryShaders.COSMIC_RENDER_TYPE || renderType == AdorableArmoryShaders.COSMIC_BLOCK_RENDER_TYPE);
        boolean overrideStarScale = false;
        float prevSaved = AdorableArmoryShaders.starScaleItemSaved;

        if (mc.player != null) {
            yaw = (float) (mc.player.getYRot() * Math.PI / rot);
            pitch = -(float) (mc.player.getXRot() * Math.PI / rot);
            camYaw = (float) (mc.player.getYRot() * Math.PI / rot);
            camPitch = -(float) (mc.player.getXRot() * Math.PI / rot);
        }

        if (isGUIMode) {
            scale = 100.0F;
            if (mc.player != null) {
                if (isCosmicShader) {
                    yaw = 0.0F;
                    pitch = 0.0F;
                    camYaw = 0.0F;
                    camPitch = 0.0F;
                } else if (isStarrySkyShader) {
                    camYaw = 0.0F;
                    camPitch = 0.0F;
                }
            }
        } else {
            if (mc.player != null) {
                yaw = (float) (mc.player.getYRot() * Math.PI / rot);
                pitch = -(float) (mc.player.getXRot() * Math.PI / rot);
                camYaw = (float) (mc.player.getYRot() * Math.PI / rot);
                camPitch = -(float) (mc.player.getXRot() * Math.PI / rot);
            }
        }

        if (isStarrySkyShader && isGUIMode) {
            renderType = AdorableArmoryShaders.SKY_ITEM_GUI;
            if (AdorableArmoryShaders.starScaleItem != null) {
                AdorableArmoryShaders.starScaleItemSaved = 2.0f;
                overrideStarScale = true;
            }
        }

        AdorableArmoryShaders.cosmicTime.set((System.currentTimeMillis() - AdorableArmoryShaders.renderTime) / 2000.0F);
        AdorableArmoryShaders.cosmicYaw.set(yaw);
        AdorableArmoryShaders.cosmicPitch.set(pitch);

        if (AdorableArmoryShaders.camYaw != null && AdorableArmoryShaders.camPitch != null) {
            AdorableArmoryShaders.camYaw.set(camYaw);
            AdorableArmoryShaders.camPitch.set(camPitch);
        }

        AdorableArmoryShaders.cosmicExternalScale.set(scale);
        AdorableArmoryShaders.cosmicOpacity.set(1.0F);
        for (int i = 0; i < 10; ++i) {
            TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(AdorableArmory.path("item/cosmic_" + i)); //  at assets.adorable-armory.textures.item
            AdorableArmoryShaders.COSMIC_UVS[i * 4] = sprite.getU0();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 1] = sprite.getV0();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 2] = sprite.getU1();
            AdorableArmoryShaders.COSMIC_UVS[i * 4 + 3] = sprite.getV1();
        }

        AdorableArmoryShaders.cosmicUVs.set(AdorableArmoryShaders.COSMIC_UVS);
        VertexConsumer buffersBuffer = buffers.getBuffer(renderType);

        // Block-shaped models(3d GUI)
        if (model.isGui3d() && isBlockContext(transformType)) {
            List<BakedQuad> blockLayer = new ArrayList<>();
            RandomSource random = RandomSource.create();
            for (Direction direction : Direction.values()) {
                blockLayer.addAll(model.getQuads(null, direction, random));
            }

            List<TextureAtlasSprite> maskSprites = new ArrayList<>();
            for (ResourceLocation res : maskSprite) {
                maskSprites.add(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(res));
            }

            List<BakedQuad> overlayQuads = new ArrayList<>();
            for (BakedQuad base : blockLayer) {
                for (TextureAtlasSprite sprite : maskSprites) {
                    overlayQuads.add(textureQuadBestEffort(base, sprite));
                }
            }
            mc.getItemRenderer().renderQuadList(pStack, buffersBuffer, overlayQuads, stack, packedLight, packedOverlay);
        } else {
            // Flat/sprite-layer models
            List<TextureAtlasSprite> atlasSprite = new ArrayList<>();
            for (ResourceLocation res : maskSprite) {
                atlasSprite.add(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(res));
            }

            LinkedList<BakedQuad> quads = new LinkedList<>();
            for (int i = 0; i < atlasSprite.size(); i++) {
                TextureAtlasSprite sprite = atlasSprite.get(i);
                List<BlockElement> unbaked = ITEM_MODEL_GENERATOR.processFrames(i, "layer" + i, sprite.contents());
                for (BlockElement element : unbaked) {
                    for (Map.Entry<Direction, BlockElementFace> entry : element.faces.entrySet()) {
                        quads.add(FACE_BAKERY.bakeQuad(element.from, element.to, entry.getValue(), sprite, entry.getKey(), new PerspectiveModelState(ImmutableMap.of()), element.rotation, element.shade, AdorableArmory.path("dynamic")));
                    }
                }
            }
            mc.getItemRenderer().renderQuadList(pStack, buffersBuffer, quads, stack, packedLight, packedOverlay);
        }

        if (overrideStarScale) {
            AdorableArmoryShaders.starScaleItemSaved = prevSaved;
        }
    }

    private static BakedQuad textureQuadBestEffort(BakedQuad base, TextureAtlasSprite sprite) {
        try {
            // clone vertex data array(defensive)
            int[] vert = base.getVertices().clone();
            return new BakedQuad(vert, base.getTintIndex(), base.getDirection(), sprite, base.isShade());
        } catch (Throwable throwable) {
            AdorableArmory.LOGGER.warn("CosmicBakeModel: unable to texture quad (falling back to base quad)", throwable);
            return base;
        }
    }

    @Override
    public @NotNull BakedModel applyTransform(@NotNull ItemDisplayContext context, @NotNull PoseStack stack, boolean leftFlip) {
        PerspectiveModelState modelState = (PerspectiveModelState) this.parentState;
        if (modelState != null) {
            Transformation transform = ((PerspectiveModelState) this.parentState).getTransform(context);
            Vector3f trans = transform.getTranslation();
            Vector3f scale = transform.getScale();
            stack.translate(trans.x(), trans.y(), trans.z());
            stack.mulPose(transform.getLeftRotation());
            stack.scale(scale.x(), scale.y(), scale.z());
            stack.mulPose(transform.getRightRotation());
            if (leftFlip) {
                stack.mulPose(Axis.YN.rotationDegrees(180.0f));
            }
            return this;
        }
        return BakedModel.super.applyTransform(context, stack, leftFlip);
    }

    @Override
    public @NotNull List<BakedQuad> getQuads(BlockState state, Direction side, @NotNull RandomSource rand) {
        return Collections.emptyList();
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
    public boolean isCustomRenderer() {
        return true;
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
}