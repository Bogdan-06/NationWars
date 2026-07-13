# Nation Wars 0.4.0 quality-pass report

## Result

Nation Wars 0.4.0 builds and starts on a dedicated Minecraft 1.21.1 / NeoForge
21.1.227 server with required OPAC 0.27.5. The pass added complete English and
Romanian localization, versioned/recoverable persistence, four command-registration
modules and two calculation services, production-safe debug commands, optional
`INVITE_ONLY`-default join policies, safer OPAC configuration editing, narrower
tested metadata, and targeted regression tests. `NationCommands`, `NationStore`,
and `NationEvents` remain the principal compatibility implementations; this pass
did not claim a complete architectural extraction.

The pre-change archive is:

`C:\Users\Moth\Desktop\nation-wars project\backups\nationwars-0.4.0-pre-quality-pass_2026-07-12_161732.zip`

SHA-256:
`0FED1D4066572E70889BAF4F978200701E49AA84F0E5C409BF0117E8C41C6931`

## Substantially changed or added

- Behavior and translation documentation: `docs/current-behaviour.md`,
  `docs/implementation-plan.md`, `docs/translations.md`, and this report.
- Localization: `NationText`, `en_us.json`, `ro_ro.json`, every command/menu,
  doctrine/ideology labels, event broadcasts, and OPAC feedback.
- Command separation: `command/NationCommand`, `AllianceCommand`, `WarCommand`,
  and `MarketCommand`; `NationCommands` retains handlers as the compatibility
  facade.
- Pure services: `service/EconomyService` and `service/WarService`.
- Persistence: `persistence/NationDataSerializer`, `NationRepository`,
  `DataMigrationService`, `DataIntegrityService`, and `SaveCoordinator`.
- Configuration and policy: `TechnicalConfig`, `JoinPolicy`, `NationWars`,
  `DevCommands`, `NationStore`, and `command/NationCommand`.
- OPAC safety: `integration/opac/OpacConfigSynchronizer` and
  `OpacClaimsBridge`.
- Metadata/build: `build.gradle`, `gradle.properties`, and
  `META-INF/neoforge.mods.toml`.
- Tests: `TranslationValidationTest`, `GameplayFormulaTest`,
  `NationRepositoryTest`, `OpacConfigSynchronizerTest`, and expanded
  `NationStoreTest`.

No working gameplay system or source file was removed.

## Confirmed reliability and safety defects fixed

1. Destructive debug commands previously required only permission level 2 and
   were always available. They now require level 4, an environment-aware
   technical setting, and emit executor/target/value audit logs.
2. OPAC TOML updates previously used direct regular-expression replacement
   without backup, validation, or rollback. The synchronizer now changes only
   owned keys, preserves unrelated settings, creates a backup, validates, and
   restores on failure.
3. Save data previously had no explicit version/migration boundary. Version 0
   saves now receive an identity-preserving migration to version 1 after a
   dedicated pre-migration backup.
4. An interrupted complete `.tmp` write can now be recovered when the main file
   is missing. Corrupt-main fallback, `.bak`, immediate saves, and atomic
   replacement remain intact.
5. Unknown root and nested fields belonging to retained save objects are
   carried forward instead of being silently discarded. Invalid deleted-nation
   references are repaired with an explicit warning.

## Deliberately unchanged suspicious behavior

- Maintenance charges every claim once a nation owns more than one claim.
- A coastal British capital receives capital income and the Ports contribution.
- Alliance-defense betrayal penalties can make a treasury negative.
- An Italian build position is consumed even when its 20% reward roll fails.
- Peace score remains informational and does not enforce a fairness limit.
- Legacy private land-purchase handlers remain present although their old
  command branch is not registered.

## Compatibility risks

- Legacy nations without a saved join policy now retain the pre-pass
  invite-only behavior by migrating to `INVITE_ONLY`. Explicitly saved `OPEN`,
  `INVITE_ONLY`, and `CLOSED` policies remain unchanged; invitations expire
  after 600 seconds.
