---
name: overview-three-variants-covered
description: The three OverviewScreen layouts in :ui commonMain have 10 mutation-proven Robolectric tests; what they pin, and the parts of the overview that are still rendered-but-unasserted
metadata:
  type: project
---

`OverviewScreenStacked` / `OverviewScreenSplit` / `OverviewScreenTablet` (all already in
`ui/src/commonMain/.../compose/overview/`) are covered by
`ui/src/androidHostTest/.../compose/overview/OverviewScreenVariantsTest.kt` - 10 tests, each proven
to fail by a mutation of the file in `commonMain`. `:ui` went 485 -> 495 tests.

**What is pinned** - only what differs between the three, because that is what a careless merge of
"three nearly identical files" would break:

- status card **starts collapsed** in stacked (phone portrait), **expanded** in split and tablet
- the two wide layouts carry a `LargeClock` fed the BG reading's own timestamp; stacked has none
- tablet passes `showTimeAgo = false` to `BgInfoSection`, so the BG circle's age line is hidden
  there and shown in the other two
- no BG reading renders the `"---"` placeholder

**Why:** the three share one ViewModel set (Graph/Chips/Manage/Status), so one builder covers all
three, and feeding them identical data is what lets a difference be attributed to the layout rather
than to the fixture.

**How to apply:** the chips row, the graph section and the AAPSCLIENT status card render in every
test but nothing asserts on them - they are load-bearing for "does it compose", not covered. Treat
them as untested. `manageViewModel` is a **parameter of all three screens and read by none of
them**; that is worth raising with the user before anyone deletes it, because zero callers here has
already meant a lost caller elsewhere in this repo.

Related: [[ui-screen-test-fixture]], [[ui-twelve-file-batch-moved]], [[mutation-restore-proof]]
