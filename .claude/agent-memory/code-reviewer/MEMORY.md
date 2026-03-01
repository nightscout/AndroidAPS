# Code Reviewer Memory

## Key Patterns Confirmed

- `AapsSpacing` object holds centralized dp values: `extraSmall`(2), `small`(4), `medium`(8), `large`(12), `extraLarge`(16), `xxLarge`(24).
  Use these instead of hardcoded `.dp` literals in Compose files.
- `clearFocusOnTap` modifier lives in `app.aaps.core.ui.compose.Modifiers.kt` — import path `app.aaps.core.ui.compose.clearFocusOnTap`. Only needed for screens with text fields.
- `ComposablePluginContent` interface is in `core/ui/src/main/kotlin/app/aaps/core/ui/compose/ComposablePluginContent.kt`.
- `NSClientFragment` is still registered via `.fragmentClass()` in both `NSClientPlugin` and `NSClientV3Plugin`, alongside a `.composeContent {}` block. Both code paths currently coexist.
- ViewModels are instantiated manually (not via `viewModels<>` delegate) in both Fragment and `NSClientComposeContent` due to Dagger (not Hilt) DI. The `@Inject` annotation on the ViewModel is for Dagger's factory, not Hilt.
- `NSClientViewModel` uses `viewModelScope` — but is instantiated manually via `remember {}` in a Composable. This means the scope is tied to `ViewModel.viewModelScope` (an internal `SupervisorJob + Main.immediate`) which won't be cancelled correctly because no `ViewModelStoreOwner` is set. This is a latent lifecycle bug.
- `NSClientLog` is a plain class (not data class) with a mutable `var date` field and an auto-incrementing `id`. The `@Immutable` annotation on `NSClientUiState` containing `List<NSClientLog>` is technically lying to the compiler since `NSClientLog` is mutable.
- `rh.gs()` (ResourceHelper) is used throughout NSClientScreen composables instead of `stringResource()`. This violates project rules.
- The `urlUpdate` StateFlow on `NSClientRepository` is never collected in `NSClientViewModel` — the URL is loaded once in `loadInitialData()` but never reactively updated if the URL changes at runtime.

## Architecture Notes

- Both `NSClientPlugin` (V1) and `NSClientV3Plugin` use the same `NSClientComposeContent` and `NSClientFragment`. The fragment path is the legacy path; compose path is the new one.
- `NSClientRepositoryImpl` is `@Singleton` — its log list survives across screen recreations correctly.
- The `PluginBase.scope` is private; plugins must declare their own `CoroutineScope`.

## See Also

- Detailed review findings: see conversation history for NSClient Compose migration review (2026-03-01)
