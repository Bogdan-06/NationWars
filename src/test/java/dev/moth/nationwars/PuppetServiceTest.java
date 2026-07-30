package dev.moth.nationwars;

import dev.moth.nationwars.service.PuppetRules;
import dev.moth.nationwars.service.PuppetService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PuppetServiceTest {
    @Test
    void establishingPuppetStartsAtOneHundredIndependencePoints() {
        NationStore.State state = stateWithNations("master", "puppet");

        PuppetService.EstablishResult result = PuppetService.establish(state, "master", "puppet");

        assertTrue(result.established());
        assertTrue(result.releasedPuppets().isEmpty());
        assertNotNull(result.relation());
        assertEquals("master", result.relation().master);
        assertEquals("puppet", result.relation().puppet);
        assertEquals(PuppetRules.INITIAL_INDEPENDENCE_POINTS, result.relation().independencePoints);
        assertEquals("master", PuppetService.master(state, "puppet").orElseThrow());
    }

    @Test
    void oneMasterCanControlMultiplePuppets() {
        NationStore.State state = stateWithNations("master", "first", "second");

        assertTrue(PuppetService.establish(state, "master", "first").established());
        assertTrue(PuppetService.establish(state, "master", "second").established());

        assertEquals(java.util.List.of("first", "second"), PuppetService.puppets(state, "master"));
    }

    @Test
    void aPuppetCannotAcquireASecondMaster() {
        NationStore.State state = stateWithNations("first-master", "second-master", "puppet");
        assertTrue(PuppetService.establish(state, "first-master", "puppet").established());

        PuppetService.EstablishResult rejected = PuppetService.establish(state, "second-master", "puppet");

        assertFalse(rejected.established());
        assertEquals("first-master", PuppetService.master(state, "puppet").orElseThrow());
        assertEquals(1, state.puppetRelations.size());
    }

    @Test
    void puppetChainsCannotFormCycles() {
        NationStore.State state = stateWithNations("alpha", "beta", "gamma");
        assertTrue(PuppetService.establish(state, "alpha", "beta").established());
        assertTrue(PuppetService.establish(state, "beta", "gamma").established());

        assertFalse(PuppetService.canEstablish(state, "gamma", "alpha"));
        assertFalse(PuppetService.establish(state, "gamma", "alpha").established());
        assertFalse(PuppetService.canEstablish(state, "alpha", "alpha"));
    }

    @Test
    void puppetingAMasterReleasesItsDirectPuppets() {
        NationStore.State state = stateWithNations("new-master", "old-master", "first", "second");
        assertTrue(PuppetService.establish(state, "old-master", "first").established());
        assertTrue(PuppetService.establish(state, "old-master", "second").established());

        PuppetService.EstablishResult result = PuppetService.establish(state, "new-master", "old-master");

        assertTrue(result.established());
        assertEquals(java.util.List.of("first", "second"), result.releasedPuppets());
        assertEquals("new-master", PuppetService.master(state, "old-master").orElseThrow());
        assertTrue(PuppetService.master(state, "first").isEmpty());
        assertTrue(PuppetService.master(state, "second").isEmpty());
    }

    @Test
    void proposalsCanBeAddedRejectedAndConsumedByEstablishment() {
        NationStore.State state = stateWithNations("master", "puppet", "other");

        assertTrue(PuppetService.addProposal(state, "master", "puppet", 25L));
        assertFalse(PuppetService.addProposal(state, "master", "puppet", 30L));
        assertTrue(PuppetService.hasProposal(state, "master", "puppet"));
        assertEquals(25L, state.puppetProposals.get("master->puppet").createdTick);

        assertTrue(PuppetService.removeProposal(state, "master", "puppet"));
        assertFalse(PuppetService.hasProposal(state, "master", "puppet"));
        assertFalse(PuppetService.removeProposal(state, "master", "puppet"));

        assertTrue(PuppetService.addProposal(state, "master", "puppet", 40L));
        assertTrue(PuppetService.addProposal(state, "other", "puppet", 41L));
        assertTrue(PuppetService.establish(state, "master", "puppet").established());
        assertTrue(state.puppetProposals.isEmpty(), "accepting one proposal clears stale proposals involving that puppet");
    }

    @Test
    void normalizationRepairsCyclesAndMalformedProposalsDeterministically() {
        NationStore.State state = stateWithNations("alpha", "beta", "gamma");
        NationStore.PuppetRelation alpha = relation("beta", "alpha", 250);
        NationStore.PuppetRelation beta = relation("alpha", "beta", -10);
        state.puppetRelations.put("alpha", alpha);
        state.puppetRelations.put("beta", beta);

        NationStore.PuppetProposal valid = proposal("alpha", "gamma", 10L);
        NationStore.PuppetProposal wrongKey = proposal("alpha", "gamma", 20L);
        NationStore.PuppetProposal self = proposal("gamma", "gamma", 30L);
        state.puppetProposals.put("alpha->gamma", valid);
        state.puppetProposals.put("wrong-key", wrongKey);
        state.puppetProposals.put("gamma->gamma", self);

        PuppetService.NormalizeResult result = PuppetService.normalize(state);

        assertEquals(1, result.removedRelations());
        assertFalse(state.puppetRelations.containsKey("alpha"));
        assertTrue(state.puppetRelations.containsKey("beta"));
        assertEquals(0, state.puppetRelations.get("beta").independencePoints);
        assertEquals(2, result.removedProposals());
        assertEquals(java.util.Set.of("alpha->gamma"), state.puppetProposals.keySet());
    }

    private static NationStore.State stateWithNations(String... ids) {
        NationStore.State state = new NationStore.State();
        for (String id : ids) {
            NationStore.Nation nation = new NationStore.Nation();
            nation.id = id;
            nation.name = id;
            state.nations.put(id, nation);
        }
        return state;
    }

    private static NationStore.PuppetRelation relation(String master, String puppet, int points) {
        NationStore.PuppetRelation relation = new NationStore.PuppetRelation();
        relation.master = master;
        relation.puppet = puppet;
        relation.independencePoints = points;
        return relation;
    }

    private static NationStore.PuppetProposal proposal(String master, String puppet, long createdTick) {
        NationStore.PuppetProposal proposal = new NationStore.PuppetProposal();
        proposal.master = master;
        proposal.puppet = puppet;
        proposal.createdTick = createdTick;
        return proposal;
    }
}
