# Nation Wars 0.4.0 maintenance plan

This pass is compatibility-first. `docs/current-behaviour.md` is the behavior
contract: existing commands, navigation, values, formulas, timers, identifiers,
save timing, and OPAC behavior remain unchanged unless the requested work
explicitly permits a change.

## Checkpoints

1. **Freeze the baseline**
   - Keep the verified pre-change archive outside the version folder.
   - Record commands, permissions, data fields, formulas, timers, doctrine
     behavior, UI navigation, OPAC behavior, and known edge cases.
   - Classify findings as confirmed bugs, questionable design, or intentional
     gameplay. Fix only confirmed compatibility-safe bugs automatically.

2. **Localize without restructuring gameplay**
   - Add `en_us.json` and `ro_ro.json` plus translator context notes.
   - Replace fixed player-facing literals one class or menu at a time, keeping
     dynamic nation/player/claim values as translation arguments.
   - Preserve component styling and menu slots. Build after each related group.
   - Add validation for JSON syntax, duplicate/missing keys, unused keys where
     practical, and suspicious remaining English literals.

3. **Extract stable seams incrementally**
   - First extract pure calculations and validation shared by commands, menus,
     and events; add characterization tests before moving each rule.
   - Then extract focused command registration/handlers and gameplay services.
   - Keep compatibility facades where needed so existing call sites and stored
     identifiers do not change in one large rewrite.
   - Move OPAC code behind `integration/opac` adapters only after its current
     behavior has dedicated tests or a server smoke test.

4. **Version and centralize persistence**
   - Introduce `dataVersion = 1`; treat an absent field as version 0.
   - Add an explicit 0-to-1 identity migration and create a pre-migration
     backup before normalizing or writing migrated data.
   - Route existing immediate saves through one `SaveCoordinator`; do not add
     batching, delays, or debouncing.
   - Add fixture tests for unversioned/current saves, backup recovery,
     interrupted temporary files, optional fields, and invalid references.

5. **Apply the explicitly allowed safety/features changes in isolation**
   - Gate development commands with a development-aware config default,
     permission level 4, and audit logging.
   - Add persisted `OPEN`, `INVITE_ONLY`, and `CLOSED` join policies with an
     `INVITE_ONLY` global and per-nation default, expiring invitations, and
     translated owner commands. Keep this change isolated from general
     refactoring.
   - Move only technical/administrative settings to NeoForge configuration.
     Preserve every gameplay balance value.
   - Make OPAC config edits backup-first, structured where possible, validated,
     and limited to Nation Wars-owned settings.

6. **Verify and package**
   - Expand pure Java characterization tests for doctrine, economy, claims,
     maintenance, capture, war/diplomacy, peace, market, spies, migration, and
     translations.
   - Run `gradlew clean build` and a dedicated-server startup smoke test.
   - Inspect the JAR for both language files and correct metadata.
   - Refresh the 0.4.0 release JAR/source archive only after all checks pass.

## Review units

Each meaningful unit should be independently reviewable and compile before the
next unit begins:

1. behavior documentation;
2. localization infrastructure and validator;
3. command/config text;
4. nation/diplomacy/war text;
5. market/trade/peace text;
6. spy/doctrine/menu text;
7. pure services and calculations;
8. persistence versioning and coordinator;
9. debug-command safety;
10. join policy;
11. OPAC/config/metadata cleanup;
12. final tests, server smoke test, and release packaging.

No unit may change a gameplay value from the behavior contract unless that
change is explicitly authorized by the requested join-policy or debug-command
stages.
