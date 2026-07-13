# Nation Wars 0.4.0 current-behavior contract

This document records the behavior observed in the pre-maintenance 0.4.0
source. It is the compatibility contract for the localization, reliability,
and maintainability pass. Values and rules in this document must remain stable
unless the requested work explicitly authorizes a change.

Baseline archive:
`nationwars-0.4.0-pre-quality-pass_2026-07-12_161732.zip`  
SHA-256: `0FED1D4066572E70889BAF4F978200701E49AA84F0E5C409BF0117E8C41C6931`

## Runtime and dependencies

- Mod ID: `nationwars`; mod version: `0.4.0`.
- Java 21, Minecraft 1.21.1, NeoForge 21.1.227.
- Open Parties and Claims (OPAC) 0.27.5 is a required runtime and compile
  dependency; metadata currently accepts 0.27.5 through versions below 0.28.
- Pre-quality-pass metadata advertised Minecraft `[1.21.1,1.22)` and NeoForge
  `[21.1.227,)`; at baseline only Minecraft 1.21.1/NeoForge 21.1.227 had been
  smoke-tested in this project.
- All gameplay code is common/dedicated-server compatible. There are no
  language resources in the baseline; all fixed player text is literal English.

## Startup, ticking, and shutdown

- On `ServerStartedEvent`, Nation Wars loads its JSON config, activates and
  synchronizes its OPAC party system, loads world data, migrates coast markers,
  refreshes tab-list names, and schedules income, maintenance, and OPAC retries.
- Passive income first runs 1,200 server ticks after startup and then every
  1,200 ticks (normally one minute).
- Maintenance first runs 12,000 server ticks after startup and then every
  12,000 ticks (normally ten minutes).
- Once per second, truces expire/warn, disabled defense calls are cleared, and
  pending peace deals are cleared if `NoMercy` is enabled.
- Capture progress is processed from player ticks once per second and decays
  every 40 server ticks when the attacker is absent.
- Shutdown immediately saves Nation Wars data and clears capture bars, local
  cooldowns, respawn locks, and temporary OPAC full passes.

## Commands and permissions

All normal player roots require permission level 0. Subcommands that act for a
nation generally also require the executing player to be that nation's owner.

| Command | Baseline behavior |
| --- | --- |
| `/money` | Shows the executing player's balance. |
| `/market` | Opens the paged market. |
| `/market sellhand [price]` | Lists the full held stack at the supplied price or appraised default. |
| `/market cancel <id>` | Returns and removes the seller's own listing. |
| `/nations` | Opens the paged nation browser. |
| `/nation doctrines [list]` | Opens doctrine UI; `list` prints doctrine values and perks. |
| `/nation create [name] [doctrine]` | GUI creation, chat-name flow, or direct creation. |
| `/nation join <name>` | Baseline source requires an existing nation invitation and applies the former-member restriction. |
| `/nation invite <player>` | Nation owner creates a non-expiring invitation for an online player. |
| `/nation leave` | Non-owner member may leave only when `LeaveNation=true`; former membership is recorded. |
| `/nation kick <member>` | Owner removes a member by recorded name or UUID; former membership is recorded. |
| `/nation info [name]` | Own nation is always visible; named lookup requires `Satellites=true`. |
| `/nation claim` | Owner claims the current chunk subject to dimension, spawn, adjacency, cost, and OPAC checks. |
| `/nation unclaim` | Owner unclaims a non-capital, non-occupied claim. |
| `/nation city` | United States owner converts an owned non-occupied claim into a city by paying its current claim cost. |
| `/nation trade <country>` | Opens bilateral trade UI when `AllowTrade=true` and the nations are not fighting. |
| `/nation upgrade` | Owner buys one of four upgrades; unavailable to USA. |
| `/nation guarantee [remove] <country>` | Adds/removes an external guarantee when enabled. |
| `/nation balance` | Shows player balance and nation treasury. |
| `/nation deposit <amount>` | Atomically transfers player money to the current nation treasury. |
| `/nation syncopac` | Permission level 2; reactivates/synchronizes Nation Wars OPAC claims. |
| `/alliance create/invite/accept/kick/info` | Alliance management when `Factions=true`. |
| `/alliances` | Lists alliances when `Factions=true`. |
| `/alliance truce` | Lists active truces. |
| `/alliance truce offer/renew/accept/reject <country>` | Owner-managed bilateral non-aggression truces. |
| `/wars` and `/war` | Open the paged war browser. |
| `/war justify/declare/accept/reject` | War justification and declaration flow. |
| `/war join/acceptjoin/rejectjoin` | Voluntary multi-nation war joining. |
| `/war defend/declinedefense` | Alliance/guarantee defense-call response. |
| `/war leave <country>` | Romania's King Michael's Coup safe-leave ability. |
| `/war status` | Prints all wars and justifications. |
| `/peace <country>` | Opens peace UI unless `NoMercy=true`. |
| `/peace reject <country>` | Rejects an incoming peace offer and applies the proposer cooldown. |
| `/surrender <country>` | Applies primary or joined-nation surrender behavior. |
| `/spy create/hire/set/mission/info/status` | Owner-only espionage agency operations unless disabled. |
| `/configurate` and `/configure` | Permission level 2; reads/writes server settings. |
| `/nwdev` and `/nationwarsdev` | Baseline permission level 2; set money/treasury/doctrine, finish spy missions, save, or sync OPAC. |
| `/openpac-parties` | Removed/replaced and execution-blocked, including namespaced aliases; directs players to `/alliance`. |

