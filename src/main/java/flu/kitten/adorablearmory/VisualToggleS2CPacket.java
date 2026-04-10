package flu.kitten.adorablearmory;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record VisualToggleS2CPacket(boolean enabled) {
    public static void encode(VisualToggleS2CPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled());
    }

    public static VisualToggleS2CPacket decode(FriendlyByteBuf buf) {
        return new VisualToggleS2CPacket(buf.readBoolean());
    }

    public static void handle(VisualToggleS2CPacket msg, Supplier<NetworkEvent.Context> ctxSup) {
        var ctx = ctxSup.get();
        ctx.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> VisualToggle.setEffectsEnabled(msg.enabled()))
        );
        ctx.setPacketHandled(true);
    }
}
