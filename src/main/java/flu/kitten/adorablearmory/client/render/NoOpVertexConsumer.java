package flu.kitten.adorablearmory.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;

public final class NoOpVertexConsumer implements VertexConsumer {
    public static final NoOpVertexConsumer INSTANCE = new NoOpVertexConsumer();

    private NoOpVertexConsumer() {}

    @Override
    public @NotNull VertexConsumer vertex(double x, double y, double z) {
        return this;
    }

    @Override
    public @NotNull VertexConsumer color(int red, int green, int blue, int alpha) {
        return this;
    }

    @Override
    public @NotNull VertexConsumer uv(float u, float v) {
        return this;
    }

    @Override
    public @NotNull VertexConsumer overlayCoords(int u, int v) {
        return this;
    }

    @Override
    public @NotNull VertexConsumer uv2(int u, int v) {
        return this;
    }

    @Override
    public @NotNull VertexConsumer normal(float x, float y, float z) {
        return this;
    }

    @Override
    public void endVertex() {
    }

    @Override
    public void defaultColor(int red, int green, int blue, int alpha) {
    }

    @Override
    public void unsetDefaultColor() {
    }
}
