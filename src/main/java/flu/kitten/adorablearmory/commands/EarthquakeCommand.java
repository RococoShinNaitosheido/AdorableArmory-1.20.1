package flu.kitten.adorablearmory.commands;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.entity.boss.abilitymanager.ShakeS2CPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import static flu.kitten.adorablearmory.network.NetworkHandler.CHANNEL;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID)
public final class EarthquakeCommand {

    private static final float DEFAULT_AMPLITUDE = 0.9f;
    private static final int DEFAULT_DURATION_TICKS = 60; // 3s
    private static final float DEFAULT_FREQUENCY = 6.0f;
    private static final float MIN_AMPLITUDE = 0.0f;
    private static final int MIN_DURATION_TICKS = 1;
    private static final float MIN_FREQUENCY = 0.1f;
    private static final float MAX_AMPLITUDE = 12.0f;
    private static final int MAX_DURATION_TICKS = 1200; // 60s
    private static final float MAX_FREQUENCY = 60.0f;

    private EarthquakeCommand() {}

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("adorablearmory")
                        .requires(src -> src.hasPermission(2)) // OP ≥ 2
                        .then(Commands.literal("quake")
                                // 无参-使用默认值
                                .executes(ctx -> quake(ctx.getSource(),
                                        DEFAULT_AMPLITUDE, DEFAULT_DURATION_TICKS, DEFAULT_FREQUENCY))
                                // adorablearmory quake <amplitude> [durationTicks] [frequency]
                                .then(Commands.argument("amplitude", FloatArgumentType.floatArg(MIN_AMPLITUDE, MAX_AMPLITUDE))
                                        .executes(ctx -> quake(ctx.getSource(),
                                                FloatArgumentType.getFloat(ctx, "amplitude"),
                                                DEFAULT_DURATION_TICKS, DEFAULT_FREQUENCY))
                                        .then(Commands.argument("durationTicks", IntegerArgumentType.integer(MIN_DURATION_TICKS, MAX_DURATION_TICKS))
                                                .executes(ctx -> quake(ctx.getSource(),
                                                        FloatArgumentType.getFloat(ctx, "amplitude"),
                                                        IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                                        DEFAULT_FREQUENCY))
                                                .then(Commands.argument("frequency", FloatArgumentType.floatArg(MIN_FREQUENCY, MAX_FREQUENCY))
                                                        .executes(ctx -> quake(ctx.getSource(),
                                                                FloatArgumentType.getFloat(ctx, "amplitude"),
                                                                IntegerArgumentType.getInteger(ctx, "durationTicks"),
                                                                FloatArgumentType.getFloat(ctx, "frequency")))
                                                )
                                        )
                                )
                        )
        );
        // add more register
    }

    private static int quake(CommandSourceStack source, float amplitude, int durationTicks, float frequency) {
        amplitude = Mth.clamp(amplitude, MIN_AMPLITUDE, MAX_AMPLITUDE);
        durationTicks = Mth.clamp(durationTicks, MIN_DURATION_TICKS, MAX_DURATION_TICKS);
        frequency = Mth.clamp(frequency, MIN_FREQUENCY, MAX_FREQUENCY);

        CHANNEL.send(PacketDistributor.ALL.noArg(), new ShakeS2CPacket(amplitude, durationTicks, frequency));

        float finalAmplitude = amplitude;
        int finalDurationTicks = durationTicks;
        float finalFrequency = frequency;
        source.sendSuccess(() -> Component.literal(String.format("Quake triggered: amp=%.2f, duration=%dt, freq=%.2f", finalAmplitude, finalDurationTicks, finalFrequency)
        ), true);
        return 1;
    }
}
