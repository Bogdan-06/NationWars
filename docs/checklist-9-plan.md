# Nation Wars 0.5.0 implementation plan

This plan maps `Checklist 9.txt` and `Puppets.txt` to reviewable changes. The
0.4.0 project was backed up before work and copied into a separate `0.5.0`
version folder.

## Locked interpretations

- Passive income is paid by the ten-minute cycle established by FIX 0.5. Capital starts at
  `$120 × doctrine income multiplier`; each member after the first adds `$8`
  outside that multiplier. Existing `$6` upgrade increments remain.
- `/puppet agitate` and `/puppet pacify` keep their explicit ten-minute
  cooldown. Trade-derived point changes use the separately stated 120-second
  throttle; trades still complete while throttled.
- Reaching 0 or 200 points unlocks a manual command and never causes an
  automatic annexation or liberation.
- Both `/puppet liberate` and the separately named `/puppet automate` perform
  the same 200-point peaceful-independence action.
- Voluntary proposals use `/puppet accept <country>` and
  `/puppet reject <country>` so a proposal is never accepted implicitly.
- A master-to-puppet trade lowers independence by one only when it is a
  one-sided gift: the puppet receives money, claims, or recurring income and
  gives none of those categories.
- Independence wars use dedicated outcomes. All captured claims are restored;
  normal peace, surrender, and capitulation transfers do not leak into them.
- `Puppets=false` suspends puppet actions, restrictions, tax, and new peace
  terms without deleting saved relationships.
- The quoted misspelled `/configure Satelites` alias is removed; the correctly
  spelled compatibility setting remains.
- Annexation transfers the puppet's claims, treasury, and members to its
  master before the puppet nation record is removed.
- A master cannot release a puppet while that puppet's independence war is
  active; refusing the command avoids orphaning the relation-dependent war.
- Each puppet has at most one master and cycles are rejected. A country that is
  already a puppet may later acquire direct puppets; however, when a country
  with existing direct puppets is itself puppeted, those dependents are first
  released as required by the specification.
- Independence wars remain a two-country conflict: voluntary join requests and
  alliance/guarantee defense calls are rejected.

## Work groups

1. Release/config/command delta
   - version 0.5.0 metadata;
   - only `/configure`, add `Puppets=true`, remove `Satelites`;
   - remove legacy `/nation create` arguments and disable `/nations`;
   - visible permission-4 debug roots and audited
     `/configure deletenation <country>` administration.
2. Economy and doctrine delta
   - `$120` base plus `$8` additional members;
   - USA cities 0.2x, British ports 0.1x, Italian building reward `$2`;
   - Italian core-only recapture delay and Kingdom of the South wording;
   - regression tests for already-correct French, Soviet, Romanian, betrayal,
     capital-info, and Push-over behavior.
3. Puppet state and commands
   - data version 2 relation/proposal/cooldown/loss persistence;
   - status, propose/accept/reject, agitate, pacify, liberate/automate, release,
     annex, and independence-war commands;
   - point thresholds, trade effects, claim restrictions, tax, and lifecycle
     cleanup.
4. Peace and war integration
   - configurable puppet peace term;
   - dedicated independence peace, surrender, and capitulation results;
   - release dependents when their master is puppeted, annexed, or deleted.
5. Completion gates
   - complete English, Romanian, Spanish, and Polish localization;
   - migration/integrity/formula/puppet regression tests;
   - `gradlew clean build`, JAR inspection, dedicated-server smoke test, and
     installation into the Testing instance.
