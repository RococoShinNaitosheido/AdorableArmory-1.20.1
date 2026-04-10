package flu.kitten.adorablearmory.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import flu.kitten.adorablearmory.entity.weapons.TrueDemonArrow;
import flu.kitten.adorablearmory.entity.weapons.cap.DemonArrowStuckProvider;
import flu.kitten.adorablearmory.entity.weapons.cap.IDemonArrowStuckCap;
import flu.kitten.adorablearmory.register.AdorableArmoryRegister;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class TrueDemonArrowStuckLayer<T extends LivingEntity, M extends PlayerModel<T>> extends StuckInBodyLayer<T, M> {

    public TrueDemonArrowStuckLayer(LivingEntityRenderer<T, M> renderer) {
        super(renderer);
    }

    @Override
    protected int numStuck(@NotNull T entity) {
        return entity.getCapability(DemonArrowStuckProvider.CAPABILITY).map(IDemonArrowStuckCap::getCount).orElse(0);
    }

    @Override
    protected void renderStuckItem(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, @NotNull Entity host, float x, float y, float z, float partialTicks) {
        Level level = host.level();

        TrueDemonArrow arrow = new TrueDemonArrow(AdorableArmoryRegister.TRUE_DEMON_ARROW_ENTITY.get(), level);
        arrow.setNoGravity(true);
        arrow.setDeltaMovement(x, y, z);

        float yaw = (float)(Mth.atan2(x, z) * (180f / Math.PI));
        float pitch = (float)(Mth.atan2(y, Mth.sqrt(x * x + z * z)) * (180f / Math.PI));

        arrow.setYRot(yaw);
        arrow.setXRot(pitch);
        arrow.yRotO = yaw;
        arrow.xRotO = pitch;

        @SuppressWarnings("unchecked")
        EntityRenderer<TrueDemonArrow> renderer = (EntityRenderer<TrueDemonArrow>) Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(arrow);
        renderer.render(arrow, yaw, partialTicks, poseStack, buffer, packedLight);
    }
}