Command syntax and ordinary command permissions are fixed by this contract.
Only the requested development-command safety and optional join-policy stages
may add commands or alter related permissions.

## Configuration defaults

The baseline file is `config/nationwars-server.json`; writes use a temporary
file, an atomic replacement where available, and one `.bak` copy.

| Setting | Default | Effect |
| --- | ---: | --- |
| `enforceDoctrineLimits` / `LimitedDoctrines` | `true` | Enforces doctrine counts. |
| `defaultDoctrineLimit` | `1` | One nation per doctrine unless overridden; 0 means unlimited. |
| `setOpenPacPrimaryPartySystem` | `true` | Lets Nation Wars select its OPAC party system and party-owned claims. |
| `disableEspionage` | `false` | Hides/disables `/spy`. |
| `scorchedEarth` | `true` | Allows destructive enemy-claim actions during active war. |
| `instantWar` | `false` | A declaration still needs completed justification. |
| `noMercy` | `false` | Peace commands and deals remain enabled. |
| `factions` | `true` | Alliances are enabled. |
| `guarantees` | `true` | Guarantees and related defense calls are enabled. |
| `leaveNation` | `false` | `/nation leave` is disabled. |
| `rejoinNation` | `false` | Former members cannot rejoin the same nation. |
| `satellites` | `false` | Named `/nation info <name>` is disabled. |
| `claimNether` / `claimEnd` | `false` | Claims in those dimensions are disabled. |
| `colonialism` | `false` | Later claims in a dimension must touch national territory. |
| `allowTrade` | `true` | Nation-to-nation trade is enabled. |
| `spawnProtection` | `200` | Claims intersecting a 200-block radius around Overworld spawn are rejected. |
| `disabledDoctrines` | empty | No doctrine is disabled. |

`/configurate Satelites` is intentionally retained as a misspelled compatibility
alias for `/configurate Satellites`.

## Nation membership and identity

- A nation ID is the lowercase alphanumeric form of its display name. Creation
  requires at least three letters/numbers and a unique ID.
- Creating a nation claims the current chunk as its capital and first core,
  gives the treasury $250, records the owner as first member, and gives the
  owner their normal $50 player balance if they have no balance entry.
- Doctrine availability is evaluated at creation time against disabled IDs and
  configured doctrine limits.
