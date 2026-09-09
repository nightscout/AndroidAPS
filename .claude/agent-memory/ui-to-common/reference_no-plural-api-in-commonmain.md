---
name: no-plural-api-in-commonmain
description: There is no plural (quantity string) API in commonMain at all - TextResolver has no gq, TextRef has no plural form, and the string generator emits none. It hard-blocks any file calling rh.gq
metadata:
  type: reference
---

`ResourceHelper.gq(@PluralsRes id, quantity, vararg args)` is **androidMain only** and has no shared
counterpart. `TextResolver` (the commonMain half) declares `gs`, `gs(vararg)`, `gsNotLocalised` and
`shortTextMode` - no plural method. `TextRef` has `AndroidRes`, `Named` and `Literal` - no plural
form. `GenerateKeyStringsTask` emits `<string>` names only, never `<plurals>`.

So a file that calls `.gq(` cannot reach commonMain by any mechanical fix. Grep for it *before*
promising a move, not after.

**Why:** this is not cosmetic. `<plurals name="days">` has real per-language quantity rules - Slavic
languages use one/few/many - so replacing a plural with a `%1$d` format string silently degrades the
text in about 30 locales. That is a "changes what a screen says" decision, never a mechanical fix.

**How to apply:** the whole repo has only **11 `.gq(` call sites in 5 files** (2026-08-30):
`core/interfaces` `DateUtilAndroid`, `implementation` `FileListProviderImpl`,
`plugins/constraints` `Objective`, `pump/medtronic` `MedtronicOverviewViewModel`,
`pump/omnipod/eros` `OmnipodErosPumpPlugin`. Small enough that one shared plural port would clear
them all - which is the argument for designing it once rather than working around it per screen.
Designing it touches `:core:keys` (a `TextRef` plural form), `:core:interfaces` (`TextResolver`) and
the generator, so it is a user decision, not a move.

Related: [[constraints-objectives-move]], [[cmp-what-crosses-unchanged]]
