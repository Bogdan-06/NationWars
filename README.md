# Nation Wars

Development project for Nation Wars 0.4.0.

Nation Wars is a server-side NeoForge mod providing nations, chunk claims,
economy, markets, alliances, war, peace deals, doctrine bonuses, and integration
with Open Parties and Claims.

Version 0.4.0 adds `/configurate`, nation upgrades, invitations, leave/kick
rules, 20-minute truces, the `/nation trade` UI with recurring passive-income
terms, configurable espionage/factions/guarantees/peace/claiming/trading rules,
natural coast detection, and doctrine balance changes. The original update
specifications and spy UI reference are preserved under `docs/specifications`.

Use `/configurate` as an operator to review server settings. Examples:
`/configurate LimitedDoctrines t`, `/configurate DisableEspionage false`,
`/configurate ClaimNether true`, `/configurate Colonialism false`, and
`/configurate SpawnProtection 200`.

Use `/nation upgrade` to buy up to four upgrade levels. Each level adds five
free claims and $6/minute of capital income. Use
`/alliance truce offer <country>` to propose a 20-minute non-aggression pact;
either nation can propose renewal before it expires.

Use `/spy mission` to choose a stationed country, mission, and required chunks
through an inventory interface. Scout missions allow selecting three chunks and
large countries support paged claim lists.

After any mission, the spy enters a 60-second `recovering` cooldown and then
automatically becomes `stationed` in the same country again. `/spy status`
shows the remaining recovery time.

All long-running timers survive server restarts. Nation data is saved through
an atomic temporary file with a rolling `nationwars.json.bak` backup; malformed
primary files are preserved instead of being overwritten.

## Requirements

- Java 21
- Minecraft 1.21.1
- NeoForge 21.1.227 or newer for Minecraft 1.21.1
- Open Parties and Claims 0.27.5 through 0.27.x

Players need the Nation Wars resources on their client (normally by installing
the same Nation Wars JAR) for English, Romanian, or Spanish text to follow their
selected Minecraft language. Dedicated-server gameplay remains authoritative.

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

Operators can use `/nwdev` (or `/nationwarsdev`) to set player money, set a
nation treasury, change a doctrine, finish spy timers, save data, or synchronize
OPAC claims.

## Doctrine IDs

`GER`, `FRA`, `SOV`, `ENG`, `USA`, `ITA`, and `ROM`.

## Recovery note

The project was reconstructed from the published Nation Wars 0.2.2 JAR.
Original comments and version-control history were not present in the compiled
artifact. The recovered source was repaired into a compiling NeoForge project
before development of version 0.3.0 began.
