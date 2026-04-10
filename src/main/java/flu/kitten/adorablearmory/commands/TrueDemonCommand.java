package flu.kitten.adorablearmory.commands;

import com.mojang.brigadier.CommandDispatcher;
import flu.kitten.adorablearmory.AdorableArmory;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonDamageSource;
import flu.kitten.adorablearmory.entity.damagetype.TrueDemonTypes;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.List;

@Mod.EventBusSubscriber(modid = AdorableArmory.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrueDemonCommand {

    private TrueDemonCommand() {}

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("adorablearmory")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("true_demon_damage_test")
                                .executes(context -> executeSelf(context.getSource()))
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> executeTargets(
                                                context.getSource(),
                                                EntityArgument.getEntities(context, "targets")
                                        ))
                                )
                        )
        );
    }

    private static int executeSelf(CommandSourceStack source) {
        Entity entity = source.getEntity();

        if (!(entity instanceof LivingEntity living)) {
            source.sendFailure(Component.literal("This form requires a living entity executor. Use /adorablearmory true_demon_damage_test <targets> instead."));
            return 0;
        }

        return executeOnLivingTargets(source, List.of(living));
    }

    private static int executeTargets(CommandSourceStack source, Collection<? extends Entity> targets) {
        return executeOnTargets(source, targets);
    }

    private static int executeOnTargets(CommandSourceStack source, Collection<? extends Entity> targets) {
        int affected = 0;
        int skipped = 0;

        for (Entity entity : targets) {
            if (entity instanceof LivingEntity living) {
                TrueDemonDamageSource.armDeathGuard(living);
                TrueDemonTypes.TrueDemonDamageUtil.trueDemonMechanismKill(living, null);
                affected++;
            } else {
                skipped++;
            }
        }

        if (affected <= 0) {
            source.sendFailure(Component.literal("No living targets were affected." + (skipped > 0 ? " Skipped " + skipped + " non-living target(s)." : "")));
            return 0;
        }

        final int finalAffected = affected;
        final int finalSkipped = skipped;

        source.sendSuccess(() -> Component.literal("Triggered True Demon damage test on " + finalAffected + " living target(s)" + (finalSkipped > 0 ? ", skipped " + finalSkipped + " non-living target(s)." : ".")), true);

        return finalAffected;
    }

    private static int executeOnLivingTargets(CommandSourceStack source, Collection<? extends LivingEntity> targets) {
        int affected = 0;

        for (LivingEntity living : targets) {
            TrueDemonDamageSource.armDeathGuard(living);
            TrueDemonTypes.TrueDemonDamageUtil.trueDemonMechanismKill(living, null);
            affected++;
        }

        if (affected <= 0) {
            source.sendFailure(Component.literal("No living targets were affected."));
            return 0;
        }

        final int finalAffected = affected;
        source.sendSuccess(() -> Component.literal("Triggered True Demon damage test on " + finalAffected + " living target(s)."), true);
        return finalAffected;
    }
}
