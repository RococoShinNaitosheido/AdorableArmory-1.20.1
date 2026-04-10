package flu.kitten.adorablearmory.mixin;

import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.class)
public interface RenderTypeInvoker {
    @Accessor("sortOnUpload")
    boolean sortOnUpload();
}
