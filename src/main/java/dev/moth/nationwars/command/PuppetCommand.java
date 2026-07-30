package dev.moth.nationwars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.moth.nationwars.NationStore;
import dev.moth.nationwars.PuppetCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;

/** Registers the player-facing puppet-management command tree. */
public final class PuppetCommand {
    private PuppetCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("puppet")
            .requires(source -> source.hasPermission(0))
            .executes(PuppetCommands::status)
            .then(countryAction("propose", PuppetCommands::propose))
            .then(countryAction("accept", PuppetCommands::accept))
            .then(countryAction("reject", PuppetCommands::reject))
            .then(countryAction("pacify", PuppetCommands::pacify))
            .then(Commands.literal("agitate").executes(PuppetCommands::agitate))
            .then(Commands.literal("liberate").executes(PuppetCommands::liberate))
            .then(Commands.literal("automate").executes(PuppetCommands::liberate))
            .then(countryAction("release", PuppetCommands::release))
            .then(countryAction("annex", PuppetCommands::annex))
            .then(Commands.literal("war").executes(PuppetCommands::war)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> countryAction(
            String action, Command<CommandSourceStack> handler) {
        return Commands.literal(action)
            .then(Commands.argument("country", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    NationStore.get().nationsSorted().stream().map(nation -> nation.name), builder))
                .executes(handler));
    }
}
