# Hilt Migration Plan — Compose ViewModels

## Goal

Migrate 26 ViewModels in `ComposeMainActivity` from plain Dagger `@Inject` fields to Hilt
`@HiltViewModel`. Dialog VMs get fresh instances per `NavBackStackEntry`, all VMs survive rotation.

Legacy XML Activities/Fragments/Services/Receivers stay on `dagger.android` — no changes needed.
Hilt and `dagger.android` coexist: Hilt auto-provides `DispatchingAndroidInjector<Any>`.

## Status: BLOCKED — Databinding vs JavaPoet conflict

### The Blocker

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

| Approach | Result |
|----------|--------|
| Dagger 2.59.2 (latest) | Hilt plugin requires AGP 9.0.0+ (project uses AGP 8.13.2) |
| Dagger 2.58 (last AGP 8 compatible) | JavaPoet conflict with databinding |
| `resolutionStrategy.force("com.squareup:javapoet:1.13.0")` on buildscript classpath | Resolution shows `1.10.0 -> 1.13.0` but NoIsolation worker still loads old class |
| Move Hilt plugin to buildscript classpath | Same javapoet issue — shared classloader |
| `enableAggregatingTask = false` | Bypasses the javapoet issue BUT Hilt can't discover `@InstallIn` modules from library modules — all bindings missing |
| Plugin-free approach (`Hilt_MainApp` base class) | Works but is a workaround, user rejected |
| AGP 9 upgrade | Databinding modules (combov2, eopatch, medtrum) have issues with AGP 9 |

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
   - Add: `@Provides fun provideHasAndroidInjector(@ApplicationContext context: Context): HasAndroidInjector = context.applicationContext as HasAndroidInjector`

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

#### 4a: Dialog ViewModels (fresh per navigation) — 6 files

| ViewModel | Nav args via SavedStateHandle |
|-----------|------------------------------|
| `CareDialogViewModel` | `eventTypeOrdinal: Int` |
| `FillDialogViewModel` | `preselect: Int` |
| `CarbsDialogViewModel` | none |
| `InsulinDialogViewModel` | none |
| `TreatmentDialogViewModel` | none |
| `WizardDialogViewModel` | `carbs: Int`, `notes: String` |

Pattern:
```kotlin
@HiltViewModel
class FooDialogViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    // ... other deps
) : ViewModel() {
    // Read args from savedStateHandle in init
}
```

In NavHost: replace `daggerViewModel { ... }` with `hiltViewModel()`.
Remove dialog VM `@Inject lateinit var` fields from Activity.
Remove arg parameters from dialog Screen composables (args are in VM now).

#### 4b: Activity-scoped ViewModels — 20 files

Same `@HiltViewModel` change. Use `hiltViewModel()` with activity as ViewModelStoreOwner.
Remove 20 `@Inject lateinit var` VM fields from Activity.

#### 4c: Remove `@Singleton` from VMs

- `TempTargetManagementViewModel` — remove `@Singleton`
- `QuickWizardManagementViewModel` — remove `@Singleton`

### Phase 5: Remove rotation hacks

- Remove init guards: `lastPreselect`, `lastEventType`, `initialized`
- Remove `refreshData()`/`loadData()` split patterns
- Remove `daggerViewModel` helper function from ComposeMainActivity
- Remove TODO comments about HiltViewModel migration

### Phase 6: Cleanup

- Remove unused imports
- Keep `ViewModelFactory` + `ViewModelKey` in `core/ui/compose/ViewModelHelpers.kt`
  (still used by sync, medtrum, eopatch, omnipod for Fragment-based VMs)

---

## Key Findings from Attempt

### Hilt plugin compatibility matrix

| Dagger | Min AGP | JavaPoet needed | Works with databinding? |
|--------|---------|-----------------|------------------------|
| 2.58   | 8.x     | 1.13.0          | NO — javapoet conflict |
| 2.59+  | 9.0.0+  | 1.13.0          | Unknown (AGP 9 has own databinding issues) |

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

### Qualified VM factories in pump modules are safe

medtrum/eopatch/omnipod use `@MedtrumPluginQualifier` (etc.) with their own `@IntoMap` +
`ViewModelFactory`. These are qualified multi-bindings, completely isolated from Hilt's
ViewModel multi-binding. No conflict.

### `appComponent` field only used in MainApp.kt

Safe to delete — not referenced elsewhere.

### Wear module is independent

Has its own `DaggerApplication` + `WearComponent`. Completely separate, untouched by migration.

---

## Prerequisites (must complete before starting)

1. **Remove `dataBinding = true` from all modules:**
   - `app/build.gradle.kts` — verify nothing uses databinding, then remove
   - `pump/combov2/build.gradle.kts` — migrate layouts to Compose/viewBinding
   - `pump/eopatch/build.gradle.kts` — migrate layouts to Compose/viewBinding
   - `pump/medtrum/build.gradle.kts` — migrate layouts to Compose/viewBinding

2. **Verify build succeeds without databinding** before starting Hilt migration

3. **Consider Dagger version:**
   - After databinding removal, Dagger 2.58 should work (no javapoet conflict)
   - Alternatively, upgrade to latest Dagger if AGP 9 is also done
