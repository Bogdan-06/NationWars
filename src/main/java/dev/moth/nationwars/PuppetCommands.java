package dev.moth.nationwars;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.moth.nationwars.service.PuppetRules;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.commands.CommandSourceStack;

/** Thin command handlers for puppet relations; gameplay rules remain in {@link NationStore}. */
public final class PuppetCommands {
    private PuppetCommands() {
    }

    public static int status(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.PuppetRelation relation = actor.store.puppetRelation(actor.nation).orElse(null);
        List<NationStore.Nation> subjects = actor.store.puppetsOf(actor.nation);
        actor.player.sendSystemMessage(NationText.message("nationwars.command.puppet.status.title", actor.nation.name));
        if (relation == null) {
            actor.player.sendSystemMessage(NationText.message("nationwars.command.puppet.status.independent"));
        } else {
            String masterName = actor.store.masterOf(actor.nation).map(nation -> nation.name).orElse(relation.master);
            actor.player.sendSystemMessage(NationText.message("nationwars.command.puppet.status.master", masterName));
            actor.player.sendSystemMessage(NationText.message("nationwars.command.puppet.status.points",
                relation.independencePoints, PuppetRules.MAX_INDEPENDENCE_POINTS));
            actor.player.sendSystemMessage(NationText.message("nationwars.command.puppet.status.lost_wars",
                relation.lostIndependenceWars, PuppetRules.ANNEX_AFTER_LOST_WARS));
            if (actor.store.pointsFrozen(relation)) {
                actor.player.sendSystemMessage(NationText.message("nationwars.command.puppet.status.frozen"));
            }
        }
        if (!subjects.isEmpty()) {
            Component names = NationText.join(subjects.stream()
                .map(nation -> (Component)Component.literal(nation.name)).toList());
            actor.player.sendSystemMessage(NationText.message("nationwars.command.puppet.status.subjects", names));
        }
        return 1;
    }

    public static int propose(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.Nation target = targetNation(context, actor);
        if (target == null) {
            return 0;
        }
        if (actor.nation.id.equals(target.id)) {
            fail(actor.player, "nationwars.command.puppet.error.self");
            return 0;
        }
        if (actor.store.hasPuppetProposal(actor.nation, target)) {
            fail(actor.player, "nationwars.command.puppet.error.proposal_exists", target.name);
            return 0;
        }
        if (!actor.store.canEstablishPuppet(actor.nation, target)
            || !actor.store.proposePuppet(actor.nation, target)) {
            fail(actor.player, "nationwars.command.puppet.error.relation_unavailable", target.name);
            return 0;
        }
        ok(actor.player, "nationwars.command.puppet.propose.sent", target.name);
        actor.store.notifyNation(actor.player.getServer(), target,
            NationText.message("nationwars.command.puppet.propose.received", actor.nation.name));
        return 1;
    }

