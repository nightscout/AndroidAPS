---
name: androidmain-compose-backlog-survey
description: 2026-08-30 survey of every non-pump module's remaining androidMain Compose files, with each module's blockers grouped by root cause and classified mechanical / needs-a-port / needs-a-decision
metadata:
  type: project
---

Counts of files containing `@Composable` under `src/androidMain` (2026-08-30, non-pump):
`:ui` 36, `:plugins:sync` 21, `:core:ui` 8, `:plugins:automation` 6, `:plugins:aps` 3,
`:plugins:configuration` 3, `:plugins:constraints` 1, `:implementation` 1, `:appshell` 1,
`:plugins:calibration` 1. `:plugins:main`, `:plugins:source`, `:core:graph`, `:core:objects`,
`:wear` and `:shared:impl` have **zero** - do not spend a probe on them.

Three of the eight `:core:ui` files and one of the 21 `:plugins:sync` files are `*.android.kt`
**actual** declarations (`MetroViewModel`, `PlatformTheme`, `TextRefResource`, `ScreenshotBlocking`).
They belong in androidMain; never count them as backlog.

**Ranked by "Compose files whose only blockers are mechanical", measured by probe compiles:**

1. `:ui` - **12 files proven, and MOVED on 2026-08-30** - see [[ui-twelve-file-batch-moved]].
   TempTargetCarouselCard, TempTargetManagementScreen, BgInfoSection(+Previews),
   OverviewScreenSplit/Stacked/Tablet, ProfileHelperScreen(+Previews, + its ViewModel),
   UserEntryScreen, TreatmentsScreen, QuickLaunchConfigScreen are now in commonMain.
   The counts at the top of this note are the **pre-move** ones: `:ui` is now **24**, not 36.
2. `:plugins:sync` - **3 files proven** (the whole `xdrip/compose` cluster, 6 files incl. VM and
   repository). Stage-3 probe green, 0 errors.
3. `:plugins:automation` - 2 files at best, and one needs an `android.R.string.ok/cancel` decision.
4. `:core:ui` - 0. Every remaining file is a platform capability.
5. `:plugins:aps` - 0. Autotune is one cluster pinned by its engine.

**The mechanical fixes that were actually needed** (all verified by a green iosArm64 compile):
`java.util.concurrent.atomic.AtomicLong` -> `kotlin.concurrent.atomics` (`getAndIncrement` ->
`fetchAndIncrement`); `System.currentTimeMillis()` -> `Clock.System.now().toEpochMilliseconds()`;
`java.time` -> `kotlin.time.Instant` + `kotlinx.datetime.toLocalDateTime`; `Dispatchers.IO` ->
`aapsIoDispatcher`; `ResourceHelper` -> `TextResolver`; `java.lang.Math.toDegrees/toRadians` ->
`* 180.0 / PI`; deleting a dead `androidx.compose.ui.res.stringResource` import; deleting an unused
`LocalContext.current`; dropping `@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)`.

**Two blockers that look like libraries and are really one line of build file.** `:ui` declares
`libs.sh.calvin.reorderable` in androidMain only, while `:plugins:automation` already declares the
same artifact in **commonMain** with a comment saying it works everywhere - so
`QuickLaunchConfigScreen` needs a dependency moved, not a rewrite. Same shape in `:plugins:aps`,
which reaches `app.aaps.core.graph.profile.*` (already commonMain) but declares `:core:graph` in
androidMain only. Check the build file before calling a third-party import a blocker.

**`rh.gq(` plurals block NONE of these modules.** Still 11 sites in 5 files, all in
`:core:interfaces`, `:implementation`, `:plugins:constraints` and `pump/*`. See
[[no-plural-api-in-commonmain]].

**Blockers that need a decision, with what they pin:**

- `@StringRes Int` already sitting in `:core:interfaces` **commonMain**: `CustomAction.name: Int`
  pins `ManageBottomSheet`; `PrefsMetadataKey.label: Int` pins `:core:ui` `ImportSummaryComponents`.
  Changing either touches every pump driver.
- `FileListProvider` pins the whole `:ui` maintenance cluster - ImportSettingsScreen,
  MaintenanceDialogs, MaintenanceBottomSheet, CloudDirectorySheet, and both maintenance ViewModels.
  A probe that moves any maintenance file alone will fail on `MaintenanceViewModel`.
- `:ui` search: OkHttp + `org.json` + `java.text.Normalizer` in `WikiSearchRepository` /
  `SearchIndexBuilder`. Pins `MainScreen` and `MainTopBar` through `SearchUiState`.
- `android.R.string.ok` / `android.R.string.cancel` used as real UI text - 4 sites in
  `AutomationComposeContent`, 1 in `:core:ui` `ImportSummaryComponents`. `CoreUiStrings.ok/cancel`
  exist, but swapping changes which catalogue translates the button in ~30 locales.
- `is24HourFormat`: a `DateFormatPlatform` port **already exists** but lives in `:shared:impl`
  commonMain, and `:ui` does not depend on `:shared:impl`. Pins `ProfileActivationScreen`.

**Blockers that need a port designed, with what they pin:** window size class
(`LocalConfiguration.orientation` + `smallestScreenWidthDp`) pins `OverviewScreen` and
`SmsCommunicatorOtpScreen`; screen control (orientation lock, keep-screen-on, FLAG_SECURE) pins
`SiteRotationManagementScreen`, `:core:ui` `KeepScreenOnEffect` and the existing
`ScreenshotBlocking` actual; runtime permissions and BLE pre-check pin `:core:ui`
`BluetoothPermissionsHost` / `BlePreCheckHost` and `:ui` `PermissionsViewModel`; biometrics
(`FragmentActivity`) pins `:core:ui` `ProtectionHost`; `Application.ActivityLifecycleCallbacks`
(`ui/activityMonitor/ActivityMonitor.kt`) pins `ActivityStatsCompose` and therefore `StatsScreen`;
`Build.MANUFACTURER` pins `AboutDialog`; Glance pins the three widget files, permanently.

**Dead code found while surveying, worth confirming before deleting:**
`AutomationComposeContent` threads `activity: FragmentActivity?` through three routes and then does
`@Suppress("UNUSED_EXPRESSION") activity` with the comment "kept for future use" - it is the only
reason that file needs `LocalContext`. `ProfileHelperScreen` has a bare `LocalContext.current` whose
result is discarded, `UserEntryScreen` has an unused `val context`, `AboutDialogPreviews` has an
unused `import app.aaps.core.ui.R`, and `TempTargetCarouselCard` imports the androidx
`stringResource` although its helper already returns a `TextRef`.

Related: [[probe-move-three-stage]], [[cmp-what-crosses-unchanged]], [[constraints-objectives-move]],
[[configuration-setupwizard-blocked]]
