package flu.kitten.adorablearmory.client.render.buffer;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;

public class AlphaOverrideVertexConsumer extends DelegatingVertexConsumer {

    private final int alpha;

    public AlphaOverrideVertexConsumer(VertexConsumer consumer, double alpha) {
        this(consumer, (int) (255 * alpha));
    }

    public AlphaOverrideVertexConsumer(VertexConsumer consumer, int alpha) {
        super(consumer);
        this.alpha = alpha;
    }

    @Override
    public @NotNull VertexConsumer color(int r, int g, int b, int a) {
        return super.color(r, g, b, alpha);
    }
}