    public static int accept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.Nation master = targetNation(context, actor);
        if (master == null) {
            return 0;
        }
        if (!actor.store.hasPuppetProposal(master, actor.nation)) {
            fail(actor.player, "nationwars.command.puppet.error.proposal_missing", master.name);
            return 0;
        }
        NationStore.PuppetEstablishResult result = actor.store.acceptPuppetProposal(master, actor.nation);
        if (!result.established()) {
            fail(actor.player, "nationwars.command.puppet.error.relation_unavailable", master.name);
            return 0;
        }
        actor.store.notifyNation(actor.player.getServer(), actor.nation,
            NationText.message("nationwars.command.puppet.accept.puppet", master.name));
        actor.store.notifyNation(actor.player.getServer(), master,
            NationText.message("nationwars.command.puppet.accept.master", actor.nation.name));
        return 1;
    }

    public static int reject(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.Nation master = targetNation(context, actor);
        if (master == null) {
            return 0;
        }
        if (!actor.store.rejectPuppetProposal(master, actor.nation)) {
            fail(actor.player, "nationwars.command.puppet.error.proposal_missing", master.name);
            return 0;
        }
        ok(actor.player, "nationwars.command.puppet.reject.puppet", master.name);
        actor.store.notifyNation(actor.player.getServer(), master,
            NationText.message("nationwars.command.puppet.reject.master", actor.nation.name));
        return 1;
    }

    public static int pacify(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.Nation puppet = targetNation(context, actor);
        if (puppet == null) {
            return 0;
        }
        NationStore.PuppetActionResult result = actor.store.pacifyPuppet(actor.nation, puppet);
        if (!result.successful()) {
            return failAction(actor.player, result, 0);
        }
        ok(actor.player, "nationwars.command.puppet.pacify.success", puppet.name, result.points());
        actor.store.notifyNation(actor.player.getServer(), puppet,
            NationText.message("nationwars.command.puppet.pacify.received", actor.nation.name, result.points()));
        return 1;
    }

    public static int agitate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.PuppetActionResult result = actor.store.agitatePuppet(actor.nation);
        if (!result.successful()) {
            return failAction(actor.player, result, 0);
        }
        ok(actor.player, "nationwars.command.puppet.agitate.success", result.points());
        return 1;
    }

    public static int liberate(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.Nation master = actor.store.masterOf(actor.nation).orElse(null);
        NationStore.PuppetActionResult result = actor.store.peacefullyLiberatePuppet(actor.nation);
        if (!result.successful()) {
            return failAction(actor.player, result, PuppetRules.MAX_INDEPENDENCE_POINTS);
        }
        ok(actor.player, "nationwars.command.puppet.liberate.success");
        if (master != null) {
            actor.store.notifyNation(actor.player.getServer(), master,
                NationText.message("nationwars.command.puppet.liberate.master", actor.nation.name));
        }
        return 1;
    }

    public static int release(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.Nation puppet = targetNation(context, actor);
        if (puppet == null) {
            return 0;
        }
        NationStore.PuppetRelation relation = actor.store.puppetRelation(puppet).orElse(null);
        if (relation != null && actor.store.pointsFrozen(relation)) {
            fail(actor.player, "nationwars.command.puppet.error.active_war");
            return 0;
        }
        if (!actor.store.releasePuppet(actor.nation, puppet)) {
            fail(actor.player, "nationwars.command.puppet.error.not_your_puppet", puppet.name);
            return 0;
        }
        ok(actor.player, "nationwars.command.puppet.release.success", puppet.name);
        actor.store.notifyNation(actor.player.getServer(), puppet,
            NationText.message("nationwars.command.puppet.release.puppet", actor.nation.name));
        return 1;
    }

    public static int annex(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.Nation puppet = targetNation(context, actor);
        if (puppet == null) {
            return 0;
        }
        String puppetName = puppet.name;
        NationStore.PuppetAnnexResult result = actor.store.annexPuppet(actor.nation, puppet);
        if (!result.annexed()) {
            return failAnnex(actor.player, puppetName, result);
        }
        actor.store.notifyNation(actor.player.getServer(), actor.nation,
            NationText.message("nationwars.command.puppet.annex.success", puppetName,
                result.transferredClaims(), result.transferredMembers(), result.transferredTreasury()));
        return 1;
    }

    public static int war(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Actor actor = requireOwner(context);
        if (actor == null) {
            return 0;
        }
        NationStore.Nation master = actor.store.masterOf(actor.nation).orElse(null);
        if (master == null) {
            fail(actor.player, "nationwars.command.puppet.error.not_puppet");
            return 0;
        }
        NationStore.PuppetWarResult result = actor.store.startIndependenceWar(actor.nation);
        if (!result.started()) {
            return failWar(actor.player, result);
        }
        ok(actor.player, "nationwars.command.puppet.war.declared", master.name);
        actor.store.notifyNation(actor.player.getServer(), master,
            NationText.message("nationwars.command.puppet.war.received", actor.nation.name));
        return 1;
    }

    private static Actor requireOwner(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        if (!NationWarsConfig.get().puppets) {
            fail(player, "nationwars.command.puppet.error.disabled");
            return null;
        }
        NationStore store = NationStore.get();
        NationStore.Nation nation = store.nationOf(player.getUUID()).orElse(null);
        if (nation == null) {
            fail(player, "nationwars.command.error.not_in_nation");
            return null;
        }
        if (!store.isOwner(player.getUUID(), nation)) {
            fail(player, "nationwars.command.error.owner_only");
            return null;
        }
        return new Actor(player, store, nation);
    }

    private static NationStore.Nation targetNation(CommandContext<CommandSourceStack> context, Actor actor) {
        String name = StringArgumentType.getString(context, "country");
        NationStore.Nation nation = actor.store.nationByName(name).orElse(null);
        if (nation == null) {
            fail(actor.player, "nationwars.command.error.nation_missing");
        }
        return nation;
    }

    private static int failAction(ServerPlayer player, NationStore.PuppetActionResult result, int requiredPoints) {
        return switch (result.status()) {
            case NOT_A_PUPPET -> failResult(player, "nationwars.command.puppet.error.not_puppet");
            case NOT_MASTER -> failResult(player, "nationwars.command.puppet.error.not_your_puppet");
            case FROZEN -> failResult(player, "nationwars.command.puppet.error.frozen");
            case COOLDOWN -> failResult(player, "nationwars.command.puppet.error.cooldown",
                remainingSeconds(result.cooldownUntil()));
            case POINTS_TOO_LOW -> failResult(player, "nationwars.command.puppet.error.points_required",
                requiredPoints, result.points());
            case MAXIMUM_WARS_LOST -> failResult(player, "nationwars.command.puppet.error.maximum_wars_lost",
                PuppetRules.ANNEX_AFTER_LOST_WARS);
            case ACTIVE_WAR -> failResult(player, "nationwars.command.puppet.error.active_war");
            case NOT_ANNEXABLE -> failResult(player, "nationwars.command.puppet.error.not_annexable",
                result.points(), result.lostWars());
            case SUCCESS -> 1;
        };
    }

    private static int failWar(ServerPlayer player, NationStore.PuppetWarResult result) {
        return switch (result.status()) {
            case POINTS_TOO_LOW -> failResult(player, "nationwars.command.puppet.error.war_points_required", 151);
            case MAXIMUM_WARS_LOST -> failResult(player, "nationwars.command.puppet.error.maximum_wars_lost",
                PuppetRules.ANNEX_AFTER_LOST_WARS);
            case ACTIVE_WAR, FROZEN -> failResult(player, "nationwars.command.puppet.error.active_war");
            case NOT_A_PUPPET -> failResult(player, "nationwars.command.puppet.error.not_puppet");
            default -> failResult(player, "nationwars.command.puppet.error.war_failed");
        };
    }

    private static int failAnnex(ServerPlayer player, String puppetName, NationStore.PuppetAnnexResult result) {
        return switch (result.status()) {
            case NOT_MASTER, NOT_A_PUPPET -> failResult(player,
                "nationwars.command.puppet.error.not_your_puppet", puppetName);
            case ACTIVE_WAR, FROZEN -> failResult(player, "nationwars.command.puppet.error.active_war");
            case NOT_ANNEXABLE -> failResult(player, "nationwars.command.puppet.error.not_annexable_target", puppetName);
            default -> failResult(player, "nationwars.command.puppet.error.annex_failed", puppetName);
        };
    }

    private static long remainingSeconds(long deadline) {
        return Math.max(1L, (deadline - NationStore.persistentNow() + 19L) / 20L);
    }

    private static int failResult(ServerPlayer player, String key, Object... arguments) {
        fail(player, key, arguments);
        return 0;
    }

    private static void ok(ServerPlayer player, String key, Object... arguments) {
        player.sendSystemMessage(NationText.message(key, arguments));
    }

    private static void fail(ServerPlayer player, String key, Object... arguments) {
        player.sendSystemMessage(NationText.message(key, arguments));
    }

    private record Actor(ServerPlayer player, NationStore store, NationStore.Nation nation) {
    }
}
