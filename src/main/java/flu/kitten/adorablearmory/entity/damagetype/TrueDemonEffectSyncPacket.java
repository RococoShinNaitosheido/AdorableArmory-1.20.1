package flu.kitten.adorablearmory.entity.damagetype;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TrueDemonEffectSyncPacket {
    private final int entityId;
    private final boolean hasEffect;
    private final int remainingDuration;
    private final boolean blocksHealing;
    private final int healingBlockDuration;

    public TrueDemonEffectSyncPacket(int entityId, boolean hasEffect, int duration, boolean blocksHealing, int healingBlockDuration) {
        this.entityId = entityId;
        this.hasEffect = hasEffect;
        this.remainingDuration = duration;
        this.blocksHealing = blocksHealing;
        this.healingBlockDuration = healingBlockDuration;
    }

    public TrueDemonEffectSyncPacket(int entityId, boolean hasEffect, int duration) {
        this(entityId, hasEffect, duration, hasEffect, duration);
    }

    public static void encode(TrueDemonEffectSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeBoolean(packet.hasEffect);
        buffer.writeVarInt(packet.remainingDuration);
        buffer.writeBoolean(packet.blocksHealing);
        buffer.writeVarInt(packet.healingBlockDuration);
    }

    public static TrueDemonEffectSyncPacket decode(FriendlyByteBuf buffer) {
        int id = buffer.readVarInt();
        boolean has = buffer.readBoolean();
        int dur = buffer.readVarInt();
        boolean block = buffer.readBoolean();
        int healDur = buffer.readVarInt();
        return new TrueDemonEffectSyncPacket(id, has, dur, block, healDur);
    }

    public static void handle(TrueDemonEffectSyncPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Level level = Minecraft.getInstance().level;
            if (level != null) {
                Entity entity = level.getEntity(packet.entityId);
                if (entity instanceof LivingEntity livingEntity) {
                    livingEntity.getCapability(Capabilities.TRUE_DEMON_EFFECT)
                            .ifPresent(cap -> {
                                cap.setEffect(packet.hasEffect);
                                cap.setRemainingDuration(packet.remainingDuration);
                                cap.setBlocksHealing(packet.blocksHealing);
                                cap.setHealingBlockDuration(packet.healingBlockDuration);
                            });
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