- The baseline currently requires `/nation invite` before `/nation join`.
  Invitations do not expire. The quality pass extended this with persisted
  `OPEN`/`INVITE_ONLY`/`CLOSED` policies; the corrective pass keeps the baseline
  invite-only default while applying the new 600-second invitation expiry.
- Leaving and kicking never remove the nation owner and record former members.
  `RejoinNation=false` blocks those former members from the same nation.
- Tab-list names are `[Nation] Player` in gold/white or `[No Nation] Player` in
  dark gray/gray. Nation and player names remain dynamic and untranslated.

## Economy contract

- Money is rounded to two decimals after transactions.
- A new player ledger entry starts at **$50**. A new nation treasury starts at
  **$250**.
- Base capital income is **$12/minute**. Each nation upgrade adds **$6/minute**
  and five free claims. Upgrade prices are **$1000, $1500, $2000, $2500**.
  There are at most four upgrades.
- USA cannot purchase upgrades and always has $12/minute capital income.
- Soviet capital base income is $0/minute, but its upgrades add $6/minute each.
- A paralyzed capital or city/coast claim contributes no passive income.
- British Ports add **25% of that nation's current capital income** for every
  owned coast claim, including a coastal capital.
- Each USA city claim adds **$3/minute** and costs that claim's current claim
  cost when created.
- Doctrine `incomeMultiplier` is applied after capital, port, and city income
  are summed.
- Recurring trade payments are diverted from the payer's generated passive
  income, never directly from treasury. If obligations exceed income, available
  income is divided proportionally and rounded; recipients receive only the
  amount actually diverted.
- Maintenance runs every ten minutes. A nation with zero or one claim owes $0.
  Otherwise: `claims * $8 * doctrineMaintenanceMultiplier`, plus another
  `$8 * multiplier` for every occupied claim it holds (occupied claims are 2x).
- France multiplies its maintenance by **1.5** only while it is the primary
  attacker in an active war.
- Romania adds **0.1** to its maintenance multiplier for each unique lost war
  core. Legacy `lostCoreTerritory=true` counts as one if no set entries exist.
- Soviet base maintenance multiplier is **0.5**; Germany is **1.35**; others
  are **1.0** before the conditional doctrine effects above.
- Failed maintenance removes one non-capital border claim; if none is eligible,
  no claim is removed. The unpaid treasury is not additionally reduced.
- Carol II Lifestyle drains a random **$10-$50** every maintenance interval
  while Romania has not used King Michael's Coup against all four ideologies.
- Protected doors, trapdoors, gates, chests, barrels, and shulker boxes cost a
  non-member **$50** to open outside war/raid access. The same position has a
  60-tick local charge cooldown.
- Mining rewards: diamond ore $12, emerald $10, ancient debris $20, gold $6,
  lapis/redstone $4, iron $3, copper/quartz $2, coal $1, other ores $2, and a
  mature crop $1, multiplied by doctrine income multiplier. Silk Touch ores pay
  $0.
- Italy building rewards are $5 with a 20% chance, at most one attempt per
  world block position and one attempt per player per second, only in owned
  non-occupied territory and excluding the configured common-block list.

### Claim-cost formula

`round($100 * expansion * distance * doctrineClaimMultiplier)` where:

- `expansion = 1 + max(0, ownedClaims - 1) * 0.1`;
- `distance = 1` normally;
- for USA in the capital's dimension, `distance += ManhattanChunkDistance * 0.03`;
- Soviet doctrine claim multiplier is 0.8; all other defaults are 1.0;
- a free claim consumes one `freeClaimsRemaining` and bypasses the money cost.

Default held-stack appraisal is count multiplied by: diamond $30, emerald $24,
gold ingot $10, iron ingot $5, copper ingot $2, coal $1, listed crops $1.50,
and other items $0.20. A manually supplied market price bypasses appraisal.

