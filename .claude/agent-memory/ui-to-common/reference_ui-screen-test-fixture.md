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

`AapsScreenFixture` also provides `LocalDecimalFormatter` (added for the overview). Any screen that
contains a graph needs it - `SecondaryGraphCompose` reads it for axis labels and the default throws
"No DecimalFormatter provided" from inside the chart, which reads as a theme problem and is not.

**Per-screen builders** (`TempTargetManagementViewModelFixture`,
`QuickLaunchConfigViewModelFixture`, `OverviewViewModelFixture`) build the **real** ViewModel with
mocked dependencies, sharing `preferences`/`dateUtil`/`profileUtil`/`decimalFormatter` with the host
so the ViewModel and the composables cannot drift apart. A new screen gets its own builder next to
these; do not grow one god-object.

**Four things the overview builder needed that the other two did not:**

- **A real fake, not a mock, for a wide interface.** `OverviewDataCache` has ~25 flows that the
  view models read while being constructed; one missing `whenever` lands as an NPE inside a
  `combine`, nowhere near the gap. `FakeOverviewDataCache` is ~90 lines of `MutableStateFlow` and
  cannot have a gap. Use the same shape for any interface with more than about five flows.
- **`ActivePlugin.activePump` and `activePumpInternal` are different types** -
  `PumpWithConcentration` and `Pump`. `StatusViewModel` reads the first, `ManageViewModel` casts the
  second to `PluginBase`. One mock cannot serve both: use `mock<PumpWithConcentration>()` plus
  `mock<FakePumpPlugin>()` (the `internal abstract class` already in `ManageViewModelTest.kt`,
  reachable from any androidHostTest package), backed by one shared `PumpDescription()`.
- **`StatusViewModel.refreshState()` really hops to `aapsIoDispatcher`**, so the status card is empty
  for a moment after construction and `OverviewStatusSection` returns without drawing. Block on
  `uiState.first { it.sensorStatus != null }` before `setContent` instead of racing it.
- **Robolectric's default window is a portrait phone.** A landscape or tablet layout puts its
  side-by-side columns at near-zero width there, and a widget reports as *"is not displayed"* - the
  node exists, it just fails the displayed check, so the message looks like a wiring bug. Put
  `@Config(qualifiers = "w1280dp-h800dp")` on those tests.

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
