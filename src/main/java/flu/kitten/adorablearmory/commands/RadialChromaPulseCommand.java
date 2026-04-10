package flu.kitten.adorablearmory.commands;

import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.client.render.RadialChromaEffect;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RadialChromaPulseCommand {

    @SubscribeEvent
    public static void chroma(RegisterClientCommandsEvent event) {
        var chromaCommand = Commands.literal("chroma")
                .executes(ctx -> {
                    RadialChromaEffect.trigger();
                    ctx.getSource().sendSuccess(() -> Component.literal("ChromaEffect: " + RadialChromaEffect.debugState()), false);
                    return 1;
                });
        event.getDispatcher().register(Commands.literal("adorablearmory").then(chromaCommand));
    }
}
