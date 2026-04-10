package flu.kitten.adorablearmory.client.render.barrier;

import flu.kitten.adorablearmory.network.NetworkHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.PacketDistributor;

public class NetworkSend {

    private NetworkSend() {}

    public static <MSG> void toAllInDimension(ServerLevel level, MSG msg) {
        NetworkHandler.CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), msg);
    }
}
