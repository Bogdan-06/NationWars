package dev.moth.nationwars;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class DevCommands {
    private DevCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(command("nwdev"));
        dispatcher.register(command("nationwarsdev"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> command(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2))
            .executes(DevCommands::help)
            .then(Commands.literal("money")
                .then(Commands.argument("player", StringArgumentType.word())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0)).executes(DevCommands::setMoney))))
            .then(Commands.literal("treasury")
                .then(Commands.argument("country", StringArgumentType.word())
                    .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0)).executes(DevCommands::setTreasury))))
            .then(Commands.literal("doctrine")
                .then(Commands.argument("country", StringArgumentType.word())
                    .then(Commands.argument("id", StringArgumentType.word()).executes(DevCommands::setDoctrine))))
            .then(Commands.literal("finishspies").executes(DevCommands::finishSpies))
            .then(Commands.literal("save").executes(DevCommands::save))
            .then(Commands.literal("syncopac").executes(DevCommands::syncOpac));
    }

    private static int help(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal("Nation Wars development commands:"), false);
        context.getSource().sendSuccess(() -> Component.literal("/nwdev money <online-player> <amount>"), false);
        context.getSource().sendSuccess(() -> Component.literal("/nwdev treasury <country> <amount>"), false);
        context.getSource().sendSuccess(() -> Component.literal("/nwdev doctrine <country> <GER|FRA|SOV|ENG|USA|ITA|ROM>"), false);
        context.getSource().sendSuccess(() -> Component.literal("/nwdev finishspies | save | syncopac"), false);
        return 1;
    }

    private static int setMoney(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "player");
        ServerPlayer player = context.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (player == null) {
            context.getSource().sendFailure(Component.literal("That player must be online."));
            return 0;
        }
        NationStore store = NationStore.get();
        double amount = DoubleArgumentType.getDouble(context, "amount");
        store.addPlayerMoney(player.getUUID(), amount - store.playerBalance(player.getUUID()));
        context.getSource().sendSuccess(() -> Component.literal("Set " + player.getGameProfile().getName() + "'s money to $" + NationStore.roundMoney(amount) + "."), true);
        return 1;
    }

    private static int setTreasury(CommandContext<CommandSourceStack> context) {
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        if (nation == null) {
            context.getSource().sendFailure(Component.literal("That country does not exist."));
            return 0;
        }
        nation.balance = NationStore.roundMoney(DoubleArgumentType.getDouble(context, "amount"));
        store.save();
        context.getSource().sendSuccess(() -> Component.literal("Set " + nation.name + "'s treasury to $" + nation.balance + "."), true);
        return 1;
    }

    private static int setDoctrine(CommandContext<CommandSourceStack> context) {
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationByName(StringArgumentType.getString(context, "country")).orElse(null);
        Doctrine doctrine = Doctrine.byId(StringArgumentType.getString(context, "id")).orElse(null);
        if (nation == null || doctrine == null) {
            context.getSource().sendFailure(Component.literal("Unknown country or doctrine."));
            return 0;
        }
        nation.doctrine = doctrine.id;
        store.save();
        context.getSource().sendSuccess(() -> Component.literal("Set " + nation.name + "'s doctrine to " + doctrine.displayName + " (" + doctrine.id + ")."), true);
        return 1;
    }

    private static int finishSpies(CommandContext<CommandSourceStack> context) {
        NationStore store = NationStore.get();
        long tick = NationStore.persistentNow();
        int count = 0;
        for (NationStore.Nation nation : store.nations()) {
            for (NationStore.SpyMission mission : store.spyMissionsFor(nation)) {
                mission.completeTick = tick;
                store.spyUnit(nation, mission.spyId).ifPresent(spy -> spy.availableTick = tick);
                ++count;
            }
        }
        store.save();
        int finished = count;
        context.getSource().sendSuccess(() -> Component.literal("Marked " + finished + " spy missions ready to complete."), true);
        return 1;
    }

    private static int save(CommandContext<CommandSourceStack> context) {
        NationStore.get().save();
        context.getSource().sendSuccess(() -> Component.literal("Nation Wars data saved."), true);
        return 1;
    }

    private static int syncOpac(CommandContext<CommandSourceStack> context) {
        OpacClaimsBridge.syncAll(context.getSource().getServer(), NationStore.get());
        context.getSource().sendSuccess(() -> Component.literal("Nation Wars claims synchronized with OPAC."), true);
        return 1;
    }
}
