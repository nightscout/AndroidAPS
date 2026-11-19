# Memory Index — ui-to-common

## Module migration state

- [Overview three variants covered](project_overview-three-variants-covered.md) — 10 mutation-proven tests pin what differs between the stacked/split/tablet layouts; what is still only rendered, and the unused `manageViewModel` parameter.

## Reference

- [Lessons from finished moves](reference_lessons-from-finished-moves.md) — duplicate string names belong to the module, Math operand order, prove the net twice, real resources in asserts, when to batch mutations.
- [What crosses to CMP unchanged](reference_cmp-what-crosses-unchanged.md) — extended icons, LocalUriHandler, rememberSaveable, Canvas all move with zero edits; grep for the real blockers instead.
- [A move breaks two test harnesses](reference_move-breaks-two-test-harnesses.md) — every commonMain screen move reds Robolectric string lookups and mocked-ResourceHelper tests; both fixes (only five owners resolve through `stubTextRefResolution`), and how to write a test that is immune.
- [metroViewModel is NOT a blocker](reference_metroviewmodel-is-not-a-blocker.md) — the expect/actual has working iOS and JVM actuals; only the old inline `LocalContext` cast is Android only.
- [No plural API in commonMain](reference_no-plural-api-in-commonmain.md) — `rh.gq` has no shared counterpart; lift the call behind a small interface as `DurationText` does, never flatten plurals into format strings.
- [:ui screen test fixture](reference_ui-screen-test-fixture.md) — the shared Robolectric host for commonMain screens; the three locals a screen needs, and the null-in-a-TextField and write-then-reread traps.
- [Three-stage probe move](reference_probe-move-three-stage.md) — how to size a blocked move honestly: raw file, raw cluster, then cluster + mechanical fixes; the last one is the only informative compile.
