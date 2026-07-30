# Changelog

## 0.5.1

### Added

- `/configure MaintenanceMultiplierr <amount>` multiplies all maintenance
  after doctrine effects. Its default is `1.0`.
- `/configure ClaimCostMultiplier <amount>` multiplies claim prices after the
  doctrine claim-cost effect. Its default is `1.0`.
- `/configure IncomeMultiplier <amount>` multiplies the active capital portion
  of passive national income after doctrine effects. Its default is `1.0`.
- `/configure MemberIncome <amount>` limits how many nation members are counted
  by the per-member passive-income term. Its default is `10`.
- `/configure Stealing <true|false>` controls paid protected-container and door
  access by members of foreign nations. It defaults to `true`; nationless
  players retain access when it is disabled.

### Changed

- Maintenance is now `(claim count - one owned capital) * $8`, then doctrine
  and global maintenance multipliers are applied. The existing extra occupied-
  claim premium remains.
- Passive member income counts only the first ten members by default.
- United States: Isolation and city claims were removed. Capitalism was renamed
  American Dream and now gives 1.5x active capital passive income. Wall Street
  Crash removes $200 from the national treasury for each claim lost during war.
- United Kingdom: Ports and Sea Lion were removed. Colonial Manpower doubles
  passive member income. Urban Sprawl multiplies maintenance by 1.5 when the
  nation has at least five claims per member.
- Italy: Alpes was removed. War Propaganda multiplies maintenance by 0.8 while
  Italy participates in an active war.
- Capturing a capital no longer causes automatic capitulation or ends a war.
  Zero remaining territory still eliminates the nation.
- Nations cannot send or apply trade offers while the two nations are at war.
- `/nationwarsdev` was removed; `/nwdev` remains the development command root.

### Removed

- `/nation city` and all city-purchase behavior.
- Live city, port, coastal capture, and hill/mountain capture modifiers. Old
  serialized city/coast fields remain readable so existing saves are not
  damaged.

### Fixed

- `ScorchedEarth=true` now grants the temporary OPAC bypass early enough for
  physical hand/tool destruction in enemy wartime claims, not only explosions.

## 0.5.0

### Added

- Complete Polish localization, including commands, menus, doctrines,
  espionage, diplomacy, war, peace, trade, and the puppet system.
- Puppet relations can be created voluntarily with `/puppet propose`,
  `/puppet accept`, and `/puppet reject`, or imposed on the peace-deal receiver
  by selecting the new Puppet term.
- `/puppet` reports the executing owner's master, independence points, lost
  independence wars, frozen status, and direct puppets. A country may control
  multiple direct puppets and each puppet has at most one master. Cycles are
  rejected. If an existing master is puppeted, its current direct puppets are
  released; it may acquire new direct puppets later if no cycle is created.
- Puppets begin at 100 independence points. `/puppet agitate` adds 10 and the
  master may use `/puppet pacify <country>` to remove 10; each action has its
  own 600-second cooldown.
- Accepted foreign trades and rejected master offers add one independence
  point. A one-sided gift from the master to the puppet removes one. These
  trade-derived effects share a 120-second per-puppet throttle; trades still
  complete while the point effect is cooling down.
- Puppets may claim at 50 or more points, may use `/puppet war` above 150, and
  may use `/puppet liberate` or `/puppet automate` at 200. A master may use
  `/puppet annex <country>` at 0 points or after three lost independence wars.
  Reaching a threshold never performs an automatic release or annexation.
- `/puppet release <country>` lets a master release a direct puppet. Release is
  refused during that puppet's active independence war so the relation and war
  cannot become inconsistent.
- A puppet pays 20% of its generated national passive income to its master each
  ten-minute income cycle, rounded to cents before recurring trade income is
  diverted. `/configure Puppets false` suspends puppet commands, restrictions,
  peace terms, and tax without deleting saved relations.
- Independence wars restore all occupied claims when resolved. A puppet win
  releases the puppet without money or land gains; a loss removes 50 points and
  records one loss. Points are frozen during the war, and a fourth attempt is
  unavailable after three losses. Third nations cannot join either side or
  receive an alliance/guarantee defense call for an independence war.
- `/configure deletenation <country>` is available as an audited
  permission-level-4 administration command and accepts command IDs or full
  country names.
- `/nation info` now reports the capital chunk.

### Changed

- Passive income is now paid every ten minutes as the active capital/structure income
  times the doctrine income multiplier, plus `$8 * max(0, members - 1)`. The
  base capital contribution is $120; existing $6 upgrade increments remain.
- Soviet Collectivity starts with no base capital income, while upgrade income
  and the additional-member income still apply.
- American city claims now contribute 0.2x current capital income. British
  coast claims now contribute 0.1x current capital income.
- Italy's building payout is now $2. Kingdom of the South (formerly Civil War)
  adds 10 seconds only when Italy is recapturing a tracked core claim.
- `/nation create` now accepts no arguments and uses the existing doctrine-menu
  and chat-name flow.
- `/configure` is the only configuration root. `Puppets` defaults to `true`.
  The misspelled `Satelites` alias was removed; correctly spelled `Satellites`
  remains.

### Disabled

- `/nations` is intentionally not registered in 0.5.0.
- The old `/configurate` command spelling is no longer registered.

### Fixed

- With `ScorchedEarth=true`, OPAC claim protection is fully bypassed for enemy
  war territory, including block and entity interaction. Peace-offer and
  respawn locks remain explicit Nation Wars war rules.
- `/nation info` now shows the exact current ten-minute income payout instead
  of a mislabeled ten-times projection.
- Nation deletion moved from the debug-gated `/nation delete` command to
  `/configure deletenation <country>` and now performs an OPAC resynchronization
  after cleanup.

### Preserved and regression-checked

- Italy's Push-over still lets a defender with more claims reject an Italian
  declaration once.
- Declining an alliance or guarantee defense call still costs $250; France pays
  $750. France's offensive-war maintenance remains 1.5x.
- Soviet market purchases remain 1.25x and Soviet defense still doubles with
  fewer than two enemy players present.
- Romania still gains a stacking +0.1 maintenance multiplier for each lost
  tracked core claim.

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