- Save data is written as version 1 after first successful load. A
  `nationwars.json.pre-migration-v0.bak` is created first.
- Metadata pins Minecraft 1.21.1 and accepts NeoForge `[21.1.227,)`, avoiding
  an exact-version rejection on the Testing instance's NeoForge 21.1.235.
  OPAC remains required in `[0.27.5,0.28)`.
- No Git repository exists in this source folder, so the requested review units
  are represented by separated file groups rather than actual commits.

## Verification

- `gradlew clean build`: passed.
- Fresh non-cached unit/validation run: 34 tests passed.
- Dedicated-server smoke test: reached `Done` on Minecraft 1.21.1 / NeoForge
  21.1.227 with OPAC 0.27.5; Nation Wars registered and activated its OPAC party
  system and migrated the development save from data version 0 to 1.
- Built JAR contains all three language catalogs.
- Corrective-pass built JAR: 331,815 bytes; SHA-256
  `109220CB3BDB7EF209B33A99D6A7F1E9FDF5B90C8EFF13F381D7A365166E88BD`.
- Spanish-localization built JAR: 345,704 bytes; SHA-256
  `9A11559DC123DE6A6823AFC6751117215F96510F47D40A1583DE8D0F2268C60F`.

## Translation coverage

- Total keys: 720.
- English keys: 720.
- Romanian keys: 720.
- Spanish keys: 720.
- Missing keys in any catalog: 0.
- Duplicate keys: 0.
- Placeholder mismatches: 0.
- Suspicious fixed player-facing English literals: 0.
- Statically unreferenced keys reported: 90. These are expected dynamic
  doctrine, ideology, spy mission/status, intel-field, and join-policy keys.
- Remaining `Component.literal` calls: 18, all for runtime nation/player names,
  coordinates/dimensions, command text, custom datapack text, or blank panes.

## Gameplay values and formulas confirmed unchanged

- Player starting money $50; nation starting treasury $250.
- Claim base $100; +10% per existing claim after the first; American distance
  +3% per Manhattan chunk; doctrine claim multiplier unchanged.
- Maintenance $8 per claim when claims exceed one; occupied claims 2x;
  French declared-war 1.5x and Romanian lost-core +0.1 modifiers unchanged.
- Capital income $12/min; non-USA upgrades +$6/min; city $3/min; British coast
  contribution 0.25x capital income.
- Upgrade costs $1,000 / $1,500 / $2,000 / $2,500 and five free claims each.
- Justification base 90s, German -40s, Romanian defender +30s, minimum 10s.
- Doctrine capture bases and all French, British, Italian, Soviet, terrain,
  recapture, and attacker-count modifiers retain their original order/values.
- Peace rejection cooldown 300s; Romanian special leave cooldown 1,800s.
- Spy travel, mission, counterspy, and 60s recovery timings and all success
  chances are unchanged.
- Market multipliers/fees, peace costs, surrender shares, alliance behavior,
  guarantees, claims, war transitions, OPAC ownership, and immediate save call
  timing are unchanged.

## Recommended commit breakdown

1. `docs: capture Nation Wars 0.4.0 behavior contract`
2. `feat(i18n): localize all player-facing Nation Wars text`
3. `test(i18n): validate catalogs, placeholders, and literals`
4. `refactor: extract command registration and gameplay calculations`
5. `feat(persistence): add versioned migration and save coordinator`
6. `test(persistence): cover recovery, migration, unknown fields, and refs`
7. `security: gate and audit destructive debug commands`
8. `feat(nations): add INVITE_ONLY-default optional join policies`
9. `fix(opac): backup, validate, and roll back configuration edits`
10. `chore(metadata): pin Minecraft and allow compatible NeoForge updates`
11. `test: add formula, policy, OPAC, and server smoke coverage`
