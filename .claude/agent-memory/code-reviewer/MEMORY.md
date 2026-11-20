# Code Reviewer Memory

## Key Patterns Confirmed

- `AapsSpacing` object: `extraSmall`(2), `small`(4), `medium`(8), `large`(12), `extraLarge`(16),
  `xxLarge`(24). Use instead of hardcoded `.dp` literals.
- `clearFocusOnTap` in `app.aaps.core.ui.compose.Modifiers.kt`. Required for screens with text
  fields.
- `ComposablePluginContent` in
  `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/ComposablePluginContent.kt`.
- `PluginBase.pluginScope` is protected and lives as long as the process. Work that must stop with
  the plugin goes in a scope the plugin creates in `onStart()` and cancels in `onStop()`. Every
  `pluginScope.launch` is listed in `PluginLifetimeWorkScanTest` (app testFull).
- Previews MUST use `MaterialTheme` wrapper (NOT `AapsTheme` — crashes in preview tool).

## DI Patterns

- **One framework everywhere: Metro.** A view model is registered with
  `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())` + `@ViewModelKey`, and read
  in Compose with `metroViewModel()`.
- **ComposeContent constructor**: Always constructed manually in the plugin, receives only
  non-ViewModel deps (e.g., `protectionCheck`, `blePreCheck`) as constructor params.

## Recurring Bugs Across Pump Compose Migrations

- `BlePreCheckHost` and the wizard screen render at the same time, so the wizard shows before the
  BLE check is done. Gate the wizard on a separate "BLE ready" state.
- `SharedFlow` event branches left empty (`// handled inline`) when they are NOT handled anywhere.
  Check every branch really handles its event.
- Public `val rh: ResourceHelper` on ViewModels — should always be `private val`.
- `canGoBack` as a plain computed property reading `StateFlow.value` is not reactive in Compose.
  Derive a `StateFlow` instead.
- Step composables must read ViewModel data from `StateFlow`, not from plain function calls.
- Step counts (`totalSteps`) must come from the workflow's step list, not from a number written
  next to it: shared steps reused across workflows made the two disagree.
- When porting a confirm step, check every `insertTherapyEvent` call for a duplicate event.
- A "Finish" button must stay disabled until the command it waits for has been sent and succeeded.
  Easy to forget when porting from XML, where the button was only made transparent.
- A plain `var` written from another thread or coroutine needs `@Volatile`, or a `StateFlow`.
- Check string values, not just keys: stray quotes and full-width punctuation slip in.

## Architecture Notes

- `WizardStepLayout`, `StepProgressIndicator`, `WizardButton` in
  `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/pump/WizardStepLayout.kt` — shared wizard
  chrome components.
- `BlePreCheckHost` in `core/ui/src/androidMain/kotlin/app/aaps/core/ui/compose/pump/BlePreCheckHost.kt`
  — async, renders the wizard at the same time unless guarded.
