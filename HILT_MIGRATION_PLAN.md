# Hilt Migration Plan — Compose ViewModels

## Goal

Migrate **37 ViewModels** across all Compose code to `@HiltViewModel`:

- 25 in `ComposeMainActivity` (app module)
- 12 in plugin modules (source, sync, medtrum, eopatch)

Screen composables own their VMs via `hiltViewModel()` default parameters (Google best practice).
Dialog VMs get fresh instances per `NavBackStackEntry`, activity-scoped VMs are shared via
activity `ViewModelStoreOwner`. All VMs survive rotation.

Delete legacy Fragments replaced by Compose (`NSClientFragment`, `XdripFragment`,
`TidepoolFragment`).
After migration, delete `ViewModelFactory`/`ViewModelKey` infrastructure and plugin qualifiers.

Legacy XML Activities/Fragments/Services/Receivers stay on `dagger.android` — no changes needed.
Hilt and `dagger.android` coexist: Hilt auto-provides `DispatchingAndroidInjector<Any>`.
Omnipod (Fragment-only, custom scopes) stays on old pattern until those Fragments are removed.

## Status: Phases 0-3 COMPLETE + review fixes applied

Phases 0-3 (Gradle setup, @InstallIn, @HiltAndroidApp, @AndroidEntryPoint) are done.
Code review fixes applied: AppModule made abstract, @DisableInstallInCheck comments added,
test modules annotated, onPermissionResultDenied nulled in onDestroy.
Full build + unit tests pass. Next: Phase 4 (convert ViewModels to @HiltViewModel).

### Previous Blocker (RESOLVED)

The **Hilt Gradle plugin** requires `com.squareup:javapoet:1.13.0` at runtime.
AGP's `androidx.databinding:databinding-compiler-common` (pulled in by `dataBinding = true`)
pins `javapoet:1.10.0`. The older version is missing `ClassName.canonicalName()` which
Hilt's `AggregateDepsTask` calls.

**Result:** `hiltAggregateDepsFullDebug` fails with:

```
NoSuchMethodError: 'java.lang.String com.squareup.javapoet.ClassName.canonicalName()'
  at dagger.hilt.processor.internal.root.ir.AggregatedRootIrValidator.rootsToProcess
```

### What was tried

| Approach                                                                            | Result                                                                                                               |
|-------------------------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------|
| Dagger 2.59.2 (latest)                                                              | Hilt plugin requires AGP 9.0.0+ (project uses AGP 8.13.2)                                                            |
| Dagger 2.58 (last AGP 8 compatible)                                                 | JavaPoet conflict with databinding                                                                                   |
| `resolutionStrategy.force("com.squareup:javapoet:1.13.0")` on buildscript classpath | Resolution shows `1.10.0 -> 1.13.0` but NoIsolation worker still loads old class                                     |
| Move Hilt plugin to buildscript classpath                                           | Same javapoet issue — shared classloader                                                                             |
| `enableAggregatingTask = false`                                                     | Bypasses the javapoet issue BUT Hilt can't discover `@InstallIn` modules from library modules — all bindings missing |
| Plugin-free approach (`Hilt_MainApp` base class)                                    | Works but is a workaround, user rejected                                                                             |
| AGP 9 upgrade                                                                       | Databinding modules (combov2, eopatch, medtrum) have issues with AGP 9                                               |

### Resolution Path: Remove databinding first

Modules using `dataBinding = true`:

- `app` — already Compose, databinding likely unused (verify)
- `pump/combov2` — needs Compose migration
- `pump/eopatch` — needs Compose migration
- `pump/medtrum` — needs Compose migration

`viewBinding = true` (enabled globally in convention plugin) does NOT cause this issue —
it doesn't pull in databinding-compiler-common.

**Once all 4 modules drop `dataBinding = true`, the javapoet conflict disappears and Hilt
migration can proceed.** This also unblocks the AGP 9 upgrade.

---

## Phases (to execute after databinding removal)

### Phase 0: Add Hilt to Gradle

**Files:**

