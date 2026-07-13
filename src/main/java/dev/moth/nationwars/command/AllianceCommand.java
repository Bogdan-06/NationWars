package dev.moth.nationwars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.moth.nationwars.NationCommands;
import dev.moth.nationwars.NationWarsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class AllianceCommand {
    private AllianceCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("alliance").requires(source -> source.hasPermission(0))
            .then(Commands.literal("create").requires(source -> NationWarsConfig.get().factions).then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceCreate)))
            .then(Commands.literal("invite").requires(source -> NationWarsConfig.get().factions).then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::allianceInvite)))
            .then(Commands.literal("accept").requires(source -> NationWarsConfig.get().factions).then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceAccept)))
            .then(Commands.literal("kick").requires(source -> NationWarsConfig.get().factions).then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::allianceKick)))
            .then(Commands.literal("info").requires(source -> NationWarsConfig.get().factions).executes(NationCommands::allianceInfo)
                .then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::allianceInfoNamed)))
            .then(Commands.literal("truce").executes(NationCommands::truceStatus)
                .then(Commands.literal("offer").then(Commands.argument("country", StringArgumentType.word()).executes(context -> NationCommands.offerTruce(context, false))))
                .then(Commands.literal("renew").then(Commands.argument("country", StringArgumentType.word()).executes(context -> NationCommands.offerTruce(context, true))))
                .then(Commands.literal("accept").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::acceptTruce)))
                .then(Commands.literal("reject").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::rejectTruce)))));
        dispatcher.register(Commands.literal("alliances").requires(source -> source.hasPermission(0) && NationWarsConfig.get().factions).executes(NationCommands::alliances));
    }
}
