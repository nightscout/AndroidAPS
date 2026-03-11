# Code Reviewer Memory

## Key Patterns Confirmed

- `AapsSpacing` object: `extraSmall`(2), `small`(4), `medium`(8), `large`(12), `extraLarge`(16),
  `xxLarge`(24). Use instead of hardcoded `.dp` literals.
- `clearFocusOnTap` in `app.aaps.core.ui.compose.Modifiers.kt`. Required for screens with text
  fields.
- `ComposablePluginContent` in
  `core/ui/src/main/kotlin/app/aaps/core/ui/compose/ComposablePluginContent.kt`.
- `PluginBase.scope` is private; plugins must declare their own `CoroutineScope`.
- Previews MUST use `MaterialTheme` wrapper (NOT `AapsTheme` — crashes in preview tool).

## DI Patterns by Module

- **Medtrum + Equil**: Use Hilt (`@HiltViewModel`, `hiltViewModel()`). Build needs
  `libs.plugins.hilt`,
  `com.google.dagger.hilt.android`, `androidx.hilt.navigation.compose`, ksp hilt compiler.
  App has `@HiltAndroidApp` on `MainApp`. This is the correct modern pattern.
- **NSClient, Tidepool, Wear, SMS**: Use Dagger `ViewModelFactory` via
  `@Binds @IntoMap @ViewModelKey`.
  `ComposablePluginContent` receives `viewModelFactory` and calls
  `ViewModelProvider(viewModelStoreOwner, viewModelFactory)[...]`. Instantiating via `remember {}`
  is a latent lifecycle bug (viewModelScope never cancelled correctly).
- **ComposeContent constructor**: Always constructed manually in the plugin, receives only
  non-ViewModel deps (e.g., `protectionCheck`, `blePreCheck`) as constructor params.

## Recurring Bugs Across Pump Compose Migrations

- `BlePreCheckHost` + wizard screen render simultaneously — wizard shows before BLE check completes.
  Need a separate `isCheckingBle` state to gate wizard rendering. (EOPatch2, Equil) — STILL OPEN in
  Equil.
- `SharedFlow` event branches left empty (`// handled inline`) when they are actually NOT handled.
  (Equil: `ShowMessage` in `EquilComposeContent` swallows unpair errors)
- Public `val rh: ResourceHelper` on ViewModels — should always be `private val`. (EOPatch2,
  Equil) — FIXED in Equil.
- `canGoBack` implemented as a plain Kotlin computed property reading `StateFlow.value` instead of
  a derived `StateFlow` — not reactive in Compose. (Equil) — FIXED.
- Step composables accessing ViewModel data via plain function calls instead of `StateFlow`. (
  Equil) — FIXED.
- Step count (`totalSteps`) mismatch when shared steps (AIR, CONFIRM) are reused across workflows
  without updating the workflow's declared `totalSteps`. (Equil: CHANGE_INSULIN declares 4 but
  runs 6 steps) — FIXED (count correct), but comment in EquilWizardStep.kt line 15 not updated.
- Duplicate therapy event insertion in activation confirm step — check all `insertTherapyEvent`
  calls when porting confirm logic. (Equil: double CANNULA_CHANGE event) — RESOLVED, not duplicated.
- Air removal step: "Finish" button must be disabled until the removal command has been sent and
  succeeded. Easy to forget when porting from XML (button was initially disabled via alpha). (
  Equil) — FIXED.
- Callback.run() in commandQueue executes on background HandlerThread — all MutableStateFlow.value
  assignments from callbacks are safe (StateFlow is thread-safe), but plain `var` fields (
  autoFillActive,
  fillStepCount) accessed from callbacks are NOT safe without @Volatile.
- Empty password allowed through SerialNumberStep — `isPasswordValid = password.isEmpty() || ...`
  lets user pair with no password, which is stored and used for future unpair.
- Resource strings with embedded stray characters: equil_install has trailing `"`,
  equil_unbind_content
  has full-width `！`. Always check string values not just keys.
- `GIF_MAX_HEIGHT = 300.dp` duplicated in 4 step files — WizardGifImage.kt was planned but not
  created.

## Architecture Notes

- `WizardGifImage.kt` / `WizardImage` in `core/ui/compose/pump/` — shared GIF/image wrapper for
  wizards. Step composables should use this instead of copy-pasting `GlideImage` boilerplate.
- `WizardStepLayout`, `StepProgressIndicator`, `WizardButton` in `core/ui/compose/pump/` — shared
  wizard chrome components.
- `BlePreCheckHost` in `core/ui/compose/pump/BlePreCheckHost.kt` — async, renders wizard at same
  time unless guarded.

## See Also

- `equil-migration.md` — detailed Equil Compose migration review (2026-03-09)
- Earlier migration reviews (NSClient, Tidepool, Wear, SMS, Preferences, EOPatch2): see conversation
  history from 2026-03-01 and 2026-03-02.