- `gradle/libs.versions.toml` — add:
  ```toml
  hiltNavigationCompose = "1.2.0"
  # in [plugins]:
  hilt = { id = "com.google.dagger.hilt.android", version.ref = "dagger" }
  # in [libraries]:
  com-google-dagger-hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "dagger" }
  com-google-dagger-hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "dagger" }
  androidx-hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
  ```
- `core/interfaces/build.gradle.kts` — add `api(libs.com.google.dagger.hilt.android)`

### Phase 1: Add `@InstallIn` to ALL `@Module` classes

Every `@Module` needs `@InstallIn(SingletonComponent::class)` — both top-level AND inner modules.

**IMPORTANT finding:** The original plan assumed inner modules inherit `@InstallIn` from parents
via `includes = [...]`. This is WRONG — Hilt enforces the check on every `@Module` class
independently. All inner `@Module` classes need their own `@InstallIn`.

**Top-level modules (39):**
App: `AppModule`, `PluginsListModule`, `ActivitiesModule`, `ReceiversModule`
Core/DB: `CoreModule`, `ValidatorsModule`, `DatabaseModule`, `PersistenceModule`,
`ImplementationModule`, `LoggerModule`, `SharedImplModule`, `UiModule`, `WorkflowModule`
Plugins: `ApsModule`, `AutomationModule`, `ConfigurationModule`, `PluginsConstraintsModule`,
`InsulinModule`, `PluginsModule`, `SourceModule`, `SyncModule`, `OpenHumansModule`
Pumps: all 17 pump `*Module.kt`

**Inner modules (~25):**

- `AppModule.Provide`, `AppModule.AppBindings`
- `PersistenceModule.Bindings`
- `WorkflowModule.WorkflowBindings`
- `VirtualPumpModule.Bindings`
- `UiModule.Bindings`
- `ImplementationModule.Bindings`
- `CommandQueueModule.Bindings` (needs import added — no hilt imports present)
- `CoreModule.Bindings`
- `SourceModule.Bindings`
- `SyncModule.Provide`, `SyncModule.Binding`
- `SetupWizardModule.Provide` (needs imports added)
- `ConfigurationModule.Bindings`
- `ApsModule.Bindings`
- `AutomationModule.Bindings`
- `PluginsConstraintsModule.Bindings`
- `ObjectivesModule.Provide`, `ObjectivesModule.ObjectivesListModule` (needs imports added)
- `SkinsModule.Bindings` (needs imports added)
- `OverviewModule.Provide`, `OverviewModule.Bindings` (needs imports added)
- `PluginsModule.Bindings`
- `ProfileModule.Bindings` (needs imports added)

**Skip:** `wear/WearModule` (separate DaggerApplication), `TestModule` (test infrastructure)

**Note:** `database:impl` does NOT depend on `core:interfaces`, so it needs
`api(libs.com.google.dagger.hilt.android)` added directly to its `build.gradle.kts`.

### Phase 2: Switch MainApp to `@HiltAndroidApp`

**Files:**

1. `app/build.gradle.kts`:
    - Add `alias(libs.plugins.hilt)` in plugins block
    - Add `implementation(libs.com.google.dagger.hilt.android)` in dependencies
    - Add `ksp(libs.com.google.dagger.hilt.compiler)` in dependencies

2. `app/.../MainApp.kt`:
   ```kotlin
   @HiltAndroidApp
   class MainApp : Application(), HasAndroidInjector {
       @Inject lateinit var androidInjector: DispatchingAndroidInjector<Any>
       override fun androidInjector(): AndroidInjector<Any> = androidInjector
       // DELETE: applicationInjector(), appComponent field, DaggerAppComponent imports
       // All other @Inject fields stay unchanged
   }
   ```

3. `app/.../di/AppModule.kt`:
    - Remove: `@Binds fun bindContext(mainApp: MainApp): Context`
    - Remove: `@Binds fun bindInjector(mainApp: MainApp): HasAndroidInjector`
    - Add: `@Provides fun provideContext(@ApplicationContext context: Context): Context = context`
    - Add:
      `@Provides fun provideHasAndroidInjector(@ApplicationContext context: Context): HasAndroidInjector = context.applicationContext as HasAndroidInjector`