Market buyers pay listing price times their doctrine buy multiplier. Sellers
receive listing price times their sell multiplier, capped at the buyer price.
USA buys at 0.8x; Soviet buys at 1.25x; default multipliers are 1.0.

## Claims and OPAC protection

- Claims use `dimension:x:z`; adjacency means Manhattan distance one in the same
  dimension.
- Overworld claims intersecting the configured spawn radius are rejected.
  Nether/End claims require their respective configuration toggle.
- With `Colonialism=false`, the first non-occupied claim in a dimension may be
  isolated; later claims must touch a non-occupied national claim.
- New claims must be free in both Nation Wars and OPAC. Nation owners are the
  OPAC record owners, while the Nation Wars primary party adapter makes the
  party/claim display use the nation name.
- Capitals and claims occupied in an active war cannot be unclaimed or traded.
- Coast detection samples a four-block grid over the chunk plus an eight-block
  border in the Overworld and recognizes warm/lukewarm/deep/ocean/cold ocean,
  river, beach, snowy beach, and stony shore biomes.
- Enemy beds are invincible during war. A player who dies while their nation is
  in an active war receives a 20-second attack/defense/action lock on respawn.
- When a peace offer is pending between opposing participants, capture and
  hostile break/place/fluid/container/explosion actions are paused.
- `ScorchedEarth=false` blocks destructive hostile actions during war;
  `ScorchedEarth=true` allows them, subject to peace and respawn locks.
- Active RAID effects grant temporary protected-claim interaction access.

## Doctrine defaults and effects

| ID | Nation | Ideology | Claim x | Income x | Maintenance x | Free claims | Base capture | Other baseline effects |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| GER | Germany | Fascist | 1.0 | 1.0 | 1.35 | 4 | 35s | Justification 40s faster; counterspy blocks 50%. |
| SOV | Soviet Union | Communist | 0.8 | 1.0 | 0.5 | 4 | 50s | No base capital income; market buy 1.25x; defense doubles unless two attackers are present. |
| USA | United States | Democratic | 1.0 | 1.0 | 1.0 | 4 | 50s | Pacifist; distance claim scaling; market buy 0.8x; city claims; no upgrades. |
| FRA | France | Democratic | 1.0 | 1.0 | 1.0 | 4 | 50s | Defender adds 25s; five bonus $300 spies; 3x betrayal cost; offensive-war maintenance 1.5x. |
| ENG | United Kingdom | Democratic | 1.0 | 1.0 | 1.0 | 6 | 50s | Ports; coastal defense is captured 10s faster; peace-offer fee 3x. |
| ITA | Italy | Fascist | 1.0 | 1.0 | 1.0 | 4 | 50s | Owned-land Speed II at peace/Speed I at war; building payouts; hills add 15s defense; recapture adds 10s; larger defender can reject once. |
| ROM | Romania | Non-aligned | 1.0 | 1.0 | 1.0 | 4 | 50s | Safe war leave once per enemy ideology with 30m cooldown; enemies justify 30s longer; lost-core maintenance; Carol II drain. |

Doctrine JSON datapacks may override these existing fields under
`nationwars/doctrines`. Invalid/non-finite overrides are rejected and defaults
are restored before each reload.

## Alliances, guarantees, and truces

- One nation can belong to at most one alliance. The alliance leader nation
  creates/invites/kicks; invited nation owners accept.
- New alliance members cannot be admitted if their active-war relationships
  conflict with existing members.
- Guarantees are only for countries outside the guarantor's alliance.
- An attack creates defense calls for eligible allies and guarantors; it does
  not auto-join them. Accepting joins the caller's side if no protected or
  conflicting relationship would be violated.
- Declining a valid defense call subtracts **$250** from treasury. France pays
  **$750**. The balance may become negative; that is baseline behavior.
- A truce lasts **1,200 seconds (20 minutes)**. Offers expire after **300
  seconds**. Renewal can be proposed only in the final **60 seconds** and adds
  another 1,200 seconds to the later of the current expiry or acceptance time.
