package flu.kitten.adorablearmory.client.compat.oculus.itemoutline;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.function.Function;

public final class ItemOutlineRenderTypes {
    public static RenderType itemMask() {
        return SeedRenderType.ITEM_MASK.apply(InventoryMenu.BLOCK_ATLAS);
    }

    public static @Nullable ShaderInstance compositeShader() {
        return CompositeRender.COMPOSITE_SHADER;
    }

    @Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ClientRegisterShaderEvents {
        @SubscribeEvent
        public static void registerShaders(RegisterShadersEvent event) throws IOException {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(AdorableArmory.MODID, "rendertype_item_outline_mask"), DefaultVertexFormat.NEW_ENTITY), shader -> SeedRenderType.ITEM_OUTLINE_MASK_SHADER = shader);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(AdorableArmory.MODID, "item_outline_composite"), DefaultVertexFormat.POSITION_TEX), shader -> CompositeRender.COMPOSITE_SHADER = shader);
        }
    }

    private static final class SeedRenderType extends RenderType {
        private static ShaderInstance ITEM_OUTLINE_MASK_SHADER;
        private static final ShaderStateShard SHADER_STATE = new ShaderStateShard(() -> ITEM_OUTLINE_MASK_SHADER);
        private static final OutputStateShard OUTLINE_TARGET_STATE = new OutputStateShard(AdorableArmory.MODID + ":item_outline_seed_target", ItemOutlinePostProcessor::bindOutlineTarget, ItemOutlinePostProcessor::restoreMainTarget);
        private static final Function<ResourceLocation, RenderType> ITEM_MASK = Util.memoize(SeedRenderType::createMaskType);

        private SeedRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
            throw new IllegalStateException("No instances");
        }

        private static RenderType createMaskType(ResourceLocation atlas) {
            CompositeState state = CompositeState.builder()
                    .setShaderState(SHADER_STATE)
                    .setTextureState(new TextureStateShard(atlas, false, false))
                    .setTransparencyState(NO_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setDepthTestState(LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setLightmapState(NO_LIGHTMAP)
                    .setOverlayState(NO_OVERLAY)
                    .setLayeringState(NO_LAYERING)
                    .setOutputState(OUTLINE_TARGET_STATE)
                    .createCompositeState(false);
            return create(AdorableArmory.MODID + ":item_outline_mask", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, false, state);
        }
    }

    private static final class CompositeRender {
        private static ShaderInstance COMPOSITE_SHADER;
    }

    private ItemOutlineRenderTypes() {}
}