4. Delete `app/.../di/AppComponent.kt` (Hilt generates the component)

**Note:** `TestAppComponent` + `TestModule` are independent. `TestModule` has its own
`bindContext(TestApplication)` and `bindInjector(TestApplication)`. Unaffected.

### Phase 3: `@AndroidEntryPoint` on ComposeMainActivity

1. `app/.../ComposeMainActivity.kt`:
    - Add `@AndroidEntryPoint`
    - Change base from `DaggerAppCompatActivityWithResult` to `AppCompatActivity`
    - Copy needed fields from `DaggerAppCompatActivityWithResult` as `@Inject lateinit var`
    - Copy activity result launcher setup code

2. `app/.../di/ActivitiesModule.kt`:
    - Remove: `@ContributesAndroidInjector abstract fun contributesComposeMainActivity()`

3. `ui/build.gradle.kts`:
    - Add: `implementation(libs.androidx.hilt.navigation.compose)`

### Phase 4: Convert ViewModels to `@HiltViewModel`

**Architecture**: Screen composables own their VMs via default parameters (Google best practice).
This makes composables self-contained, reusable, and testable (pass fake VM in tests).

#### 4a: Dialog ViewModels (NavBackStackEntry-scoped) — 6 VMs

Dialog VMs get **fresh instances per navigation** — each dialog open creates a new VM.
This fixes the stale-state bug where dialog VMs retained old values between navigations.

| ViewModel                  | Nav args via SavedStateHandle      |
|----------------------------|------------------------------------|
| `CareDialogViewModel`      | `eventTypeOrdinal: Int`            |
| `FillDialogViewModel`      | `preselect: Int`                   |
| `CarbsDialogViewModel`     | none                               |
| `InsulinDialogViewModel`   | none                               |
| `TreatmentDialogViewModel` | none                               |
| `WizardDialogViewModel`    | `carbs: String?`, `notes: String?` |

**Changes per dialog VM file** (in `ui/src/main/kotlin/.../`):

1. Add `@HiltViewModel` annotation + import
2. For VMs with nav args: add `savedStateHandle: SavedStateHandle` as first constructor param,
   read args in `init {}` block
3. Remove init guards (`initialized`, `lastPreselect`, `lastEventType`) — VM is fresh each time
4. Remove TODO comments about HiltViewModel migration
5. Remove `initForEventType()` / `init(preselect)` / `init(carbs, notes)` functions —
   initialization moves to `init {}` block using SavedStateHandle

**Changes per dialog Screen composable**:

1. Add default parameter: `viewModel: FooDialogViewModel = hiltViewModel()`
2. Remove nav-arg parameters that are now read by VM from SavedStateHandle
3. Remove `LaunchedEffect` calls that passed args to VM init functions

**Changes in ComposeMainActivity NavHost**:

```kotlin
// BEFORE:
composable(route = AppRoute.CareDialog.route, arguments = ...) { backStackEntry ->
    val ordinal = backStackEntry.arguments?.getInt("eventTypeOrdinal") ?: 0
    val eventType = UiInteraction.EventType.entries[ordinal]
    val vm: CareDialogViewModel = viewModel(factory = daggerViewModel { careDialogViewModel })
    CareDialogScreen(viewModel = vm, eventType = eventType, ...)
}

// AFTER:
composable(route = AppRoute.CareDialog.route, arguments = ...) {
    CareDialogScreen(onNavigateBack = { navController.popBackStack() }, ...)
}
```

Remove 6 `@Inject lateinit var` dialog VM fields from Activity.

#### 4b: Activity-scoped ViewModels — 19 VMs

These VMs are shared across the activity lifetime (survive rotation, shared between composables).

