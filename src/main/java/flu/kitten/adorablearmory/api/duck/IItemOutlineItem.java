package flu.kitten.adorablearmory.api.duck;

import flu.kitten.adorablearmory.client.itemoutline.ItemOutlineData;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public interface IItemOutlineItem {
    @Nullable ItemOutlineData getItemOutline(ItemStack stack, ItemDisplayContext context); // 返回 null = 不渲染描边
}
