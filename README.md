# Nation Wars

Development project for Nation Wars 0.5.1.

This repository preserves the complete known Nation Wars release lineage. Use
the Git tags and GitHub Releases to view or download a specific version. Source
for releases before 0.2.2 was recovered from the published CurseForge JARs and
is clearly marked as decompiled historical source. See [RELEASES.md](RELEASES.md)
for provenance, file IDs, and checksums.

Nation Wars is a server-side NeoForge mod providing nations, chunk claims,
economy, markets, alliances, war, peace deals, doctrine bonuses, and integration
with Open Parties and Claims.

Version 0.5.0 added voluntary and peace-deal puppeting, independence points,
independence wars, manual release/annexation, and a 20% puppet-income tax. It
also changes the passive-income payout to $120 every ten minutes plus $8 for
each member after the first, adds the capital chunk and current payout to
`/nation info`, streamlines
nation creation, and applies the doctrine changes listed in the changelog.

Use `/configure` as an operator to review server settings. Examples:
`/configure LimitedDoctrines t`, `/configure DisableEspionage false`,
`/configure Puppets true`, `/configure Colonialism false`, and
`/configure SpawnProtection 200`. Version 0.5.1 also provides
`/configure Stealing <true|false>`,
`/configure MaintenanceMultiplierr <amount>`,
`/configure ClaimCostMultiplier <amount>`,
`/configure IncomeMultiplier <amount>`, and
`/configure MemberIncome <amount>`. `MaintenanceMultiplierr` intentionally
retains the checklist's double-r command spelling. The old `/configurate`
spelling and the
misspelled `Satelites` setting alias are no longer registered; the correctly
spelled `/configure Satellites` setting remains available.

Use `/nation create` to open the doctrine menu, then enter the new nation's
name in chat. The former direct `<name> [doctrine]` arguments are intentionally
disabled. `/nations` is also disabled for this version; `/nation info` and the
other nation-management commands remain available.

Nation owners use `/puppet` to view their status and direct puppets. The command
tree includes `propose`, `accept`, `reject`, `pacify`, `agitate`, `liberate`,
`automate`, `release`, `annex`, and `war`. A new puppet starts at 100
independence points. Claiming starts at 50 points, an independence war requires
more than 150, peaceful liberation requires 200, and annexation requires 0
points or three lost independence wars. Thresholds only unlock the matching
command; liberation and annexation are never automatic.

Use `/nation upgrade` to buy up to four upgrade levels. Each level adds five
free claims and $6 per ten-minute income cycle. Use
`/alliance truce offer <country>` to propose a 20-minute non-aggression pact;
either nation can propose renewal before it expires.

Use `/spy mission` to choose a stationed country, mission, and required chunks
through an inventory interface. Scout missions allow selecting three chunks and
large countries support paged claim lists.

After any mission, the spy enters a 60-second `recovering` cooldown and then
automatically becomes `stationed` in the same country again. `/spy status`
shows the remaining recovery time.

Version 0.5.1 removes the city and port income systems, makes an owned capital
free from maintenance, and adds server controls for maintenance, claim cost,
capital income, counted members, and protected-container access. It also
updates the American, British, and Italian doctrine effects described in the
changelog. Capital capture is now an ordinary wartime claim capture; a nation
is eliminated only when it loses all territory.

All long-running timers survive server restarts. Nation data is saved through
an atomic temporary file with a rolling `nationwars.json.bak` backup; malformed
primary files are preserved instead of being overwritten.

## Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.227 or newer for Minecraft 1.21.1
- Open Parties and Claims 0.27.5 through 0.27.x

Players need the Nation Wars resources on their client (normally by installing
the same Nation Wars JAR) for English, Romanian, Spanish, or Polish text to
follow their selected Minecraft language. Dedicated-server gameplay remains
authoritative.

## Build

On Windows:

```powershell
.\gradlew.bat build
```

The output JAR is created under `build/libs`.

Regression tests:

```powershell
.\gradlew.bat test
```

## Development server

```powershell
.\gradlew.bat runServer
```

## Development commands

Permission-level-4 operators can use `/nwdev` to set
player money, set a nation treasury, change a doctrine, finish spy timers, save
data, or synchronize OPAC claims. Permission-level-4 operators can use
`/configure deletenation <country>` for an audited administrative deletion;
this command is not controlled by the debug-command toggle. Destructive
development commands still require
`debugCommandsEnabled=true` in `nationwars-technical.toml`; the production
default is disabled.

## Doctrine IDs

`GER`, `FRA`, `SOV`, `ENG`, `USA`, `ITA`, and `ROM`.

## Recovery note

The project was reconstructed from the published Nation Wars 0.2.2 JAR.
Original comments and version-control history were not present in the compiled
artifact. The recovered source was repaired into a compiling NeoForge project
before development of version 0.3.0 began.
