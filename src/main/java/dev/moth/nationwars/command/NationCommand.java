package dev.moth.nationwars.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.moth.nationwars.NationCommands;
import dev.moth.nationwars.NationWarsConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;

public final class NationCommand {
    private NationCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("nation").requires(source -> source.hasPermission(0))
            .then(Commands.literal("doctrines").executes(NationCommands::openDoctrinesMenu)
                .then(Commands.literal("list").executes(NationCommands::doctrines)))
            .then(Commands.literal("syncopac").requires(source -> source.hasPermission(2)).executes(NationCommands::syncOpac))
            .then(Commands.literal("create").executes(NationCommands::openNationCreateMenuUnnamed))
            .then(Commands.literal("join").then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::joinNation)))
            .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(NationCommands::inviteNationMember)))
            .then(Commands.literal("accept").then(Commands.argument("nation", StringArgumentType.word()).executes(NationCommands::acceptNationInvitation)))
            .then(Commands.literal("reject").then(Commands.argument("nation", StringArgumentType.word()).executes(NationCommands::rejectNationInvitation)))
            .then(Commands.literal("joinpolicy").then(Commands.argument("policy", StringArgumentType.word()).executes(NationCommands::setNationJoinPolicy)))
            .then(Commands.literal("leave").executes(NationCommands::leaveNation))
            .then(Commands.literal("kick").then(Commands.argument("member", StringArgumentType.word()).executes(NationCommands::kickNationMember)))
            .then(Commands.literal("info").executes(NationCommands::ownNationInfo)
                .then(Commands.argument("name", StringArgumentType.word()).executes(NationCommands::nationInfo)))
            .then(Commands.literal("claim").executes(NationCommands::claim))
            .then(Commands.literal("trade").requires(source -> NationWarsConfig.get().allowTrade)
                .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::openTradeMenu)))
            .then(Commands.literal("upgrade").executes(NationCommands::upgradeNation))
            .then(Commands.literal("unclaim").executes(NationCommands::unclaim))
            .then(Commands.literal("guarantee")
                .then(Commands.literal("remove").then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::removeGuarantee)))
                .then(Commands.argument("country", StringArgumentType.word()).executes(NationCommands::guarantee)))
            .then(Commands.literal("balance").executes(NationCommands::nationBalance))
            .then(Commands.literal("deposit").then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.01)).executes(NationCommands::deposit))));
    }
}
