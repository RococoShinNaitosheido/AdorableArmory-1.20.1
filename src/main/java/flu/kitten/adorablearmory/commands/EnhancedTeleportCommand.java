package flu.kitten.adorablearmory.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class EnhancedTeleportCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var rtpCommand = Commands.literal("rtp")
                .requires(source -> source.hasPermission(2)) // OP level 2 required
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(EnhancedTeleportCommand::teleportSelf)
                                        .then(Commands.argument("targets", EntityArgument.players())
                                                .executes(EnhancedTeleportCommand::teleportTargets)))));

        dispatcher.register(Commands.literal("adorablearmory").then(rtpCommand)
        );
    }

    private static int teleportSelf(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Entity entity = source.getEntityOrException();

        if (!(entity instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Only players can be teleported"));
            return 0;
        }

        double x = DoubleArgumentType.getDouble(context, "x");
        double y = DoubleArgumentType.getDouble(context, "y");
        double z = DoubleArgumentType.getDouble(context, "z");
        return teleportPlayer(source, player, x, y, z);
    }

    private static int teleportTargets(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");

        double x = DoubleArgumentType.getDouble(context, "x");
        double y = DoubleArgumentType.getDouble(context, "y");
        double z = DoubleArgumentType.getDouble(context, "z");

        int successCount = 0;

        for (ServerPlayer player : targets) {
            if (teleportPlayer(source, player, x, y, z) > 0) {
                successCount++;
            }
        }

        if (successCount > 0) {
            if (targets.size() == 1) {
                source.sendSuccess(() -> Component.literal("Teleported " + targets.iterator().next().getDisplayName().getString() + " to " + formatCoordinate(x) + ", " + formatCoordinate(y) + ", " + formatCoordinate(z)), true);
            } else {
                int finalSuccessCount = successCount;
                source.sendSuccess(() -> Component.literal("Teleported " + finalSuccessCount + " players to " + formatCoordinate(x) + ", " + formatCoordinate(y) + ", " + formatCoordinate(z)), true);
            }
        }

        return successCount;
    }

    private static int teleportPlayer(CommandSourceStack source, ServerPlayer player, double x, double y, double z) {
        try {
            if (isValidCoordinate(x) || isValidCoordinate(y) || isValidCoordinate(z)) {
                source.sendFailure(Component.literal("Coordinates are too extreme or invalid!"));
                return 0;
            }

            player.teleportTo(x, y, z);

            if (source.getEntity() == player) {
                source.sendSuccess(() -> Component.literal("Teleported to " +
                        formatCoordinate(x) + ", " + formatCoordinate(y) + ", " + formatCoordinate(z)), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Failed to teleport: " + e.getMessage()));
            return 0;
        }
    }

    private static boolean isValidCoordinate(double coordinate) {
        return Double.isNaN(coordinate) || Double.isInfinite(coordinate) || !(coordinate >= -Double.MAX_VALUE) || !(coordinate <= Double.MAX_VALUE);
    }

    private static String formatCoordinate(double coordinate) {
        if (coordinate == Math.floor(coordinate) && !Double.isInfinite(coordinate)) {
            return String.format("%.0f", coordinate);
        } else {
            return String.format("%.2f", coordinate);
        }
    }
}
