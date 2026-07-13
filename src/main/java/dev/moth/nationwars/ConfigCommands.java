package dev.moth.nationwars;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class ConfigCommands {
    private ConfigCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(root("configurate"));
        dispatcher.register(root("configure"));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> root(String name) {
        return Commands.literal(name).requires(source -> source.hasPermission(2))
            .executes(ConfigCommands::show)
            .then(booleanSetting("LimitedDoctrines", "LimitedDoctrines", config -> config.enforceDoctrineLimits, (config, enabled) -> config.enforceDoctrineLimits = enabled))
            .then(booleanSetting("DisableEspionage", "DisableEspionage", config -> config.disableEspionage, (config, enabled) -> config.disableEspionage = enabled))
            .then(booleanSetting("ScorchedEarth", "ScorchedEarth", config -> config.scorchedEarth, (config, enabled) -> config.scorchedEarth = enabled))
            .then(booleanSetting("InstantWar", "InstantWar", config -> config.instantWar, (config, enabled) -> config.instantWar = enabled))
            .then(disableDoctrine())
            .then(booleanSetting("NoMercy", "NoMercy", config -> config.noMercy, (config, enabled) -> config.noMercy = enabled))
            .then(booleanSetting("Factions", "Factions", config -> config.factions, (config, enabled) -> config.factions = enabled))
            .then(booleanSetting("Guarantees", "Guarantees", config -> config.guarantees, (config, enabled) -> config.guarantees = enabled))
            .then(booleanSetting("LeaveNation", "LeaveNation", config -> config.leaveNation, (config, enabled) -> config.leaveNation = enabled))
            .then(booleanSetting("RejoinNation", "RejoinNation", config -> config.rejoinNation, (config, enabled) -> config.rejoinNation = enabled))
            .then(booleanSetting("Satelites", "Satellites", config -> config.satellites, (config, enabled) -> config.satellites = enabled))
            .then(booleanSetting("Satellites", "Satellites", config -> config.satellites, (config, enabled) -> config.satellites = enabled))
            .then(booleanSetting("ClaimNether", "ClaimNether", config -> config.claimNether, (config, enabled) -> config.claimNether = enabled))
            .then(booleanSetting("ClaimEnd", "ClaimEnd", config -> config.claimEnd, (config, enabled) -> config.claimEnd = enabled))
            .then(booleanSetting("Colonialism", "Colonialism", config -> config.colonialism, (config, enabled) -> config.colonialism = enabled))
            .then(booleanSetting("AllowTrade", "AllowTrade", config -> config.allowTrade, (config, enabled) -> config.allowTrade = enabled))
            .then(Commands.literal("SpawnProtection")
                .then(Commands.argument("blocks", IntegerArgumentType.integer(0))
                    .executes(context -> {
                        NationWarsConfig config = NationWarsConfig.get();
                        int previous = config.spawnProtection;
                        config.spawnProtection = IntegerArgumentType.getInteger(context, "blocks");
                        if (config.save()) {
                            return ok(context, "nationwars.command.config.spawn_protection", config.spawnProtection);
                        }
                        config.spawnProtection = previous;
                        return saveFailed(context);
                    })));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> booleanSetting(String literal, String display, BooleanGetter getter, BooleanSetter setter) {
        return Commands.literal(literal)
            .then(Commands.argument("enabled", StringArgumentType.word())
                .executes(context -> {
                    Boolean enabled = parseBoolean(context, "enabled");
                    if (enabled == null) {
                        return 0;
                    }
                    NationWarsConfig config = NationWarsConfig.get();
                    boolean previous = getter.get(config);
                    setter.set(config, enabled);
                    if (config.save()) {
                        return ok(context, "nationwars.command.config.value", display, enabled);
                    }
                    setter.set(config, previous);
                    return saveFailed(context);
                }));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> disableDoctrine() {
        return Commands.literal("DisableDoctrine")
            .then(Commands.argument("doctrine", StringArgumentType.word())
                .executes(context -> setDoctrineDisabled(context, true))
                .then(Commands.argument("disabled", StringArgumentType.word())
                    .executes(context -> {
                        Boolean disabled = parseBoolean(context, "disabled");
                        return disabled == null ? 0 : setDoctrineDisabled(context, disabled);
                    })));
    }

    private static Boolean parseBoolean(CommandContext<CommandSourceStack> context, String argument) {
        String value = StringArgumentType.getString(context, argument).trim().toLowerCase(java.util.Locale.ROOT);
        return switch (value) {
            case "true", "t", "yes", "y", "1", "on" -> true;
            case "false", "f", "no", "n", "0", "off" -> false;
            default -> {
                context.getSource().sendFailure(NationText.tr("nationwars.command.config.error.boolean", value));
                yield null;
            }
        };
    }

    private static int setDoctrineDisabled(CommandContext<CommandSourceStack> context, boolean disabled) {
        String id = StringArgumentType.getString(context, "doctrine");
        Doctrine doctrine = Doctrine.byId(id).orElse(null);
        if (doctrine == null) {
            context.getSource().sendFailure(NationText.tr("nationwars.command.config.error.unknown_doctrine", id, Doctrine.choices()));
            return 0;
        }
        if (!NationWarsConfig.get().setDoctrineDisabled(doctrine, disabled)) {
            return saveFailed(context);
        }
        return ok(context, "nationwars.command.config.doctrine_disabled", doctrine.id, disabled);
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        NationWarsConfig config = NationWarsConfig.get();
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> NationText.tr("nationwars.command.config.header"), false);
        line(source, "LimitedDoctrines", config.enforceDoctrineLimits);
        line(source, "DisableEspionage", config.disableEspionage);
        line(source, "ScorchedEarth", config.scorchedEarth);
        line(source, "InstantWar", config.instantWar);
        line(source, "NoMercy", config.noMercy);
        line(source, "Factions", config.factions);
        line(source, "Guarantees", config.guarantees);
        line(source, "LeaveNation", config.leaveNation);
        line(source, "RejoinNation", config.rejoinNation);
        line(source, "Satellites", config.satellites);
        line(source, "ClaimNether", config.claimNether);
        line(source, "ClaimEnd", config.claimEnd);
        line(source, "Colonialism", config.colonialism);
        line(source, "AllowTrade", config.allowTrade);
        source.sendSuccess(() -> NationText.tr("nationwars.command.config.spawn_protection", config.spawnProtection), false);
        Component disabled = config.disabledDoctrines.isEmpty()
            ? NationText.tr("nationwars.common.none")
            : Component.literal(String.join(", ", config.disabledDoctrines));
        source.sendSuccess(() -> NationText.tr("nationwars.command.config.disabled_doctrines", disabled), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String name, boolean value) {
        source.sendSuccess(() -> NationText.tr("nationwars.command.config.value", name, value), false);
    }

    private static int ok(CommandContext<CommandSourceStack> context, String key, Object... arguments) {
        context.getSource().sendSuccess(() -> NationText.message(key, arguments), true);
        return 1;
    }

    private static int saveFailed(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(NationText.message("nationwars.command.config.error.save"));
        return 0;
    }

    @FunctionalInterface
    private interface BooleanGetter {
        boolean get(NationWarsConfig config);
    }

    @FunctionalInterface
    private interface BooleanSetter {
        void set(NationWarsConfig config, boolean value);
    }
}
