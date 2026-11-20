---
name: no-plural-api-in-commonmain
description: There is no plural (quantity string) API in commonMain at all - TextResolver has no gq, TextRef has no plural form, and the string generator emits none. Lift the rh.gq call behind a small interface, as DurationText does; never flatten plurals into format strings
metadata:
  type: reference
---

`ResourceHelper.gq(@PluralsRes id, quantity, vararg args)` is **androidMain only** and has no shared
counterpart. `TextResolver` (the commonMain half) declares `gs`, `gs(vararg)` and `gsNotLocalised` -
no plural method. `TextRef` has `AndroidRes`, `Named` and `Literal` - no plural
form. `GenerateKeyStringsTask` emits `<string>` names only, never `<plurals>`.

So a file that calls `.gq(` cannot reach commonMain by a mechanical fix. Grep for it *before*
promising a move, not after.

**The way that worked: lift only the plural call out.** The objectives did this in
`:plugins:constraints`. `DurationText` is a small interface in commonMain. `AndroidDurationText`
(androidMain) implements it with `rh.gq(R.plurals.days/hours/minutes, ...)`, so Android keeps the same
wording and the same translations, and `PlainDurationText` (commonMain) says it plainly on the
platforms that have no plural table. The class that used it (`Objective`) then moved whole. Copy that
shape: the shared code keeps the rule, the platform keeps only the plural lookup.

**Why:** this is not cosmetic. `<plurals name="days">` has real per-language quantity rules - Slavic
languages use one/few/many - so replacing a plural with a `%1$d` format string silently degrades the
text in about 30 locales. That is a "changes what a screen says" decision, never a mechanical fix.

**How to apply:** the repo has only about a dozen `.gq(` call sites, all in androidMain or `pump/*`
files (re-check with `grep -rn "\.gq(" --include=*.kt`). A lifted interface like `DurationText` is
the per-feature answer. A real shared plural API would clear them all at once, but designing it
touches `:core:keys` (a `TextRef` plural form), `:core:interfaces` (`TextResolver`) and the generator,
so it is a user decision, not a move. Ask before adding a second lifted interface that does the same
job as `DurationText`.

Related: [[cmp-what-crosses-unchanged]]
