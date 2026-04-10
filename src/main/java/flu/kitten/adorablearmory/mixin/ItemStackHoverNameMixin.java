package flu.kitten.adorablearmory.mixin;

import flu.kitten.adorablearmory.item.TrueDemonArrowItem;
import flu.kitten.adorablearmory.item.tool.TrueDemonBowItem;
import flu.kitten.adorablearmory.util.TrueDemonBowNameEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class ItemStackHoverNameMixin {

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void trueDemonBowAnimatedName(CallbackInfoReturnable<Component> cir) {
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() instanceof TrueDemonBowItem || stack.getItem() instanceof TrueDemonArrowItem) {
            if (stack.hasCustomHoverName()) return;
            // 取原始翻译后的纯文本
            String string = stack.getItem().getName(stack).getString();
            cir.setReturnValue(TrueDemonBowNameEffects.violetMagentaSweep(string));
        }
    }
}
