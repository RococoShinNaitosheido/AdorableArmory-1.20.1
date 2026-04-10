package flu.kitten.adorablearmory.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import flu.kitten.adorablearmory.AdorableArmory;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RemoveEntitiesCommand {

    private RemoveEntitiesCommand() {}

    @SubscribeEvent
    @SuppressWarnings("unused")
    public static void removeCommand(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        LiteralArgumentBuilder<CommandSourceStack> removeEntitiesCommand = literal("removeentities").requires(src -> src.hasPermission(4));

        // default: 不删玩家
        removeEntitiesCommand.then(argument("targets", EntityArgument.entities()).executes(ctx -> purge(ctx, /*includePlayers=*/false, /*dryRun=*/false)));

        // dry run
        removeEntitiesCommand.then(literal("dry").then(argument("targets", EntityArgument.entities()).executes(ctx -> purge(ctx, /*includePlayers=*/false, /*dryRun=*/true))));

        // force: 包含玩家
        removeEntitiesCommand.then(literal("force").then(argument("targets", EntityArgument.entities()).executes(ctx -> purge(ctx, /*includePlayers=*/true, /*dryRun=*/false))));

        dispatcher.register(literal("adorablearmory").then(removeEntitiesCommand));
    }

    private static int purge(CommandContext<CommandSourceStack> context, boolean includePlayers, boolean dryRun) {
        Collection<? extends Entity> targets;
        try {
            targets = EntityArgument.getEntities(context, "targets");
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("无法解析 targets: " + e.getMessage()));
            return 0;
        }

        int total = 0;
        int skippedPlayers = 0;

        for (Entity entity : targets) {
            if (!includePlayers && entity instanceof Player) {
                skippedPlayers++;
                continue;
            }
            if (dryRun) {
                total++;
                continue;
            }

            entity.remove(Entity.RemovalReason.DISCARDED); // void
            if (entity.isRemoved()) total++;
        }

        // 回显
        if (dryRun) {
            int finalTotal = total;
            int finalSkippedPlayers = skippedPlayers;
            context.getSource().sendSuccess(() -> Component.literal("DRY RUN：将要删除 " + finalTotal + " 个实体(已跳过玩家 " + finalSkippedPlayers + ")"), true);
        } else {
            int finalTotal1 = total;
            int finalSkippedPlayers1 = skippedPlayers;
            context.getSource().sendSuccess(() -> Component.literal("已删除 " + finalTotal1 + " 个实体(跳过玩家 " + finalSkippedPlayers1 + ")"), true);
        }
        return total;
    }
}
