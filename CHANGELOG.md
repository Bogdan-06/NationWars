# Changelog

## 0.3.0

### Added

- Nation guarantees through `/nation guarantee <country>` and
  `/nation guarantee remove <country>`. Guarantors automatically join the
  defender when the guaranteed nation is attacked.
- Complete nation-owned spy agencies:
  `/spy create`, `/spy hire`, `/spy set`, `/spy mission`,
  `/spy info show`, `/spy info update`, and `/spy status`.
- `/spy mission` now opens a country, mission, and claim-selection UI. Missions
  no longer require typed country names or chunk coordinates.
- Nation, market, war, peace-claim, and spy-country interfaces now have paging.
- Spy missions: counterspy, doctrine, treasury, members, faction, size, scout,
  infiltrate, paralyze, steal, and raid.
- Persistent spy intelligence snapshots, counterspies, treasury infiltration,
  paralyzed income claims, and temporary raid protection removal.
- Spies now recover for 60 seconds after missions and automatically return to
  stationed duty instead of disappearing from the mission interface.
- Chest-style spy status interface based on the supplied UI reference.
- `/gamerule LimitedDoctrines true|false`.
- `/wars` overview.
- Operator development commands under `/nwdev` and `/nationwarsdev`.
- Enemy-bed protection during active wars.
- A 20-second capture attack/defense lock after dying and respawning in war.
- OPAC party-owned claims are forced on so claimed chunks display the nation
  name instead of the owner's player name.
- The Nation Wars party adapter now uses OPAC's current API, preventing a
  startup crash when party-owned claims are enabled.
- Persistent countdowns now survive restarts, including war justification,
  spies, mission effects, peace cooldowns, and Romania's war-leave cooldown.
- Nation data now uses atomic saves, rolling backups, malformed-file
  preservation, and stronger normalization.
- Capture boss bars explicitly display the attacking and defending nation names.

### Doctrine changes

- Doctrine IDs are now `GER`, `FRA`, `SOV`, `ENG`, `USA`, `ITA`, and `ROM`.
  Legacy IDs migrate automatically.
- France: White Flag was replaced by Spy Master, adding five $300 spies.
- Germany: 1945 was replaced by Turing's Bombe; German counterspies block 50%
  of attacks instead of 100%.
- United States: Worldwide Economy now lowers market purchase prices without a
  selling bonus. Capitalism and Isolation descriptions match their mechanics.
- United Kingdom, Soviet Union, Italy, and Romania descriptions now match their
  capture, war-rejection, safe-leave, and core-claim mechanics.

### War and correctness changes

- A war can only be declared while at least one target member is online.
- RAID can only start and complete while a target member is online and the
  target has between half and twice the attacker's claims.
- Romania's Iron Guard penalty now activates on the loss of any tracked core
  claim, not only the capital.
- Treasury infiltration blocks voluntary nation spending for its duration.
- Existing OPAC name-display, tab-list, command-routing, owner-permission, and
  Romania cooldown fixes remain included.
- Spy missions recover cleanly when a target disappears and revalidate selected
  claim ownership before applying effects.
- Allied defenders now pause claim capture, and Italian bonuses use actual core
  territory.
- Multi-party surrender returns all relevant captured land, ambiguous war joins
  can select an enemy, and unrelated peace negotiations cannot overwrite one
  another.

### Spy timing and balance defaults

- Agency: $3500.
- Base spies: $250 for the first, then +$150 per additional spy; base limit 10.
- France: five additional spies at $300 each; total limit 15.
- Travel: 60 seconds.
- Counterspy defense: 15 minutes.
- Infiltration and paralysis: 10 minutes.
- RAID protection removal: 5 minutes.
- Intelligence missions without an explicit risk use a 15% failure chance.
