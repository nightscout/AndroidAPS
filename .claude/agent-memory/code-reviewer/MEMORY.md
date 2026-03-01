# Code Reviewer Memory

## Key Patterns Confirmed

- `AapsSpacing` object holds centralized dp values: `extraSmall`(2), `small`(4), `medium`(8),
  `large`(12), `extraLarge`(16), `xxLarge`(24).
  Use these instead of hardcoded `.dp` literals in Compose files.
- `clearFocusOnTap` modifier lives in `app.aaps.core.ui.compose.Modifiers.kt` — import path
  `app.aaps.core.ui.compose.clearFocusOnTap`. Only needed for screens with text fields.
- `ComposablePluginContent` interface is in
  `core/ui/src/main/kotlin/app/aaps/core/ui/compose/ComposablePluginContent.kt`.
- `NSClientFragment` is still registered via `.fragmentClass()` in both `NSClientPlugin` and
  `NSClientV3Plugin`, alongside a `.composeContent {}` block. Both code paths currently coexist.
- ViewModels are instantiated manually (not via `viewModels<>` delegate) in both Fragment and
  `NSClientComposeContent` due to Dagger (not Hilt) DI. The `@Inject` annotation on the ViewModel is
  for Dagger's factory, not Hilt.
- `NSClientViewModel` uses `viewModelScope` — but is instantiated manually via `remember {}` in a
  Composable. This means the scope is tied to `ViewModel.viewModelScope` (an internal
  `SupervisorJob + Main.immediate`) which won't be cancelled correctly because no
  `ViewModelStoreOwner` is set. This is a latent lifecycle bug.
- `NSClientLog` is a plain class (not data class) with a mutable `var date` field and an
  auto-incrementing `id`. The `@Immutable` annotation on `NSClientUiState` containing
  `List<NSClientLog>` is technically lying to the compiler since `NSClientLog` is mutable.
- `rh.gs()` (ResourceHelper) is used throughout NSClientScreen composables instead of
  `stringResource()`. This violates project rules.
- The `urlUpdate` StateFlow on `NSClientRepository` is never collected in `NSClientViewModel` — the
  URL is loaded once in `loadInitialData()` but never reactively updated if the URL changes at
  runtime.

## Architecture Notes

- Both `NSClientPlugin` (V1) and `NSClientV3Plugin` use the same `NSClientComposeContent` and
  `NSClientFragment`. The fragment path is the legacy path; compose path is the new one.
- `NSClientRepositoryImpl` is `@Singleton` — its log list survives across screen recreations
  correctly.
- The `PluginBase.scope` is private; plugins must declare their own `CoroutineScope`.

## Tidepool Compose Migration Patterns (2026-03-01)

- `TidepoolComposeContent` constructs `TidepoolViewModel` via `remember {}` (not
  `ViewModelProvider`) — same latent lifecycle bug as `XdripComposeContent`. The Dagger
  `ViewModelFactory` is registered in `SyncModule` but NOT passed to `TidepoolComposeContent`.
  `NSClientComposeContent` is the correct reference: it accepts `viewModelFactory` and calls
  `ViewModelProvider(viewModelStoreOwner, viewModelFactory)[...]`.
- `TidepoolFragment` manually `new`s the ViewModel in `onCreateView` without `ViewModelProvider` —
  no ViewModel store owner so `viewModelScope` is never cancelled on fragment backstack pop (only on
  `onDestroyView` when `viewModel = null`).
- `TidepoolLog` has `var date` (mutable) — same issue as `NSClientLog`. `@Immutable` on
  `TidepoolUiState` containing `List<TidepoolLog>` is technically incorrect (mutable element).
- Both `TidepoolScreen` and `XdripScreen` use `rh.gs()` (ResourceHelper) inside Composables instead
  of `stringResource()` — violates project convention. Pattern is widespread in this module but
  still a violation.
- `TidepoolScreen` and `XdripScreen` both use hardcoded `16.dp`, `4.dp`, `8.dp`, `2.dp` instead of
  `AapsSpacing` constants.
- `SimpleDateFormat` (line 47 of TidepoolScreen.kt) is a file-level `private val` — not thread-safe
  if multiple threads access the preview. Safe in practice because it's only used in preview with a
  single thread, but the pattern should still use `DateTimeFormatter` or be local.
- `TidepoolPlugin` and `TidepoolFragment` duplicate all menu action lambdas — same 5 actions in 4
  places (Fragment.onMenuItemSelected, Fragment.setContent lambdas, ComposeContent.Render lambdas,
  TidepoolPlugin.composeContent lambdas).
- Preview uses `MaterialTheme` wrapper, not `AapsTheme` — project convention requires `AapsTheme`.
- `TidepoolRepository.addLog()` calls `aapsLogger.debug()` inside the `update {}` lambda — side
  effect inside a state update lambda; should log before/after the update, not inside it.

## Wear Compose Migration Patterns (2026-03-01)

