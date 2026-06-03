package flu.kitten.adorablearmory.client.render.dimensional;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;

final class ReusableBufferBuilder {
    private final int initialSize;
    private BufferBuilder builder;

    ReusableBufferBuilder(int initialSize) {
        this.initialSize = initialSize;
    }

    BufferBuilder begin(VertexFormat.Mode mode, VertexFormat format) {
        if (builder == null || builder.building()) {
            builder = new BufferBuilder(initialSize);
        }
        builder.begin(mode, format);
        return builder;
    }

    static void drawWithShaderOrDiscard(BufferBuilder builder) {
        BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
        if (rendered != null) {
            BufferUploader.drawWithShader(rendered);
        }
    }
}
