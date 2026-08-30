---
name: ui-screen-test-fixture
description: The shared Robolectric fixture for :ui commonMain screens - what it provides, the three failures it prevents, and the two traps that cost the most time when writing screen tests here
metadata:
  type: reference
---

`ui/src/androidHostTest/kotlin/app/aaps/ui/compose/testing/` holds one ambient host plus one small
builder per screen. It exists because a `:ui` screen will not render at all without three things
that each fail in a way that does not point at itself.

**`AapsScreenFixture`** - the ambient environment, no ViewModel:
`TextRefIdRegistry.register("ui")` (else every label renders as its raw name and reads as "not
displayed"); a `Preferences` mock answering `observe(GeneralDarkMode)` (else `AapsTheme` throws "No
Preferences provided"); a `Config` (else `masterEditingEnabled()` throws on `LocalConfig`); plus
`LocalDateUtil`, `LocalProfileUtil`, `LocalMasterReachable`. `asOfflineClient()` renders the
client-with-unreachable-master state. Use `compose.setAapsContent(fixture) { Screen(...) }`.
`stubResourcesFromRobolectric(rh)` in the same file answers a mocked `ResourceHelper` from the real
resources and then calls `stubTextRefResolution`, so a ViewModel that formats text gets words, not
nulls.

**Per-screen builders** (`TempTargetManagementViewModelFixture`,
`QuickLaunchConfigViewModelFixture`) build the **real** ViewModel with mocked dependencies, sharing
`preferences`/`dateUtil`/`profileUtil` with the host so the ViewModel and the composables cannot
drift apart. A new screen gets its own builder next to these; do not grow one god-object.

**Two traps that cost the most time:**

- **A mock's `null` reaching an `OutlinedTextField` surfaces as
  `NullPointerException: TextFieldValue.getText() is null` inside `BasicTextField`**, ten frames from
  the missing stub. `dateUtil.dateString`/`timeString` were the culprits for the TT editor.
- **A ViewModel that writes then re-reads a preference needs the mock to behave like storage.**
  `QuickLaunchConfigViewModel.saveAndReload` does `put` then `loadState()`; a `get` stubbed with
  `thenReturn` keeps answering the old list, so the screen looks like it ignores Remove. Back the
  mock with a field: `get` via `thenAnswer { stored }`, `put` via `doAnswer { stored = ... }`. This is
  a fixture fix, not a screen finding.

**A `LazyColumn` only composes what is on screen**, so `assertDoesNotExist` on a section far down the
list proves nothing. Either pick a case where the list is short (an unpaired `VisibilityContext`
empties the whole Care Portal section) or scroll with
`onNode(hasScrollToNodeAction()).performScrollToNode(hasText(...))`. Also: node order from
`onAllNodesWithContentDescription` inside a `LazyColumn` did **not** match visual row order here -
do not select a row by index.

Related: [[move-breaks-two-test-harnesses]], [[ui-twelve-file-batch-moved]], [[mutation-restore-proof]]
