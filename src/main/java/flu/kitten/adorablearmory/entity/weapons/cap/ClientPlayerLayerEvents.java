package flu.kitten.adorablearmory.entity.weapons.cap;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.render.layer.TrueDemonArrowStuckLayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientPlayerLayerEvents {

    @SubscribeEvent
    public static void addLayer(EntityRenderersEvent.AddLayers layers) {
        for (String skin : layers.getSkins()) {
            LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> renderer = layers.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new TrueDemonArrowStuckLayer<>(renderer));
            }
        }
    }
}