| ViewModel                        | Has init {} | Notes                                           |
|----------------------------------|-------------|-------------------------------------------------|
| `MainViewModel`                  | no          | Used in multiple places (MainScreen, callbacks) |
| `ManageViewModel`                | no          | Shared: ManageSheetHost + MainScreen            |
| `MaintenanceViewModel`           | no          |                                                 |
| `StatusViewModel`                | no          |                                                 |
| `TreatmentViewModel`             | no          |                                                 |
| `AutomationViewModel`            | no          |                                                 |
| `GraphViewModel`                 | no          |                                                 |
| `TreatmentsViewModel`            | no          |                                                 |
| `TempTargetManagementViewModel`  | yes         | Remove `@Singleton`                             |
| `QuickWizardManagementViewModel` | yes         | Remove `@Singleton`, has `onCleared()`          |
| `StatsViewModel`                 | no          |                                                 |
| `ProfileHelperViewModel`         | no          |                                                 |
| `ProfileEditorViewModel`         | no          |                                                 |
| `ProfileManagementViewModel`     | no          |                                                 |
| `RunningModeManagementViewModel` | no          |                                                 |
| `ImportViewModel`                | no          |                                                 |
| `SearchViewModel`                | yes         | `init { observeSearchQuery() }`                 |
| `PermissionsViewModel`           | no          |                                                 |
| `ConfigurationViewModel`         | yes         | `init { loadCategories() }`                     |

**Changes per activity-scoped VM file**:

1. Add `@HiltViewModel` annotation + import
2. Remove `@Singleton` from `TempTargetManagementViewModel` and `QuickWizardManagementViewModel`

**Changes per Screen composable** (best-practice pattern):

```kotlin
// BEFORE:
@Composable
fun ProfileManagementScreen(
    viewModel: ProfileManagementViewModel,
    onNavigateBack: () -> Unit,
)

// AFTER:
@Composable
fun ProfileManagementScreen(
    viewModel: ProfileManagementViewModel = hiltViewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    ),
    onNavigateBack: () -> Unit,
)
```

For VMs used on a single screen: simple `= hiltViewModel(...)` default.
For shared VMs (`mainViewModel`, `manageViewModel`, `searchViewModel`): activity scope
ensures the same instance is shared across all composables that use it.

**Changes in ComposeMainActivity NavHost**:

```kotlin
// BEFORE:
composable(AppRoute.Profile.route) {
    ProfileManagementScreen(
        viewModel = profileManagementViewModel,
        onNavigateBack = { navController.popBackStack() },
    )
}

// AFTER:
composable(AppRoute.Profile.route) {
    ProfileManagementScreen(
        onNavigateBack = { navController.popBackStack() },
    )
}
```

Remove 19 `@Inject lateinit var` activity-scoped VM fields from Activity.

**Special cases in ComposeMainActivity**: Some VMs are called directly in the NavHost
(e.g., `mainViewModel.openDrawer()`, `searchViewModel.onQueryChanged()`). These call sites
need local `val vm = hiltViewModel<FooVM>(...)` in the NavHost scope, or the composable
that uses them needs to expose callbacks instead. Handle case-by-case.

#### 4c: Plugin module ViewModels — 12 VMs

Migrate plugin Compose ViewModels from `ViewModelFactory` + `@IntoMap` + `@ViewModelKey`
to `@HiltViewModel`. Delete legacy Fragments that have been replaced by Compose.

##### Source module (1 VM)

| ViewModel           | File                                              |
|---------------------|---------------------------------------------------|
| `BgSourceViewModel` | `plugins/source/.../compose/BgSourceViewModel.kt` |

**Changes:**

1. Add `@HiltViewModel` to `BgSourceViewModel`
2. In `BgSourceComposeContent`: replace `ViewModelProvider(owner, factory)[...]` with
   `viewModel: BgSourceViewModel = hiltViewModel()` default parameter
3. Remove `viewModelFactory` parameter from `BgSourceComposeContent`
4. In all source plugins (13 plugins: DexcomPlugin, GlimpPlugin, etc.): remove
   `@Inject lateinit var viewModelFactory` field, stop passing factory to composable
5. In `SourceModule`: remove `@Binds ViewModelFactory` binding and `@IntoMap` VM binding

##### Sync module (7 VMs)

