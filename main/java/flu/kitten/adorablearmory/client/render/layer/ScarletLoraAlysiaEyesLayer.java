package flu.kitten.adorablearmory.client.render.layer;

import flu.kitten.adorablearmory.client.shader.AdorableArmoryShaders;
import net.minecraft.client.model.PhantomModel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.monster.Phantom;
import org.jetbrains.annotations.NotNull;

import static flu.kitten.adorablearmory.AdorableArmory.MODID;

public class ScarletLoraAlysiaEyesLayer<T extends Phantom> extends EyesLayer<T, PhantomModel<T>> {

    // private static final RenderType EYES = AdorableArmoryShaders.eyes(new ResourceLocation(MODID,"textures/entity/scarlet_lora_alysia_eyes.png"));

    public ScarletLoraAlysiaEyesLayer(RenderLayerParent<T, PhantomModel<T>> parent) {
        super(parent);
    }

    @Override
    public @NotNull RenderType renderType() {
        return null;
    }
}
