package dev.moth.nationwars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.moth.nationwars.NationCommands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public final class MarketCommand {
    private MarketCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("money").requires(source -> source.hasPermission(0)).executes(NationCommands::money));
        dispatcher.register(Commands.literal("openpac-parties").requires(source -> source.hasPermission(0)).executes(NationCommands::blockedOpenPacParties));
        dispatcher.register(Commands.literal("market").requires(source -> source.hasPermission(0))
            .executes(NationCommands::openMarket)
            .then(Commands.literal("sellhand")
                .executes(context -> NationCommands.sellHand(context, -1.0))
                .then(Commands.argument("price", DoubleArgumentType.doubleArg(0.01))
                    .executes(context -> NationCommands.sellHand(context, DoubleArgumentType.getDouble(context, "price")))))
            .then(Commands.literal("cancel")
                .then(Commands.argument("id", IntegerArgumentType.integer(1)).executes(NationCommands::cancelListing))));
    }
}
