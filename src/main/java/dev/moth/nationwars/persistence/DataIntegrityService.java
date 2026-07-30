package dev.moth.nationwars.persistence;

import dev.moth.nationwars.ClaimKey;
import dev.moth.nationwars.NationStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Repairs only structurally invalid cross-references already rejected by the legacy normalizer. */
public final class DataIntegrityService {
    private DataIntegrityService() {
    }

    public static RepairReport repairReferences(NationStore.State state) {
        List<String> details = new ArrayList<>();
        int repaired = 0;

        repaired += removeEntries(state.playerNation, (player, nation) -> blank(player) || !state.nations.containsKey(nation),
            "player-to-nation reference", details);
        repaired += removeEntries(state.claims, (claim, nation) -> !validClaim(claim) || !state.nations.containsKey(nation),
            "claim owner reference", details);
        repaired += removeEntries(state.wars, (id, war) -> war == null || !state.nations.containsKey(war.attacker)
            || !state.nations.containsKey(war.defender) || Objects.equals(war.attacker, war.defender), "war reference", details);
        repaired += removeEntries(state.alliances, (id, alliance) -> alliance == null || !state.nations.containsKey(alliance.leader),
            "alliance reference", details);
        repaired += removeEntries(state.formerNationMembers, (nation, members) -> !state.nations.containsKey(nation),
            "former-membership reference", details);
        repaired += removeEntries(state.nationInvites, (nation, players) -> !state.nations.containsKey(nation),
            "nation invitation reference", details);
        repaired += removeEntries(state.puppetRelations, (puppet, relation) -> relation == null
            || !state.nations.containsKey(puppet) || !state.nations.containsKey(relation.master)
            || !puppet.equals(relation.puppet) || puppet.equals(relation.master), "puppet relation reference", details);
        repaired += removeEntries(state.puppetProposals, (id, proposal) -> proposal == null
            || !state.nations.containsKey(proposal.master) || !state.nations.containsKey(proposal.puppet)
            || proposal.master.equals(proposal.puppet), "puppet proposal reference", details);
        repaired += removeEntries(state.guarantees, (nation, guarantors) -> !state.nations.containsKey(nation),
            "guarantee target reference", details);

        for (var entry : state.guarantees.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            int before = entry.getValue().size();
            entry.getValue().removeIf(id -> !state.nations.containsKey(id));
            int removed = before - entry.getValue().size();
            repaired += removed;
            if (removed > 0) {
                details.add("removed " + removed + " deleted guarantor reference(s) from " + entry.getKey());
            }
        }
        return new RepairReport(repaired, List.copyOf(details));
    }

    private static <K, V> int removeEntries(java.util.Map<K, V> map,
                                             java.util.function.BiPredicate<K, V> invalid,
                                             String label, List<String> details) {
        int before = map.size();
        map.entrySet().removeIf(entry -> invalid.test(entry.getKey(), entry.getValue()));
        int removed = before - map.size();
        if (removed > 0) {
            details.add("removed " + removed + " invalid " + label + "(s)");
        }
        return removed;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean validClaim(String value) {
        try {
            ClaimKey.parse(value);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public record RepairReport(int repairedReferences, List<String> details) {
    }
}
