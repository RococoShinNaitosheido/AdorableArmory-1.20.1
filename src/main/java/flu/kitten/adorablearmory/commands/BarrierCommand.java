package flu.kitten.adorablearmory.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import flu.kitten.adorablearmory.client.render.barrier.BarrierFieldSharedState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public final class BarrierCommand {

    private BarrierCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        var barrierTestCommand = Commands.literal("barrier_test")
                .then(Commands.literal("add_here")
                        .executes(ctx -> {
                            ServerLevel serverLevel = ctx.getSource().getLevel();
                            BlockPos blockPos = BlockPos.containing(ctx.getSource().getPosition());
                            BarrierFieldSharedState.addAndSync(serverLevel, blockPos, 16, 10); // fix this
                            ctx.getSource().sendSuccess(() -> Component.literal("Barrier added @ " + blockPos), true);
                            return 1;
                        }))
                .then(Commands.literal("clear")
                        .executes(ctx -> {
                            ServerLevel lvl = ctx.getSource().getLevel();
                            BarrierFieldSharedState.clearAndSync(lvl);
                            ctx.getSource().sendSuccess(() -> Component.literal("Barriers cleared"), true);
                            return 1;
                        }))
                .then(Commands.literal("sync")
                        .executes(ctx -> {
                            ServerLevel lvl = ctx.getSource().getLevel();
                            BarrierFieldSharedState.syncAll(lvl);
                            ctx.getSource().sendSuccess(() -> Component.literal("Synced"), true);
                            return 1;
                        }));

        return Commands.literal("adorablearmory").then(barrierTestCommand);
    }
}
