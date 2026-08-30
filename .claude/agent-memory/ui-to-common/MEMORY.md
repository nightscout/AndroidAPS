# Memory Index — ui-to-common

## Module migration state

- [Constraints objectives move](project_constraints-objectives-move.md) — ObjectivesScreen is in commonMain; why the ComposeContent and ViewModel are blocked by bigger things.
- [Configuration setup wizard blocked](project_configuration-setupwizard-blocked.md) — all 3 remaining Compose files pinned by SWDefinition and CryptoUtil; needs a :core:* decision first.

- [APS Loop move](project_aps-loop-move.md) — Loop screen+VM+ComposeContent are in commonMain with no build file edit; the duplicated `constraints` string trap.

## Reference

- [What crosses to CMP unchanged](reference_cmp-what-crosses-unchanged.md) — extended icons, LocalUriHandler, rememberSaveable, Canvas all move with zero edits; grep for the real blockers instead.
- [A move breaks two test harnesses](reference_move-breaks-two-test-harnesses.md) — every commonMain screen move reds Robolectric string lookups and mocked-ResourceHelper tests; both fixes, and how to write a test that is immune.
- [metroViewModel is NOT a blocker](reference_metroviewmodel-is-not-a-blocker.md) — the expect/actual has a working iOS actual and 37 users; two earlier runs stopped on this wrongly.

## Working style

- [Prove the mutation restore](feedback_mutation-restore-proof.md) — after breaking code to test a test, show the diff proving it is back.
