package dev.moth.nationwars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.moth.nationwars.NationCommands;
import dev.moth.nationwars.NationWarsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class WarCommand {
    private WarCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("wars").requires(source -> source.hasPermission(0)).executes(NationCommands::openWarMenu));
        dispatcher.register(Commands.literal("war").requires(source -> source.hasPermission(0))
            .executes(NationCommands::openWarMenu)
            .then(Commands.literal("justify").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::justifyWar)))
            .then(Commands.literal("declare").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::declareWar)))
            .then(Commands.literal("accept").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptWarDeclaration)))
            .then(Commands.literal("reject").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectWarDeclaration)))
            .then(Commands.literal("join").then(Commands.argument("country", StringArgumentType.word())
                .executes(NationCommands::requestWarJoin)
                .then(Commands.argument("enemy", StringArgumentType.word()).executes(NationCommands::requestWarJoin))))
            .then(Commands.literal("acceptjoin").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptWarJoin)))
            .then(Commands.literal("rejectjoin").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectWarJoin)))
            .then(Commands.literal("defend").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptAllianceDefense)))
            .then(Commands.literal("declinedefense").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::declineAllianceDefense)))
            .then(Commands.literal("leave").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::leaveWar)))
            .then(Commands.literal("status").executes(NationCommands::warStatus)));
        dispatcher.register(Commands.literal("peace").requires(source -> source.hasPermission(0) && !NationWarsConfig.get().noMercy)
            .then(Commands.literal("reject").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectPeace)))
            .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::peace)));
        dispatcher.register(Commands.literal("surrender").requires(source -> source.hasPermission(0))
            .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::surrender)));
    }
}
