package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;

public final class ColoredGlintVertexConsumer implements VertexConsumer {
    private final VertexConsumer delegate;
    private final int tintA;
    private final int tintR;
    private final int tintG;
    private final int tintB;

    public ColoredGlintVertexConsumer(VertexConsumer delegate, int argb) {
        this.delegate = delegate;
        this.tintA = (argb >>> 24) & 0xFF;
        this.tintR = (argb >>> 16) & 0xFF;
        this.tintG = (argb >>> 8) & 0xFF;
        this.tintB = argb & 0xFF;
    }

    private static int mul255(int base, int tint) {
        return (base * tint + 127) / 255;
    }

    @Override
    public @NotNull VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        return this;
    }

    @Override
    public @NotNull VertexConsumer color(int red, int green, int blue, int alpha) {
        delegate.color(mul255(red, tintR), mul255(green, tintG), mul255(blue, tintB), mul255(alpha, tintA));
        return this;
    }

    @Override
    public @NotNull VertexConsumer uv(float u, float v) {
        delegate.uv(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer overlayCoords(int u, int v) {
        delegate.overlayCoords(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer uv2(int u, int v) {
        delegate.uv2(u, v);
        return this;
    }

    @Override
    public @NotNull VertexConsumer normal(float x, float y, float z) {
        delegate.normal(x, y, z);
        return this;
    }

    @Override
    public void endVertex() {
        delegate.endVertex();
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
        delegate.defaultColor(mul255(red, tintR), mul255(green, tintG), mul255(blue, tintB), mul255(alpha, tintA));
    }

    @Override
    public void unsetDefaultColor() {
        delegate.unsetDefaultColor();
    }
}