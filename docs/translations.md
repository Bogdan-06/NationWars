# Nation Wars translation notes

## Romanian terminology

- Nation: **Națiune**.
- Claim as a noun in ownership/UI contexts: **Teritoriu revendicat**; the act or
  allowance to claim land: **Revendicare**.
- Treasury: **Trezorerie**.
- Doctrine: **Doctrină**.
- Alliance: **Alianță**.
- Guarantee: **Garanție**.
- War justification: **Justificare de război**.
- Peace deal: **Acord de pace**.
- Spy agency: **Agenție de spionaj**.
- Puppet nation: **Națiune-marionetă**; master in this geopolitical context:
  **Națiune suzerană**.
- Puppet proposal/peace term: **Propunere/clauză de vasalizare**.
- Independence points: **Puncte de independență**; annexation: **Anexare**.

## Spanish terminology

- Nation: **Nación**.
- Claim as a noun in ownership/UI contexts: **Territorio**; claiming land:
  **Reclamar territorio**.
- Treasury: **Tesorería**.
- Doctrine: **Doctrina**.
- Alliance: **Alianza**.
- Guarantee: **Garantía**.
- War justification: **Justificación de guerra**.
- Peace deal: **Acuerdo de paz**.
- Spy agency: **Agencia de espionaje**.
- Puppet nation: **Nación títere**; master in this geopolitical context:
  **Potencia dominante**.
- Puppet proposal/peace term: **Propuesta/cláusula de subordinación**.
- Independence points: **Puntos de independencia**; annexation: **Anexión**.
- The technical Minecraft term **chunk** remains untranslated.

## Polish terminology

- Nation: **Państwo** or **naród**, according to sentence context.
- Claim as owned land: **Terytorium**; claiming and unclaiming land:
  **Zajęcie terytorium** and **Zwolnienie terytorium**.
- Treasury: **Skarbiec**.
- Doctrine: **Doktryna**.
- Alliance: **Sojusz**.
- Guarantee: **Gwarancja**.
- War justification: **Uzasadnienie wojny**.
- Peace deal: **Układ pokojowy**.
- Spy agency: **Agencja szpiegowska**.
- Puppet nation: **Państwo marionetkowe**; master in this geopolitical
  context: **Państwo zwierzchnie**.
- Puppet proposal/peace term: **Propozycja/warunek podporządkowania**.
- Independence points: **Punkty niepodległości**; annexation: **Aneksja**.
- The technical Minecraft term **chunk** remains untranslated.

Nation names, player names, command IDs, claim coordinates, command syntax, and
datapack-provided doctrine text are runtime data and are not translated.

## Context-sensitive keys

- Doctrine `perk.N` keys intentionally preserve the original per-line layout;
  a continuation line can begin with spaces because Minecraft lore renders each
  key on a separate line.
- `nationwars.message.prefix` is the fixed chat marker, not a sentence.
- `nationwars.common.none` is used for empty list/status values, not a negative
  answer to a question.
- Doctrine defaults use translation keys. A datapack that supplies a custom
  doctrine display name or lore keeps that custom text as server-provided data.
- Join-policy labels are status values. Command literals (`open`,
  `invite_only`, and `closed`) remain unchanged while their displayed labels are
  translated.
- Nation invitations repeat the nation command ID in two command examples.
  Positional placeholders keep both examples tied to the same untranslated ID.
- `nationwars.gui.peace.puppet.term` is directional: the receiving nation
  becomes the puppet of the nation that proposed the peace offer. Translate it
  as a complete sentence rather than assembling translated fragments.
- Puppet `master` never means a Minecraft owner, operator, or command
  permission. It is the geopolitical controlling nation described above.
- Point thresholds use numeric arguments and do not imply automatic
  liberation/annexation; corresponding text must continue to describe a command
  becoming available.
- Italy's doctrine name **Kingdom of the South** is **Regatul Sudului** in
  Romanian, **Reino del Sur** in Spanish, and **Królestwo Południa** in Polish.