| ViewModel                  | File                                        | Used in            |
|----------------------------|---------------------------------------------|--------------------|
| `NSClientViewModel`        | `plugins/sync/.../nsShared/compose/`        | Compose + Fragment |
| `XdripViewModel`           | `plugins/sync/.../xdrip/compose/`           | Compose + Fragment |
| `TidepoolViewModel`        | `plugins/sync/.../tidepool/compose/`        | Compose + Fragment |
| `WearViewModel`            | `plugins/sync/.../wear/compose/`            | Compose only       |
| `SmsCommunicatorViewModel` | `plugins/sync/.../smsCommunicator/compose/` | Compose only       |
| `OHViewModel`              | `plugins/sync/.../openhumans/compose/`      | Compose only       |
| `OHLoginViewModel`         | `plugins/sync/.../openhumans/ui/`           | Compose Activity   |

**Changes:**

1. Add `@HiltViewModel` to all 7 VMs
2. In each `*ComposeContent`: replace `ViewModelProvider(owner, factory)[...]` with
   `viewModel: FooViewModel = hiltViewModel()` default parameter
3. Remove `viewModelFactory` parameters from composables
4. In sync plugins: remove `@Inject lateinit var viewModelFactory` fields
5. `OHLoginActivity`: change to `@AndroidEntryPoint`, use `hiltViewModel()` instead of
   `by viewModels { viewModelFactory }`
6. In `SyncModule`: remove `@Binds ViewModelFactory` binding and all `@IntoMap` VM bindings
7. In `OpenHumansModule`: remove `@IntoMap` VM bindings for `OHViewModel`/`OHLoginViewModel`
8. **Delete legacy Fragments** (replaced by Compose):
    - `plugins/sync/.../nsShared/NSClientFragment.kt`
    - `plugins/sync/.../xdrip/XdripFragment.kt`
    - `plugins/sync/.../tidepool/TidepoolFragment.kt`
9. Remove `@ContributesAndroidInjector` entries for deleted Fragments from `SyncModule`

##### Medtrum module (2 VMs)

| ViewModel                  | File                                                   |
|----------------------------|--------------------------------------------------------|
| `MedtrumOverviewViewModel` | `pump/medtrum/.../compose/MedtrumOverviewViewModel.kt` |
| `MedtrumPatchViewModel`    | `pump/medtrum/.../compose/MedtrumPatchViewModel.kt`    |

**Changes:**

1. Add `@HiltViewModel` to both VMs
2. In `MedtrumComposeContent`: replace `ViewModelProvider(owner, factory)[...]` with
   `viewModel: FooViewModel = hiltViewModel()` default parameters
3. Remove `viewModelFactory` parameter from composable
4. In `MedtrumPlugin`: remove `@Inject @MedtrumPluginQualifier lateinit var viewModelFactory`
5. In `MedtrumModule`: remove `@Provides ViewModelFactory`, remove `@IntoMap` VM bindings,
   remove `@MedtrumPluginQualifier` usage for VMs
6. Delete `@MedtrumPluginQualifier` if no longer used anywhere

##### Eopatch module (2 VMs)

| ViewModel                  | File                                                   |
|----------------------------|--------------------------------------------------------|
| `EopatchOverviewViewModel` | `pump/eopatch/.../compose/EopatchOverviewViewModel.kt` |
| `EopatchPatchViewModel`    | `pump/eopatch/.../compose/EopatchPatchViewModel.kt`    |

**Changes:**

1. Add `@HiltViewModel` to both VMs
2. In `EopatchComposeContent`: replace `ViewModelProvider(owner, factory)[...]` with
   `viewModel: FooViewModel = hiltViewModel()` default parameters
3. Remove `viewModelFactory` parameter from composable
4. In `EopatchPlugin` (or equivalent): remove
   `@Inject @EopatchPluginQualifier lateinit var viewModelFactory`
5. In `EopatchModule`: remove `@Provides ViewModelFactory`, remove `@IntoMap` VM bindings,
   remove `@EopatchPluginQualifier` usage for VMs
6. Delete `@EopatchPluginQualifier` if no longer used anywhere

### Phase 5: Cleanup

