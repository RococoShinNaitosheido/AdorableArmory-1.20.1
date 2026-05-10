package flu.kitten.adorablearmory.mixin;

import flu.kitten.adorablearmory.client.compat.oculus.itemoutline.ItemOutlinePostProcessor;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenItemOutlineMixin {

    @Inject(method = "renderEntityInInventory", at = @At("HEAD"))
    private static void beginItemOutlineGuiEntityPreview(GuiGraphics guiGraphics, int x, int y, int scale, Quaternionf poseRotation, @Nullable Quaternionf cameraRotation, LivingEntity entity, CallbackInfo ci) {
        ItemOutlinePostProcessor.beginGuiEntityPreview();
    }

    @Inject(method = "renderEntityInInventory", at = @At("RETURN"))
    private static void endItemOutlineGuiEntityPreview(GuiGraphics guiGraphics, int x, int y, int scale, Quaternionf poseRotation, @Nullable Quaternionf cameraRotation, LivingEntity entity, CallbackInfo ci) {
        ItemOutlinePostProcessor.endGuiEntityPreview();
    }
}
