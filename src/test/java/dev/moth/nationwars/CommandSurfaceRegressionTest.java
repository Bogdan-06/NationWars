package dev.moth.nationwars;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards checklist command-surface removals that are otherwise difficult to exercise without a running server. */
class CommandSurfaceRegressionTest {
    private static final Path JAVA = Path.of("src/main/java/dev/moth/nationwars");

    @Test
    void onlyConfigureSpellingIsRegistered() throws IOException {
        String source = read("ConfigCommands.java");

        assertTrue(source.contains("dispatcher.register(root(\"configure\"))"));
        assertFalse(source.contains("root(\"configurate\")"));
        assertFalse(source.contains("booleanSetting(\"Satelites\""));
    }

    @Test
    void nationsShortcutRemainsDisabled() throws IOException {
        String source = read("command/MarketCommand.java");

        assertFalse(source.contains("Commands.literal(\"nations\")"));
    }

    @Test
    void nationCreateOpensTheMenuWithoutLegacyNameOrDoctrineArguments() throws IOException {
        String source = read("command/NationCommand.java");
        Pattern directMenuRegistration = Pattern.compile(
            "\\.then\\(Commands\\.literal\\(\\\"create\\\"\\)\\.executes\\(NationCommands::openNationCreateMenuUnnamed\\)\\)");

        assertTrue(directMenuRegistration.matcher(source).find());
        assertFalse(source.contains("openNationCreateMenuWithName"));
    }

    @Test
    void nationDeletionLivesUnderConfigureAndAcceptsFullCountryNames() throws IOException {
        String nation = read("command/NationCommand.java");
        String configure = read("ConfigCommands.java");

        assertFalse(nation.contains("Commands.literal(\"delete\")"));
        assertTrue(configure.contains("Commands.literal(\"deletenation\")"));
        assertTrue(configure.contains("StringArgumentType.greedyString()"));
        assertTrue(configure.contains("store.deleteNation(nation)"));
        assertTrue(configure.contains("OpacClaimsBridge.syncAll"));
    }

    @Test
    void puppetRestrictionsRemainWiredIntoClaimsAndOrdinaryWars() throws IOException {
        String source = read("NationCommands.java");

        assertTrue(source.contains("!store.canPuppetClaim(nation.get())"));
        assertTrue(source.contains("store.isPuppet(attacker)"));
        assertTrue(source.contains("store.isMasterOf(attacker, defender)"));
        assertTrue(source.contains("store.isMasterOf(defender, attacker)"));
    }

    @Test
    void checklistTenCommandAndConfigurationSurfaceIsRegisteredExactly() throws IOException {
        String dev = read("DevCommands.java");
        String nation = read("command/NationCommand.java");
        String configure = read("ConfigCommands.java");

        assertTrue(dev.contains("dispatcher.register(command(\"nwdev\"))"));
        assertFalse(dev.contains("command(\"nationwarsdev\")"));
        assertFalse(nation.contains("Commands.literal(\"city\")"));
        assertTrue(configure.contains("doubleSetting(\"MaintenanceMultiplierr\""));
        assertTrue(configure.contains("doubleSetting(\"ClaimCostMultiplier\""));
        assertTrue(configure.contains("doubleSetting(\"IncomeMultiplier\""));
        assertTrue(configure.contains("integerSetting(\"MemberIncome\""));
        assertTrue(configure.contains("booleanSetting(\"Stealing\""));
    }

    @Test
    void capitalCaptureNoLongerEndsWarsAndWartimeTradeRemainsBlocked() throws IOException {
        String events = read("NationEvents.java");
        String commands = read("NationCommands.java");
        String trade = read("NationTradeMenu.java");
        String store = read("NationStore.java");

        assertFalse(events.contains("applyCapitulation("));
        assertFalse(events.contains("capitalCaptured ||"));
        assertFalse(events.contains("migrateNaturalCoastClaims"));
        assertFalse(events.contains("isHillOrMountainClaim"));
        assertFalse(commands.contains("setCoastClaim("));
        assertTrue(events.contains("applyWarClaimLossPenalty(player.getServer(), defender.get(), claim)"));
        assertTrue(trade.contains("nationwars.trade.error.at_war"));
        assertTrue(trade.contains("store.activeWarForCapture(this.ownNation, this.otherNation)"));
        assertTrue(store.contains("this.activeWarForCapture(proposer, receiver).isPresent()"));
    }

    @Test
    void scorchedEarthAndStealingAreWiredIntoPhysicalClaimActions() throws IOException {
        String events = read("NationEvents.java");

        assertTrue(events.contains("refreshScorchedEarthPass(player)"));
        assertTrue(Pattern.compile("receiveCanceled=true\\)\\s+public static void blockBreakAccess").matcher(events).find());
        assertTrue(Pattern.compile("receiveCanceled=true\\)\\s+public static void blockPlaceAccess").matcher(events).find());
        assertTrue(events.contains("ClaimProtectionRules.paidAccessAllowed(NationWarsConfig.get().stealing"));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(JAVA.resolve(relativePath), StandardCharsets.UTF_8);
    }
}
