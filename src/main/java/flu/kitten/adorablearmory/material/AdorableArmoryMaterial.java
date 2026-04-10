package flu.kitten.adorablearmory.material;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.ForgeTier;
import net.minecraftforge.common.TierSortingRegistry;

import java.util.List;

public class AdorableArmoryMaterial {
    final static int level = 0;
    final static int maxUses = Integer.MAX_VALUE;
    final static float speed = 1.4f;
    final static float attackDamage = 98 + 1000;
    final static int enchant = 25;
    public static final Tier ENDING_LOVE_TIER = TierSortingRegistry.registerTier(
            new ForgeTier(level, maxUses, speed, attackDamage, enchant, AdorableArmoryTags.Blocks.AdorableArmory_BLOCK_TAG, () -> Ingredient.of(Items.NETHERITE_INGOT)),
            new ResourceLocation(AdorableArmory.MODID,"textures"),
            List.of(Tiers.DIAMOND),
            List.of()
    );
}
