package flu.kitten.adorablearmory.client.model;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.api.shader.ShaderLayerItem;
import flu.kitten.adorablearmory.api.shader.ShaderLayerProperties;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ShaderLayerModelEvents {

    @SubscribeEvent
    public static void wrapShaderLayerModels(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (Item item : ForgeRegistries.ITEMS) {
            if (!(item instanceof ShaderLayerItem shaderLayerItem)) {
                continue;
            }

            ResourceLocation itemKey = ForgeRegistries.ITEMS.getKey(item);
            if (itemKey == null) {
                continue;
            }

            ShaderLayerProperties properties = shaderLayerItem.getShaderLayer(new ItemStack(item));
            if (properties == null) {
                continue;
            }

            ModelResourceLocation modelLocation = new ModelResourceLocation(itemKey, "inventory");
            BakedModel model = models.get(modelLocation);
            if (model == null || model instanceof CosmicBakeModel) {
                continue;
            }

            models.put(modelLocation, new CosmicBakeModel(model, properties.maskTextures()));
        }
    }

    private ShaderLayerModelEvents() {}
}
