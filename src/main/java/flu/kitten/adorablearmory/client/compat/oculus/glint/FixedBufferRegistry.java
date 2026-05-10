package flu.kitten.adorablearmory.client.compat.oculus.glint;

import com.mojang.blaze3d.vertex.BufferBuilder;
import flu.kitten.adorablearmory.mixin.RenderBuffersAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;

public final class FixedBufferRegistry {
    private FixedBufferRegistry() {}
    public static void ensureFixed(RenderType type) {
        var minecraft = Minecraft.getInstance();
        var buffers = minecraft.renderBuffers();
        var fixed = ((RenderBuffersAccessor) buffers).getFixedBuffers();
        fixed.computeIfAbsent(type, t -> new BufferBuilder(t.bufferSize()));
    }
}
