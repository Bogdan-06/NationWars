package dev.moth.nationwars.service;

import dev.moth.nationwars.NationStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/** Pure relationship/proposal operations over persisted Nation Wars state. */
public final class PuppetService {
    private PuppetService() {
    }

    public static Optional<NationStore.PuppetRelation> relation(NationStore.State state, String puppetId) {
        return state == null || puppetId == null ? Optional.empty()
            : Optional.ofNullable(state.puppetRelations.get(puppetId));
    }

    public static Optional<String> master(NationStore.State state, String puppetId) {
        return relation(state, puppetId).map(value -> value.master);
    }

    public static List<String> puppets(NationStore.State state, String masterId) {
        if (state == null || masterId == null) {
            return List.of();
        }
        return state.puppetRelations.values().stream()
            .filter(relation -> masterId.equals(relation.master))
            .map(relation -> relation.puppet)
            .sorted(String::compareTo)
            .toList();
    }

    public static boolean canEstablish(NationStore.State state, String masterId, String puppetId) {
        if (state == null || masterId == null || puppetId == null || masterId.equals(puppetId)
            || !state.nations.containsKey(masterId) || !state.nations.containsKey(puppetId)
            || state.puppetRelations.containsKey(puppetId)) {
            return false;
        }
        String current = masterId;
        java.util.HashSet<String> visited = new java.util.HashSet<>();
        while (current != null && visited.add(current)) {
            if (puppetId.equals(current)) {
                return false;
            }
            NationStore.PuppetRelation relation = state.puppetRelations.get(current);
            current = relation == null ? null : relation.master;
        }
        return current == null;
    }

    public static EstablishResult establish(NationStore.State state, String masterId, String puppetId) {
        if (!canEstablish(state, masterId, puppetId)) {
            return new EstablishResult(false, List.of(), null);
        }
        List<String> released = releaseAll(state, puppetId);
        NationStore.PuppetRelation relation = new NationStore.PuppetRelation();
        relation.master = masterId;
        relation.puppet = puppetId;
        relation.independencePoints = PuppetRules.INITIAL_INDEPENDENCE_POINTS;
        state.puppetRelations.put(puppetId, relation);
        state.puppetProposals.entrySet().removeIf(entry -> involves(entry.getValue(), puppetId));
        return new EstablishResult(true, released, relation);
    }

    public static boolean release(NationStore.State state, String masterId, String puppetId) {
        NationStore.PuppetRelation relation = state == null ? null : state.puppetRelations.get(puppetId);
        if (relation == null || masterId == null || !masterId.equals(relation.master)) {
            return false;
        }
        state.puppetRelations.remove(puppetId);
        state.puppetProposals.entrySet().removeIf(entry -> involves(entry.getValue(), puppetId));
        return true;
    }

    public static List<String> releaseAll(NationStore.State state, String masterId) {
        if (state == null || masterId == null) {
            return List.of();
        }
        List<String> released = puppets(state, masterId);
        released.forEach(state.puppetRelations::remove);
        if (!released.isEmpty()) {
            state.puppetProposals.entrySet().removeIf(entry -> released.stream().anyMatch(id -> involves(entry.getValue(), id)));
        }
        return released;
    }

    public static boolean addProposal(NationStore.State state, String masterId, String puppetId, long createdTick) {
        if (!canEstablish(state, masterId, puppetId)) {
            return false;
        }
        NationStore.PuppetProposal proposal = new NationStore.PuppetProposal();
        proposal.master = masterId;
        proposal.puppet = puppetId;
        proposal.createdTick = Math.max(0L, createdTick);
        return state.puppetProposals.putIfAbsent(proposalKey(masterId, puppetId), proposal) == null;
    }

    public static boolean hasProposal(NationStore.State state, String masterId, String puppetId) {
        return state != null && state.puppetProposals.containsKey(proposalKey(masterId, puppetId));
    }

    public static boolean removeProposal(NationStore.State state, String masterId, String puppetId) {
        return state != null && state.puppetProposals.remove(proposalKey(masterId, puppetId)) != null;
    }

    public static NormalizeResult normalize(NationStore.State state) {
        if (state.puppetRelations == null) {
            state.puppetRelations = new LinkedHashMap<>();
        }
        if (state.puppetProposals == null) {
            state.puppetProposals = new LinkedHashMap<>();
        }
        int relationsBefore = state.puppetRelations.size();
        state.puppetRelations.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null
            || !state.nations.containsKey(entry.getKey()) || !state.nations.containsKey(entry.getValue().master)
            || entry.getKey().equals(entry.getValue().master));
        state.puppetRelations.forEach((puppet, relation) -> {
            relation.puppet = puppet;
            relation.independencePoints = PuppetRules.clampPoints(relation.independencePoints);
            relation.lostIndependenceWars = Math.max(0, relation.lostIndependenceWars);
            relation.agitateCooldownUntil = Math.max(0L, relation.agitateCooldownUntil);
            relation.pacifyCooldownUntil = Math.max(0L, relation.pacifyCooldownUntil);
            relation.tradePointCooldownUntil = Math.max(0L, relation.tradePointCooldownUntil);
        });

        // Remove one deterministic edge at a time until every master chain is acyclic.
        boolean changed;
        do {
            changed = false;
            for (String puppet : new ArrayList<>(state.puppetRelations.keySet()).stream().sorted().toList()) {
                if (!chainHasCycle(state, puppet)) {
                    continue;
                }
                state.puppetRelations.remove(puppet);
                changed = true;
                break;
            }
        } while (changed);

        int proposalsBefore = state.puppetProposals.size();
        state.puppetProposals.entrySet().removeIf(entry -> {
            NationStore.PuppetProposal proposal = entry.getValue();
            return proposal == null || proposal.master == null || proposal.puppet == null
                || !entry.getKey().equals(proposalKey(proposal.master, proposal.puppet))
                || !canEstablish(state, proposal.master, proposal.puppet);
        });
        return new NormalizeResult(relationsBefore - state.puppetRelations.size(),
            proposalsBefore - state.puppetProposals.size());
    }

    public static String proposalKey(String masterId, String puppetId) {
        return String.valueOf(masterId) + "->" + String.valueOf(puppetId);
    }

    private static boolean chainHasCycle(NationStore.State state, String start) {
        java.util.HashSet<String> visited = new java.util.HashSet<>();
        String current = start;
        while (current != null) {
            if (!visited.add(current)) {
                return true;
            }
            NationStore.PuppetRelation relation = state.puppetRelations.get(current);
            current = relation == null ? null : relation.master;
        }
        return false;
    }

    private static boolean involves(NationStore.PuppetProposal proposal, String nationId) {
        return proposal != null && (nationId.equals(proposal.master) || nationId.equals(proposal.puppet));
    }

    public record EstablishResult(boolean established, List<String> releasedPuppets,
                                  NationStore.PuppetRelation relation) {
        public EstablishResult {
            releasedPuppets = List.copyOf(releasedPuppets);
        }
    }

    public record NormalizeResult(int removedRelations, int removedProposals) {
    }
}
