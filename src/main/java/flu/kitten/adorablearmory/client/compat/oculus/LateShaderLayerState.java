package flu.kitten.adorablearmory.client.compat.oculus;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

public final class LateShaderLayerState {
    public static void prepareMainTargetPass() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        RenderSystem.disableScissor();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }

    public static void finishMainTargetPass() {
        Minecraft.getInstance().getMainRenderTarget().bindWrite(false);
        RenderSystem.disableScissor();
        RenderSystem.depthMask(true);
        RenderSystem.colorMask(true, true, true, true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.defaultBlendFunc();
    }

    private LateShaderLayerState() {}
}
