package flu.kitten.adorablearmory.client.compat.oculus.glint;

import com.mojang.blaze3d.systems.RenderSystem;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.compat.oculus.ItemShaderModCompat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class TrueDemonGlintRenderType extends RenderType {
    private static final float DEFAULT_COLOR_OVERLAY = 1.24f;
    private static final float OCULUS_SHADERPACK_COLOR_OVERLAY = 1.82f;

    private TrueDemonGlintRenderType(String name, RenderType type, int argb, boolean sortOnUpload, float glintAlpha, float uvScale, float scrollMulU, float scrollMulV) {
        super(name, type.format(), type.mode(), type.bufferSize(), type.affectsCrumbling(), sortOnUpload,
                () -> {
                    type.setupRenderState();
                    RenderSystem.setShaderTexture(0, new ResourceLocation(AdorableArmory.MODID, "textures/misc/true_demon_glint.png"));
                    float a = ((argb >>> 24) & 0xFF) / 255f;
                    float r = ((argb >>> 16) & 0xFF) / 255f;
                    float g = ((argb >>> 8) & 0xFF) / 255f;
                    float b = (argb & 0xFF) / 255f;
                    float colorOverlay = ItemShaderModCompat.isOculusShaderPackActive() ? OCULUS_SHADERPACK_COLOR_OVERLAY : DEFAULT_COLOR_OVERLAY;
                    RenderSystem.setShaderColor(r * colorOverlay, g * colorOverlay, b * colorOverlay, a);
                    RenderSystem.setShaderGlintAlpha(glintAlpha);
                    applyExtraUvScale(uvScale);
                    applyScrollSpeed(scrollMulU, scrollMulV);
                },
                () -> {
                    RenderSystem.setTextureMatrix(new Matrix4f());
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    RenderSystem.setShaderGlintAlpha(1f);
                    type.clearRenderState();
                }
        );
    }

    private static void applyExtraUvScale(float uvScale) {
        if (uvScale == 1f) return;
        Matrix4f base = RenderSystem.getTextureMatrix();
        Matrix4f matrix4f = new Matrix4f(base);
        matrix4f.m00(matrix4f.m00() * uvScale);
        matrix4f.m01(matrix4f.m01() * uvScale);
        matrix4f.m10(matrix4f.m10() * uvScale);
        matrix4f.m11(matrix4f.m11() * uvScale);
        RenderSystem.setTextureMatrix(matrix4f);
    }

    private static void applyScrollSpeed(float mulU, float mulV) {
        if (mulU == 1f && mulV == 1f) return;
        Matrix4f matrix4f = new Matrix4f(RenderSystem.getTextureMatrix());
        float u = matrix4f.m30();
        float v = matrix4f.m31();
        u = fact(u * mulU);
        v = fact(v * mulV);
        matrix4f.m30(u);
        matrix4f.m31(v);
        RenderSystem.setTextureMatrix(matrix4f);
    }

    private static float fact(float x) {
        return x - (float)Math.floor(x);
    }

    public static RenderType wrap(RenderType base, int argb, String namePrefix, boolean sortOnUpload) {
        String name = namePrefix + "_" + Integer.toHexString(argb);
        RenderType type = new TrueDemonGlintRenderType(name, base, argb, sortOnUpload, 1.0f, 2.32f, 2, 2);
        FixedBufferRegistry.ensureFixed(type);
        return type;
    }
}
