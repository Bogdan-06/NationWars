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
