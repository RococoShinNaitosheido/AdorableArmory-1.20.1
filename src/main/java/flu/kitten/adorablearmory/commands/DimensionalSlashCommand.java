package flu.kitten.adorablearmory.commands;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.network.DimensionalSlashS2CPacket;
import flu.kitten.adorablearmory.network.NetworkHandler;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import static flu.kitten.adorablearmory.client.render.dimensional.config.DimensionalSlashTuning.WorldSlash.*;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DimensionalSlashCommand {
    private DimensionalSlashCommand() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("adorablearmory")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("dimensional_slash")
                                .executes(ctx -> trigger(ctx.getSource(), DEFAULT_SLASHES, DEFAULT_LENGTH, DEFAULT_RADIUS))
                                .then(Commands.argument("slashes", IntegerArgumentType.integer(MIN_SLASHES, MAX_SLASHES))
                                        .executes(ctx -> trigger(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "slashes"),
                                                DEFAULT_LENGTH,
                                                DEFAULT_RADIUS))
                                        .then(Commands.argument("length", FloatArgumentType.floatArg(MIN_LENGTH, MAX_LENGTH))
                                                .executes(ctx -> trigger(ctx.getSource(),
                                                        IntegerArgumentType.getInteger(ctx, "slashes"),
                                                        FloatArgumentType.getFloat(ctx, "length"),
                                                        DEFAULT_RADIUS))
                                                .then(Commands.argument("radius", FloatArgumentType.floatArg(MIN_RADIUS, MAX_RADIUS))
                                                        .executes(ctx -> trigger(ctx.getSource(),
                                                                IntegerArgumentType.getInteger(ctx, "slashes"),
                                                                FloatArgumentType.getFloat(ctx, "length"),
                                                                FloatArgumentType.getFloat(ctx, "radius"))))
                                        )
                                )
                        )
        );
    }

    private static int trigger(CommandSourceStack source, int slashCount, float length, float radius) {
        Entity entity = source.getEntity();
        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only a player can trigger a player-centered Dimensional Slash."));
            return 0;
        }

        if (NetworkHandler.CHANNEL == null) {
            source.sendFailure(Component.literal("Dimensional Slash network channel is not ready."));
            return 0;
        }

        int safeSlashCount = Mth.clamp(slashCount, MIN_SLASHES, MAX_SLASHES);
        float safeLength = Mth.clamp(length, MIN_LENGTH, MAX_LENGTH);
        float safeRadius = Mth.clamp(radius, MIN_RADIUS, MAX_RADIUS);
        double x = player.getX();
        double y = player.getY() + player.getBbHeight() * 0.58;
        double z = player.getZ();
        long seed = player.level().getGameTime() ^ player.getUUID().getMostSignificantBits() ^ System.nanoTime();

        NetworkHandler.CHANNEL.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new DimensionalSlashS2CPacket(x, y, z, safeSlashCount, safeLength, safeRadius, seed)
        );

        source.sendSuccess(() -> Component.literal(String.format("Dimensional Slash triggered: slashes=%d, length=%.1f, radius=%.1f", safeSlashCount, safeLength, safeRadius)), true);
        return 1;
    }
}