- Both nations receive a final-60-second warning and an expiry notification.
- A truce/offer is blocked by war, justification, opposing defense calls, an
  existing active truce (except valid renewal), or another pending offer.

## War, capture, capitulation, and surrender

- Base justification is **90 seconds**; Germany subtracts 40 seconds and a
  Romanian target adds 30 seconds, with a hard minimum of 10 seconds.
- USA cannot justify or declare wars but may join ongoing conflicts.
- Declaration requires at least one target member online. `InstantWar=true`
  creates an immediately ready justification but preserves all other checks.
- Italy's larger-territory rejection ability lets a larger defender reject that
  attacker once; the attacker/defender rejection pair is persisted.
- War sides support voluntary join requests and alliance/guarantee defense
  calls. Cross-side protected relationships and conflicting active wars block
  admission.
- Each participant's core snapshot is the claims owned when it joins the war.
  Occupied claims retain original-owner provenance and cannot be captured by a
  second active war.
- Capture base uses the **attacker doctrine's capture seconds** times the
  defender's defense multiplier. Then: France defender +25s; British coast
  defender -10s; Italian hill/mountain defender +15s; Italian attacker
  recapturing tracked land +10s; Soviet defender doubles the result with fewer
  than two attackers. Final capture time is rounded with a 10-second minimum.
- A defending member in the claim pauses capture. A pending peace offer also
  pauses capture without resetting accumulated seconds.
- Capturing a capital capitulates the defender and targets 25% (rounded up,
  minimum one) of its war-start core count times surrender multiplier,
  including already captured qualifying cores. A primary defender ends the
  war; a joined defender leaves while the wider war continues.
- Elimination at zero claims deletes the nation and splits its treasury plus
  owner player balance among captors, weighted by captured territory.
- Primary-defender surrender transfers enough eligible cores to reach 25% of
  starting claims, gives the enemy 50% of treasury plus all owner player money,
  and ends the war. Primary-attacker surrender restores its captured claims.
  Joined-nation surrender restores claims involving it and removes only that
  participant.
- Romania may leave safely once per enemy ideology; each use has a 1,800-second
  cooldown. Carol II Lifestyle ends after all four ideologies have been used.

## Peace deals

- `/peace` is owner-only, requires an opposing active-war participant, and is
  disabled by `NoMercy`.
- The six-row UI has 16 paged demand claim slots, 16 paged offer claim slots,
  $100 money controls, optional captured-claim restoration, counteroffer,
  accept/send, clear, and close controls. Incoming offers are read-only until a
  counteroffer is started.
- Capitals and territory occupied by another active war cannot be selected.
- Each selected claim is displayed as 100 score; money contributes one score
  per dollar. Score is informational and does not restrict acceptance.
- Sending costs **$10** times doctrine `peaceOfferCostMultiplier`; UK pays $30.
- A rejected offer gives its proposer a **300-second** retry cooldown.
- Acceptance revalidates exact pending terms, nation/war identity, ownership,
  capitals, treasuries, infiltration blocks, and other-war occupation before an
  atomic state save and OPAC claim synchronization.
- White peace is an empty deal. A primary pair ends the war; peace involving
  joined participants removes only eligible joined nations after applying
  selected terms.

## Nation trade

- The six-row trade UI has 16 paged request claim slots, 16 offer claim slots,
  $100 treasury controls, recurring-income controls, accept/send, reject/clear,
  and close controls. Incoming offers are read-only.
- Left/right click changes a recurring term by $1/min; shift-click changes it by
  $10/min. Accepted recurring terms replace the existing bilateral agreement;
  explicit $0/$0 ends it.
- Capital and active-war-occupied claims are not tradable. Sending and
  accepting revalidate identities, ownership, war state, treasuries, and
  infiltration blocks. Exact snapshots prevent stale menu acceptance.
- A single pending bilateral offer is stored; a new offer from either direction
  replaces the old one.

