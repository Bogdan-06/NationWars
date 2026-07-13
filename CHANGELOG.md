# Changelog

## 0.4.0

### Added

- `/configurate` and `/configure` operator commands for Nation Wars server
  settings. Boolean settings accept `true/false`, `t/f`, `yes/no`, `on/off`,
  and `1/0`.
- Config toggles: `LimitedDoctrines`, `DisableEspionage`, `ScorchedEarth`,
  `InstantWar`, `NoMercy`, `Factions`, `Guarantees`, `LeaveNation`,
  `RejoinNation`, `Satellites`, `Colonialism`, `ClaimNether`, `ClaimEnd`,
  `SpawnProtection`, and `AllowTrade`.
- `/configurate DisableDoctrine <doctrine> [true|false]` to disable or
  re-enable individual doctrine choices.
- `/nation leave` for non-leader members when `LeaveNation` is enabled.
- `/nation kick <member>` for nation leaders. Players who leave or are kicked
  cannot rejoin that same nation unless `RejoinNation` is enabled.
- `/nation invite <player>` and invite-only nation joining.
- `/nation upgrade`, with four treasury-funded levels that add five free claims
  and $6/minute of capital income per level. The United States cannot upgrade;
  the Soviet capital starts at $0/minute but gains upgrade income normally.
- Nation-to-nation 20-minute truces through `/alliance truce offer`, `accept`,
  `reject`, and `renew`, including a warning during the final minute.
- `/nation trade <country>` chest UI replacing `/nation buyclaim`. Leaders can
  offer/request money, non-capital claims, and recurring income; the other
  leader can accept or reject from the same UI. Recurring payments are diverted
  from passive income rather than withdrawn from the treasury.
- Natural coast detection when claims are created. Ocean, river, and beach
  biome checks within an 8-block border mark claims as coast claims.
- Nether/End claim controls and overworld spawn-protection radius.

### Changed

- `/spy` is hidden/disabled while `DisableEspionage` is enabled.
- `/peace` is hidden/disabled while `NoMercy` is enabled.
- `/alliance` and `/alliances` are hidden/disabled while `Factions` is
  disabled.
- Guarantees can be disabled. When enabled, guarantees now create a defense
  call instead of instantly joining the war, so the defender can be accepted or
  declined through `/war defend` and `/war declinedefense`.
- Declining an allied or guaranteed defense call now applies a $250 betrayal
  penalty. France pays 3x that penalty through Casus Foederis.
- If a peace offer is pending, claim capture pauses for both sides.
- `ScorchedEarth=false` blocks wartime block breaking, building, explosions,
  and fluid placement in enemy claims while still allowing the war itself.
- `InstantWar=true` lets leaders use `/war declare <country>` without first
  running `/war justify`.
- `/nation info <name>` is controlled by `Satellites`; `/nation info` for your
  own nation still works.
- `Colonialism=false` requires later claims to border national territory;
  `AllowTrade=false` disables `/nation trade`.
- `/openpac-parties` is blocked with a message telling players to use
  `/alliance`.

### Doctrine changes

- Italy: Developed Infrastructure gives Speed II on owned claims while at
  peace, and Speed I while the nation is in an active war.
- United States: Capitalism city claims now produce 0.25x capital income after
  paying the claim cost.
- United Kingdom: Ports now use stored coast claims and produce 0.25x capital
  income, including a coastal capital. Sea Lion makes coast claims capture 10
  seconds faster.
- Soviet Union: maintenance multiplier is now 0.5x. Yellow Curtains remains
  1.25x market buy cost, and Great Patriotic War still doubles capture time
  unless two attackers are present.
- France: No War Support now raises maintenance to 1.5x only for wars France
  declared.
- Romania: Iron Guard now stacks by unique lost core claim, adding +0.1x
  maintenance per lost core claim.

### Fixed

- Nation member names are now remembered on login/join so `/nation kick <name>`
  works without requiring UUIDs.
- Claim-name display continues to sync through the Nation Wars OPAC party
  bridge after new claims and transfers.
- Nation, claim, and war displays consistently use nation names rather than
  player-owned OPAC party names.
- Spies are reconciled after reloads and always enter recovery after a resolved
  mission before returning to stationed duty.
- Trade, market, and peace interfaces reject stale or duplicated offers instead
  of applying outdated menu state.
- Captured territory remembers its original owner, cannot be unclaimed or
  traded while occupied, and is restored correctly on surrender and multi-party
  war exits.
- Alliance, guarantee, truce, and voluntary-war-join rules can no longer be
  bypassed to join conflicting sides or overlapping wars.
- Peace offers now display incoming demands from the correct side, freeze
  hostile claim actions while pending, and cannot mutate a completed war.
- Economy and doctrine overrides reject non-finite values, and market purchases
  and trade transfers are committed atomically.

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