- Remove `daggerViewModel` helper function from ComposeMainActivity
- Delete `ViewModelFactory` + `ViewModelKey` from `core/ui/compose/ViewModelHelpers.kt`
  (no longer used — omnipod has its own copy in `OmnipodInjectHelpers.kt`)
- Remove unused imports from all modified files
- Verify no remaining `@Inject lateinit var` VM fields in Activity
- Verify no remaining `ViewModelProvider(owner, factory)` calls in Compose code
- `hilt-navigation-compose` dependency: add to `plugins/source`, `plugins/sync`,
  `pump/medtrum`, `pump/eopatch` build.gradle.kts files

---

## Key Findings

### AGP 9 + Dagger 2.59.2 works with Hilt

With AGP 9.0.1 and no databinding, Dagger 2.59.2 Hilt plugin works correctly.

### Hilt plugin compatibility matrix

| Dagger | Min AGP | JavaPoet needed | Works with databinding?              |
|--------|---------|-----------------|--------------------------------------|
| 2.58   | 8.x     | 1.13.0          | NO — javapoet conflict               |
| 2.59+  | 9.0.0+  | 1.13.0          | YES (no databinding after migration) |

### Every library module needs `ksp(hilt-compiler)`

Hilt discovers `@InstallIn` modules via generated metadata. Each module must have
`ksp(libs.com.google.dagger.hilt.compiler)` alongside `ksp(libs.com.google.dagger.compiler)`.
Without it, the aggregating task can't find bindings (88 MissingBinding errors).

### `@ContributesAndroidInjector` subcomponent modules must NOT have `@InstallIn`

Modules used as `modules = [...]` in `@ContributesAndroidInjector` belong to the
generated subcomponent, not `SingletonComponent`. Use `@DisableInstallInCheck` instead.
Example: `OmnipodWizardModule`, `OmnipodDashWizardViewModelsModule`,
`OmnipodErosWizardViewModelsModule`.

### Test-only modules need `@DisableInstallInCheck`

`TestDatabaseModule` provides a duplicate `AppDatabase` binding. It should not be in
`SingletonComponent` — use `@DisableInstallInCheck` to exclude from Hilt.

### `AndroidInjectionModule` must be explicitly included

Unlike the old `AppComponent` which listed `AndroidInjectionModule::class`, Hilt doesn't
auto-include it. Added it to `AppModule`'s `includes` for `dagger.android` compatibility.

### `enableAggregatingTask = false` is not viable

Without the aggregating task, Hilt can't discover `@InstallIn` modules from library modules.
All bindings from other modules become invisible to the generated component. This defeats the
purpose since almost all bindings are in library modules.

### Inner `@Module` classes need `@InstallIn`

Contrary to Dagger documentation, Hilt enforces `@InstallIn` on every `@Module` class,
including inner classes referenced via `includes = [...]`. They do NOT inherit from parents.

### `database:impl` is isolated from `core:interfaces`

`database:impl` doesn't depend on `core:interfaces`, so it doesn't get `hilt-android`
transitively. Needs direct `api(libs.com.google.dagger.hilt.android)` in its build.gradle.kts.

### Omnipod VM infrastructure is isolated

Omnipod has its own `ViewModelKey` + `ViewModelFactory` in `OmnipodInjectHelpers.kt`
(not the shared `core/ui` version). Uses `@OmnipodPluginQualifier` + `@ContributesAndroidInjector`
subcomponents. Completely isolated — untouched by this migration. Will be cleaned up when
omnipod Fragments are migrated to Compose.

### Plugin module qualifiers become unnecessary

`@MedtrumPluginQualifier` and `@EopatchPluginQualifier` exist to isolate `ViewModelFactory`
multi-bindings between pump modules. With `@HiltViewModel`, each VM is resolved directly
by type — no multi-binding map, no qualifier needed.

### Wear module is independent

Has its own `DaggerApplication` + `WearComponent`. Completely separate, untouched by migration.

---

## Prerequisites (ALL COMPLETE)

1. ~~Remove `dataBinding = true` from all modules~~ — DONE (Compose migration)
2. ~~AGP 9 upgrade~~ — DONE (AGP 9.0.1)
3. ~~Dagger 2.59.2~~ — DONE