## Espionage

- Agency creation costs **$3,500**. Base limit is 10 spies; France has five
  additional spies. Base spy `n` costs `$250 + (n-1)*$150`; French spies 11-15
  each cost $300.
- `/spy set` sends idle/stationed spies to a country with **60 seconds** travel.
- Every completed, failed, blocked, or ownership-cancelled non-counterspy
  mission enters **60 seconds** recovery, then returns to stationed duty in the
  same surviving country. Orphaned mission state is reconciled on load.
- Counterspy assignments run for 900 seconds after their 60-second mission and
  then enter normal recovery. German counterspies block with 50% probability;
  other counterspies block eligible missions with 100% probability unless
  disabled by infiltration.

| Mission | Cost | Mission time | Failure | Chunks | Result duration/effect |
| --- | ---: | ---: | ---: | ---: | --- |
| counterspy | $0 | 60s | 0% | 1 | Defends own claim for 900s. |
| doctrine | $250 | 90s | 15% | 0 | Reveals doctrine/ideology. |
| treasury | $200 | 90s | 15% | 0 | Reveals treasury. |
| members | $100 | 60s | 15% | 0 | Reveals members. |
| faction | $200 | 90s | 15% | 0 | Reveals alliance and guarantees. |
| size | $100 | 60s | 15% | 0 | Reveals claim count. |
| scout | $300 | 120s | 15% | 3 | Reveals selected territory information. |
| infiltrate | $500 | 120s | 30% | 1 | Disables counterspy for 600s; capital also blocks treasury spending. |
| paralyze | $300 | 120s | 30% | 1 | Stops income from claim for 600s. |
| steal | $700 | 180s | 40% | 1 | Applies existing claim-based theft logic. |
| raid | $1,200 | 180s | 50% | 1 | Removes protected interaction restriction for 300s. |

RAID must begin and finish while a target member is online and while target
size remains between half and twice the attacker's claim count. Intelligence
refresh costs $100 for every currently known field.

## GUI navigation contract

- Nation creation and doctrine menus are three rows with doctrines in slots
  10-16. Nation browser, market, war browser, spy status, spy mission, trade,
  and peace menus are six rows.
- Nation and market browsers show 45 entries per page with previous/page/next
  controls at 45/49/53. War browser shows 36 entries and command hints along the
  bottom row, with navigation at 50/51/53.
- Spy mission navigation is target -> mission -> optional claim selection;
  scout requires exactly three distinct claims. Existing back, pagination, and
  confirmation slots must not move.
- Trade and peace use their existing left/right claim columns, page controls,
  bottom-row money/action controls, and read-only incoming-offer flow.
- Shift-click does not move inventory items in Nation Wars display menus.

## Persistence contract

- World data is JSON at `world/data/nationwars.json`. Baseline state has no
  `dataVersion`; this is version 0 for migration purposes.
- The state stores nations, memberships/names/player balances, former members,
  invitations, claims/coasts, wars and capture provenance, alliances, truces,
  recurring income, guarantees, peace cooldowns, spy state/effects/intel,
  market and trade offers, Italian rewarded positions, and next IDs.
- Every existing mutating operation saves immediately at its current call site.
  This pass must preserve that timing while routing it through a coordinator.
- Save writes `nationwars.json.tmp`, copies the prior main file to one `.bak`,
  then atomically replaces the main file where supported. Temporary files are
  deleted in `finally`.
- A malformed main file is moved to a timestamped `.corrupt-*` file. The `.bak`
  is then attempted. A malformed file is never knowingly overwritten.
- Load normalization initializes missing optional collections, repairs IDs and
  membership mappings, removes invalid references, migrates legacy server-tick
  deadlines to wall-clock-derived persistent ticks, and reconciles spies,
  wars, truces, and recurring payments. The normalized state is saved
  immediately after load.
- Stage 4 may add `dataVersion=1`, an identity migration, a pre-migration
  backup, and `SaveCoordinator`, but may not change save timing or identifiers.

