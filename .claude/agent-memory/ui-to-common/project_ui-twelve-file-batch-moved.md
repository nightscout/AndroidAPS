---
name: ui-twelve-file-batch-moved
description: 2026-08-30 - the :ui 12-file mechanical batch is moved to commonMain with 46 new mutation-proven tests; what got real coverage, what moved on the compiler alone, and the Robolectric setup that survives a move
metadata:
  type: project
---

The batch predicted in [[androidmain-compose-backlog-survey]] is **done and kept**. 13 files (12 batch
entries; `ProfileHelperScreen` and `BgInfoSection` each brought their Previews file) moved from
`ui/src/androidMain/.../compose/` to `ui/src/commonMain/.../compose/`, same package path, via
`git mv` one file at a time. `:ui` androidMain Compose files went **36 -> 24**.

**Only five content changes across all 13 files** - everything else is a byte-identical rename:
dead `androidx.compose.ui.res.stringResource` import (TempTargetCarouselCard); discarded
`LocalContext.current` (ProfileHelperScreen); unused `val context` (UserEntryScreen); the two
`java.lang.Math` calls (BgInfoSection); and `libs.sh.calvin.reorderable` moved androidMain ->
commonMain in `ui/build.gradle.kts`.

**Use the JDK's own expressions for the Math substitution, and mind the operand order.**
`Math.toDegrees(x)` is `x * 180.0 / PI` but `Math.toRadians(x)` is `x / 180.0 * PI` - **divide
first**. Writing `x * PI / 180.0` is a different floating point expression. Both forms copied
verbatim are bit-identical to the JVM, which costs nothing and removes the question.

**Coverage written before the move, 46 tests in 5 new androidHostTest classes, all still passing
unchanged afterwards** (`:ui` 416 -> 462, 0 failures, no test edited):
`BgInfoSectionTest` 9, `TempTargetCarouselCardTest` 12, `ProfileHelperViewModelLogicTest` 10,
`ProfileHelperCloneActionTest` 8, `UserEntryScreenTest` 7.

**Five of the twelve got real new coverage**: BgInfoSection(+Previews), TempTargetCarouselCard,
ProfileHelperScreen(+Previews), ProfileHelperViewModel, UserEntryScreen. **Six moved on the compiler
plus pre-existing ViewModel tests only**: TempTargetManagementScreen, OverviewScreenSplit/Stacked/
Tablet, TreatmentsScreen, QuickLaunchConfigScreen - each needs 4+ real ViewModels (GraphViewModel,
ChipsViewModel, ManageViewModel, StatusViewModel; or TreatmentsViewModel's 8 children) rendered
together, which is a fixture, not a test. That fixture is the obvious next piece of work.

**The Robolectric setup that survives a move** - three things, all needed together:
`TextRefIdRegistry.register("ui") { UiStringIds.idOf(it) }` in `@Before`; `LocalPreferences provides
mock` **plus** a `preferences.observe(StringKey.GeneralDarkMode)` stub for anything wrapped in
`AapsTheme { }` (otherwise `IllegalStateException: No Preferences provided`); and `LocalDateUtil` /
`LocalProfileUtil` where the file reads them. `"coreUi"`, `"keys"` and `"interfaces"` owners resolve
**without** any registration - `TextRefResource.android.kt` dispatches those directly - so only the
module's own `UiStrings` needs the registry line.

**Assert against the real resource, never a hard coded string.** Reading
`ctx.getString(R.string.format_mins, 45)` in `@Before` means a translation edit cannot make the test
lie, and it caught the duration-divisor mutation cleanly.

**A `LaunchedEffect { while(true) { ...; delay(30_000) } }` does not hang `waitForIdle()`.** The
TempTargetCarouselCard progress loop was the worry; it is not one.

**Prove the net twice: once before the move and once after.** 20 mutations were run against the
files in androidMain, then the 5 sharpest were repeated against the same files **in commonMain** -
all 5 still went red, which is what actually proves the string-resolution harness did not quietly
break in the new source set. A pre-move mutation alone does not show that.

Related: [[move-breaks-two-test-harnesses]], [[mutation-restore-proof]], [[cmp-what-crosses-unchanged]]