- `WearViewModel` is NOT registered in `SyncModule` via `@IntoMap @ViewModelKey` — it cannot use the
  Dagger `ViewModelFactory`. As a result `WearComposeContent` constructs it via plain
  `remember {}` — same latent lifecycle bug as Tidepool/Xdrip. Fix: register in SyncModule and pass
  `viewModelFactory` to `WearComposeContent`.
- `WearComposeContent` calls `.also { it.requestCustomWatchface() }` inside `remember {}` —
  side-effect at composition time is fragile; should be in `LaunchedEffect(Unit)` in `WearScreen`.
- `WearScreen` passes `rh: ResourceHelper` all the way from `WearComposeContent` down to the screen
  composable and uses `rh.gs()` in a `LaunchedEffect` (toolbar config) — the strings are resolved
  inside a LaunchedEffect so technically not in composition, but the `rh` reference should be
  replaced by pre-resolved `stringResource()` strings captured in the composable scope. Three
  specific violations: lines 88, 96, 105 of WearScreen.kt.
- `WearScreen` has no text fields, so `clearFocusOnTap` is not needed — correct.
- All hardcoded dp values in WearScreen: `16.dp` (lines 158, 224, 278), `8.dp` (lines 159, 194, 246,
  279, 291), `12.dp` (lines 168, 193), `4.dp` (lines 199, 309, 333), `18.dp` (lines 252, 261),
  `6.dp` (lines 253, 262), `20.dp` (line 324), `300.dp` (line 287) — should use `AapsSpacing` for
  the generic ones; 300.dp and 18.dp need named domain constants.
- Hardcoded `"On"` / `"Off"` strings used as contentDescription in CwfInfosContent (line 322) — must
  be string resources.
- Preview wrappers use `MaterialTheme` — this is the CORRECT pattern for Compose previews in this
  project (AapsTheme crashes in preview). Confirmed as intentional.
- `WearUiState`, `CwfInfosState`, `CwfPrefItem`, `CwfViewItem` are all proper `@Immutable` data
  classes with `val` fields — correct.
- Modifier ordering is correct in all composable signatures: `modifier: Modifier = Modifier` is
  always the last parameter after required params, first optional.
- `WearMainContent` and `CwfInfosContent` are `private` — correct visibility.
- `WearScreen` is package-private (internal) — appropriate since it is called from
  `WearComposeContent` in the same package.

## SMS Communicator Compose Migration Patterns (2026-03-01)

- `SmsCommunicatorViewModel` lacks `@Inject constructor` and is not registered in `SyncModule` —
  same latent lifecycle bug as Tidepool/Xdrip/Wear. `SmsCommunicatorComposeContent` constructs it
  via `remember {}` without a `ViewModelStoreOwner`.
- `SmsCommunicatorRepository` is a plain `class` instantiated manually inside the `@Singleton`
  plugin (`val repository = SmsCommunicatorRepository()`). Should be `@Singleton` and injected into
  both the plugin and the ViewModel once DI is fixed.
- `SmsCommunicatorOtpScreen` (Compose) is missing `FLAG_SECURE` that the legacy
  `SmsCommunicatorOtpActivity` had. This is a security regression — the OTP QR code must be
  protected from screenshots.
- `SmsCommunicatorFragment` (RxJava + ViewBinding) is still the active `.fragmentClass()` in the
  plugin; the Compose path is unreachable for the main tab until that line is removed.
- `SmsCommunicatorOtpActivity` duplicates `SmsCommunicatorOtpScreen` — candidate for deletion once
  FLAG_SECURE is resolved in Compose.
- `SmsItem` and `SmsCommunicatorUiState` are correctly `@Immutable` data classes with `val` fields —
  good model (contrast with NSClientLog/TidepoolLog which have mutable `var` fields).
- `SmsCommunicatorOtpScreen` uses `stringResource()` throughout — no `rh.gs()` violations (unlike
  Tidepool/Xdrip/Wear screens).
- `SmsCommunicatorScreen` and `SmsCommunicatorScreenContent` are `public` but should be `internal`.
- `setToolbarConfig` is received in `SmsCommunicatorComposeContent.Render()` but never called —
  toolbar stays at host default.
- `@Stable` on `SmsCommunicatorViewModel` is incorrect/misleading; other ViewModels in this project
  are not annotated `@Stable`.
- Hardcoded colors `Color.Green`, `Color.Yellow`, `Color.Red` in OTP verification feedback — should
  use M3 semantic colors.
- `SmsCommunicatorOtpScreen` is missing `clearFocusOnTap` despite containing an `OutlinedTextField`.

## See Also

- Detailed review findings: see conversation history for NSClient Compose migration review (
  2026-03-01)
- Detailed review findings: see conversation history for Tidepool Compose migration review (
  2026-03-01)
- Detailed review findings: see conversation history for Wear Compose migration review (2026-03-01)
- Detailed review findings: see conversation history for SMS Communicator Compose migration review (
  2026-03-01)