## OPAC integration contract

- Nation Wars registers party system ID `nationwars` and normally makes it the
  primary OPAC party system.
- Runtime OPAC values set `maxPlayerClaims=0` and `partyOwnedClaims=true`.
- Nation claims are mirrored using the nation owner's UUID. Party membership,
  alliance relation, party-edit permissions, and displayed party name come from
  Nation Wars state.
- Claim create/unclaim/transfer, startup/login retries, and manual sync keep
  OPAC claims and nation display names synchronized.
- Temporary OPAC full passes are scoped to a protected action and revoked on
  cleanup/expiry/shutdown.
- Existing non-Nation-Wars OPAC claims are not overwritten. Legacy misplaced
  mirrors are removed only when their recorded pattern matches.

## Finding classification

### Confirmed implementation/reliability bugs or safety defects

1. Destructive development commands are always registered at permission level
   2 and have no production gate or audit log. Stage 5 explicitly authorizes
   correcting this safety defect.
2. OPAC TOML edits use direct regular-expression replacement without a backup,
   validation, or rollback. Stage 7 explicitly requires a safe replacement.
3. World data has no explicit schema version, so normalization and an immediate
   save cannot distinguish an intentional migration from ordinary repair.
   Stage 4 explicitly requires versioned migration and pre-migration backup.

### Questionable design or compatibility risks preserved for now

- The quality pass initially interpreted the optional policy stage as requiring
  an `OPEN` default. The pre-pass implementation was invite-only, so the
  corrective pass treats that default change as a confirmed compatibility
  regression and restores `INVITE_ONLY`.
- Maintenance charges every claim once a nation owns more than one, rather than
  charging only claims after the first. This is preserved as gameplay.
- A coastal British capital receives both capital income and the additional
  Ports contribution. This is preserved as gameplay.
- Betrayal penalties may make a nation treasury negative. This is preserved.
- An Italian building position is permanently marked after the first reward
  attempt even when its 20% roll fails. This is preserved.
- Peace score is informational and does not enforce a fairness limit. This is
  preserved.
- Old private `/nation buyclaim` handler code remains even though the command is
  no longer registered. It is not deleted during localization and will only be
  removed if later extraction proves it unreachable with tests.
- Final metadata pins Minecraft 1.21.1 and accepts NeoForge 21.1.227 or newer so
  compatible 1.21.1 NeoForge updates, including the Testing instance's
  21.1.235, are not rejected solely by an exact-version lock.

### Intentional gameplay behavior

All numeric formulas, doctrine effects, command syntax, menu navigation,
immediate save timing, war/claim/alliance/peace behavior, and OPAC behavior
described above are intentional for this pass and must remain unchanged except
for the expressly allowed debug-command and join-policy changes.

## Authorized 0.4.0 maintenance addendum

- Save documents now carry `dataVersion: 1`. Missing versions are treated as
  version 0 and receive an identity migration after a dedicated pre-migration
  backup. Immediate save calls and the `.tmp`/`.bak` replacement flow remain.
- Existing nations without a saved policy migrate to `INVITE_ONLY`. New nations
  use the NeoForge technical configuration default, which is also
  `INVITE_ONLY`. Explicitly saved `OPEN`, `INVITE_ONLY`, and `CLOSED` values are
  preserved. Invitations expire after 600 seconds; `OPEN` permits joining
  without one and `CLOSED` rejects joining.
- `/nation accept`, `/nation reject`, and `/nation joinpolicy` are the isolated
  policy commands explicitly authorized by Stage 6.
- `/nwdev` and `/nationwarsdev` now require permission level 4, are gated by a
  development-aware technical setting, and audit destructive uses. Ordinary
  player command permissions are unchanged.
- Technical settings live in NeoForge's `nationwars-technical.toml`; gameplay
  and balance settings remain in the existing Nation Wars server JSON.
