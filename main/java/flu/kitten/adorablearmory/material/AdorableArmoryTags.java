package flu.kitten.adorablearmory.material;

import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class AdorableArmoryTags {
    public static class Blocks {
        public static final TagKey<Block> AdorableArmory_BLOCK_TAG = tag();
        private static TagKey<Block> tag() {
            return BlockTags.create(new ResourceLocation(AdorableArmory.MODID, "block"));
        }
    }
}
