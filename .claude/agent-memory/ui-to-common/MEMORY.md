# Memory Index — ui-to-common

## Module migration state

- [androidMain Compose backlog survey](project_androidmain-compose-backlog-survey.md) — every non-pump module counted and its blockers classified; :ui leads with 12 proven-mechanical files.
- [Constraints objectives move](project_constraints-objectives-move.md) — ObjectivesScreen is in commonMain; the 14 remaining files are pinned by exactly 3 root causes and have no movable subset.
- [Configuration setup wizard blocked](project_configuration-setupwizard-blocked.md) — all 3 remaining Compose files pinned by SWDefinition and CryptoUtil; needs a :core:* decision first.

- [APS Loop move](project_aps-loop-move.md) — Loop screen+VM+ComposeContent are in commonMain with no build file edit; the duplicated `constraints` string trap.
- [:ui 12-file batch moved](project_ui-twelve-file-batch-moved.md) — done and kept with 46 mutation-proven tests; :ui 36→24, and the Robolectric setup that survives a move.
- [Overview three variants covered](project_overview-three-variants-covered.md) — 10 mutation-proven tests pin what differs between the stacked/split/tablet layouts; what is still only rendered.

## Reference

- [What crosses to CMP unchanged](reference_cmp-what-crosses-unchanged.md) — extended icons, LocalUriHandler, rememberSaveable, Canvas all move with zero edits; grep for the real blockers instead.
- [A move breaks two test harnesses](reference_move-breaks-two-test-harnesses.md) — every commonMain screen move reds Robolectric string lookups and mocked-ResourceHelper tests; both fixes, and how to write a test that is immune.
- [metroViewModel is NOT a blocker](reference_metroviewmodel-is-not-a-blocker.md) — the expect/actual has a working iOS actual and 37 users; two earlier runs stopped on this wrongly.
- [No plural API in commonMain](reference_no-plural-api-in-commonmain.md) — `rh.gq` has no shared counterpart at all; 11 call sites repo-wide, and replacing plurals with format strings breaks ~30 locales.
- [:ui screen test fixture](reference_ui-screen-test-fixture.md) — the shared Robolectric host for commonMain screens; the three locals a screen needs, and the null-in-a-TextField and write-then-reread traps.
- [Three-stage probe move](reference_probe-move-three-stage.md) — how to size a blocked move honestly: raw file, raw cluster, then cluster + mechanical fixes; the last one is the only informative compile.

## Working style

- [Prove the mutation restore](feedback_mutation-restore-proof.md) — after breaking code to test a test, show the diff proving it is back.
