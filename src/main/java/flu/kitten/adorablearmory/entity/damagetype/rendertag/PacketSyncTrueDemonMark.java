package flu.kitten.adorablearmory.entity.damagetype.rendertag;

import flu.kitten.adorablearmory.entity.damagetype.Capabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;

import java.util.Objects;
import java.util.function.Supplier;

public class PacketSyncTrueDemonMark {
    private final int entityId;
    private final int remainingDuration;
    private final boolean hasEffect;

    public PacketSyncTrueDemonMark(int entityId, int remainingDuration, boolean hasEffect) {
        this.entityId = entityId;
        this.remainingDuration = remainingDuration;
        this.hasEffect = hasEffect;
    }

    public PacketSyncTrueDemonMark(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.remainingDuration = buf.readInt();
        this.hasEffect = buf.readBoolean();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeInt(remainingDuration);
        buf.writeBoolean(hasEffect);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getDirection().getReceptionSide().isClient()) {
                handleClient(this);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleClient(PacketSyncTrueDemonMark msg) {
        Entity entity = Objects.requireNonNull(Minecraft.getInstance().level).getEntity(msg.entityId);
        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.getCapability(Capabilities.TRUE_DEMON_EFFECT).ifPresent(cap -> {
                cap.setEffect(msg.hasEffect);
                cap.setRemainingDuration(msg.remainingDuration);
            });
        }
    }
}
