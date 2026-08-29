---
name: kmp-module-flip
description: Turn an ordinary Android library in this repo into a Kotlin Multiplatform module, and move its code to commonMain. Use when converting a module to KMP, when a KMP module fails to build after conversion, or when deciding what can move to commonMain.
---

# Flipping a module to Kotlin Multiplatform

This is the repeatable part of the KMP migration. It is written from modules already flipped
(`:implementation`, `:database:impl`, `:database:persistence`, `:core:*`, `:ui`, `:plugins:*`,
`:appshell`), so each step is something that has actually gone wrong at least once.

**Keep this file up to date.** When you flip a module and hit something that is not written here, add
it before you finish. When a step here turns out to be wrong or no longer needed, correct it in the
same change rather than working around it. A stale recipe costs more than no recipe, because it is
believed.

## The target state, and what a module arriving from elsewhere has to become

`:plugins:calibration` is the closest thing to the finished shape: 9 files in commonMain, 1 in
androidMain, 1 in iosMain, tests in androidHostTest. Aim at that.

| | target |
|---|---|
| module type | `kotlin("multiplatform")` + `libs.plugins.android.kmp.library` |
| flavours | none - only `:app`, `:wear`, `:wear:watchfacepush`, `:benchmark` have them |
| DI | Metro only. `@Inject`, `@SingleIn(AppScope::class)`, `@ContributesBinding`; a plugin registers itself with `@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())` **from commonMain** |
| UI | Compose Multiplatform in commonMain; `androidx.compose.*` package names are the same |
| strings | `XxxStrings` (`TextRef`) generated into commonMain; no `R.string` and no `@StringRes Int` in any shared signature |
| platform work | behind an interface in commonMain, implemented in androidMain (and iosMain when it exists) |
| source sets | `commonMain` holds the bulk; `androidMain` holds only ports and Android entry points |
| tests | `androidHostTest`, run by `testAndroidHostTest`; instrumented in `androidDeviceTest` |
| targets | `iosArm64()` and `iosSimulatorArm64()` declared from the start |

A module written against the old architecture - a pump driver from another fork, say - will arrive as
`com.android.library` with Dagger or Hilt, XML layouts and ViewBinding, `R.string` throughout,
`javax.inject`, and a `Context` threaded through its classes. Convert in this order, so each step is
green on its own and the DI stays working:

1. **DI first.** Dagger or Hilt out, Metro in. Nothing else can proceed while the processor is in the
   module, and this is where the constructor traps below bite.
2. **UI to Compose**, if it still has XML. A View-based screen cannot move to common code at all.
3. **Strings** to the generated `TextRef`s.
4. **Platform ports** - replace `Context` and other Android types with interfaces, checking first
   whether the parameter is used at all.
5. **Flip the module type**, move the sources, add the iOS targets.
6. **Move files to commonMain** and let the iOS compile tell you what is really left.

Then wire it up: `include` in `settings.gradle`; add it to `:appshell` as `api(...)` if it has
screens the navigation graph reaches; add it to `migratedModules` in `ios/shell/build.gradle.kts`
once it builds for iOS; register its string owner in `MainApp` and `BaseTestApp`.

## The hard precondition: no Dagger or Hilt processor in the module

No KMP module in this tree runs Dagger or Hilt KSP, and that is not a coincidence. Without
`ksp(hilt.compiler)` the module's `hilt_aggregated_deps` are not generated, `:app`'s Hilt never sees
its `@InstallIn` modules, and the build fails with `MissingBinding` for everything they bound.

An earlier "it still builds" is usually stale generated output. **Always `rm -rf <module>/build`
before believing a processor is unnecessary.**

Two ways out, both used here:

- **Move the `@InstallIn` module to `:app`.** Works when the bound classes are public.
- **Give the binding to Metro.** Needed when a constructor parameter is `internal` to the module,
  because Dagger would have to generate the factory in `:app`, where it cannot see the type.

A Hilt `@EntryPoint` counts as a Dagger module.

## Build file

Copy `core/ui/build.gradle.kts`. It is the closest template: resources, Compose and Robolectric.

- `kotlin("multiplatform")` + `alias(libs.plugins.android.kmp.library)`. **Not** `com.android.library`
  - AGP 9 refuses that plugin together with the multiplatform plugin.
- **No convention plugin can be applied** (`android-module-dependencies`, `test-module-dependencies`,
  `compose-test-module-dependencies`, `jacoco-module-dependencies`, `all-open-dependencies`) - they
  all apply `com.android.library`. Restate by hand what you need: the `lint { disable += ... }` block,
  `withHostTest { isIncludeAndroidResources = true }`, the test dependencies, the
  `JacocoTaskExtension` block, and `kotlin("plugin.allopen")` with its `allOpen { annotation(...) }`.
- `androidResources { enable = true }` - off by default here, unlike a plain Android library.
- There are **no product flavours and no build types**, so `debugImplementation` does not exist.
- Add `iosArm64()` and `iosSimulatorArm64()` as soon as anything lands in commonMain. They are what
  makes an Android import in common code fail the build instead of quietly compiling.

### Flavours are no longer a problem

Older modules carry a `ProductFlavorAttr` pin to disambiguate a flavoured dependency. **Do not copy
it into a new module.** Product flavours were removed from the library convention plugin, so only
`:app`, `:wear`, `:wear:watchfacepush` and `:benchmark` have flavours now, and an unflavoured
consumer resolves them without help. If you see the pin in an existing build file, it is left over
and can go.

### The dependency list the convention plugin used to supply

Read `buildSrc/src/main/kotlin/test-module-dependencies.gradle.kts` **before** flipping and copy the
whole list, rather than finding it one compile failure at a time: `kotlin("test")`,
`org-junit-jupiter`, `org-junit-jupiter-api`, `org-junit-platform-launcher`,
`org-mockito-junit-jupiter`, `org-mockito-kotlin`, `joda-time`, `com-google-truth`,
`org-skyscreamer-jsonassert`, `kotlinx-coroutines-test`. Add `libs.org.json.android` and
`org.robolectric` on top, plus the Compose test artifacts.

`libs.org.json.android` is the one that hurts if missed: `isReturnDefaultValues` makes the platform
`org.json` stub return null instead of throwing, and the shared profile fixtures in
`TestBaseWithProfile` then NPE. That once failed **121 of 210 tests** inside the shared base, nowhere
near the real cause.

### If the module has instrumented tests, it needs a second dependency list

`androidHostTest` is the easy one to remember, because a missing dependency there fails the build you
are already running. `androidDeviceTest` does not: nothing local compiles it, so a module can look
completely green and still be broken. `:plugins:sync` was pushed that way and only CI caught it.

Three separate things all have to be restated:

1. **The runner.** `withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }`.
   An empty `withDeviceTest { }` builds fine and has nothing to run the tests with.
2. **JUnit 4 dependencies**, from the `androidTestImplementation` lines of
   `test-module-dependencies.gradle.kts`: `androidx-test-ext`, `androidx-test-rules`,
   `com-google-truth`, `org-mockito-android`, `org-mockito-kotlin`, `kotlinx-coroutines-test`.
   Instrumented tests are JUnit 4; the host tests next to them are JUnit 5.
3. **An exclusion, if the module depends on `:shared:tests` from the device test.** That project
   carries JUnit 5, `TestBase` pulls it onto the device classpath, and dexing it fails with
   `Attempt to create a global synthetic for 'Record desugaring'` - JUnit 6 uses Java records.
   ```kotlin
   configurations.named("androidDeviceTestImplementation") {
       exclude(group = "org.junit.jupiter")
       exclude(group = "org.junit.platform")
   }
   ```

Verify locally before pushing - none of this needs a device:

```
./gradlew.bat :module:compileAndroidDeviceTest :module:assembleAndroidDeviceTest --no-daemon
```

Watch for a JUnit 4 test extending a JUnit 5 base class. `GarminDeviceClientTest` extends `TestBase`,
whose `@BeforeEach` never fires under `@RunWith(AndroidJUnit4)`. It happens to work because it only
touches a field initialised at construction, but anything relying on `openMocks` in that base would
get nulls and no warning.

## Source moves

`src/main` → `src/androidMain`, `src/test` → `src/androidHostTest`,
`src/androidTest` → `src/androidDeviceTest`.

**Grep the moved tests for the literal strings `src/test/` and `src/main/`.** A hard-coded path
compiles fine and fails only at runtime.

Task names change with the layout, and a wrong name **runs no tests and still exits 0**:

| source set | task |
|---|---|
| `androidHostTest` | `testAndroidHostTest` |
| `androidDeviceTest` | `connectedAndroidDeviceTest` |
| plain Android library | `testDebugUnitTest` |
| `:app` / `:wear` | `testFullDebugUnitTest` |

`.circleci/config.yml` names all of them, and `jacoco_aggregation.gradle.kts` picks the variant
directory per module. If you change a module's shape, check both.

## The Metro construction trap

This is the worst one, and it has happened four times. While a class is Dagger-owned, `testRoot()` in
`app/src/test/.../di/metro/TestRoot.kt` mocks `AapsLeaves` with `Answers.RETURNS_MOCKS`, so the class
is **never constructed** in unit tests. The moment it gets `@ContributesBinding`, Metro builds it for
real, with every dependency coming from those mocks.

| class | work done at construction | symptom |
|---|---|---|
| `VersionCheckerUtilsImpl` | property init opens `definition.json` | infinite read loop, OOM - looks like a machine problem |
| `OneTimePassword` | `init { configure() }` generates and persists an OTP secret | NPE, 11 graph tests |
| `AuthFlowOut` | property init builds `AuthorizationService`, which inspects installed browsers | `ExceptionInInitializerError` |
| `NotificationManagerImpl`, `FabricPrivacyImpl` | notification channel, Firebase flags, `while(true)` loop | not converted - see below |

**Before adding `@ContributesBinding` to a class, read its property initializers and `init` block for
I/O, sockets, or anything blocking.** Usually the fix is `by lazy { ... }`, which is better in
production too, since building the graph should not do file I/O.

**Laziness is not always right.** Where the `init` work is a *startup obligation* - registering a
receiver, setting analytics flags, starting a periodic loop - deferring it changes behaviour. Those
need an explicit `start()` from `MainApp.onCreate()`, which is a real refactor.

To confirm this class of failure: `git stash` the change and re-run the same test task. A baseline
that passes in about a minute against a run that never finishes is unambiguous.

## Moving code to commonMain

Counting files with no `android`/`androidx`/`java`/`javax`/`dagger` import over-estimates badly: a
file can name `app.aaps.core.ui.R` or take a `Context` indirectly. Compile for iOS to find out.

Beware the grep, too: `^import android` also matches `androidx`, so it hides every Compose file.
Anchor it as `^import android\.`.

### Strings are usually the biggest single blocker

`R.string.x` cannot exist in commonMain. The fix is `GenerateKeyStringsTask`, which turns the
module's `strings.xml` into a `XxxStrings` object of `TextRef.Named` (commonMain) plus a
`XxxStringIds` map (androidMain). Copy the task registration from `ui/build.gradle.kts`, then:

1. Add `kotlin.srcDir(...)` for the common output to `commonMain` and the android output to
   `androidMain`, and `implementation(project(":core:keys"))` to commonMain for `TextRef`.
2. Register the owner in **both** `MainApp.registerStringOwners()` and `BaseTestApp` - they must
   match, or instrumented tests render blank text and fail as "not displayed", a long way from the
   cause.
3. Swap `R.string.foo` for `XxxStrings.foo`. The substitution is name-preserving, so a wrong mapping
   cannot happen silently - it fails to compile.
4. In Composables import `app.aaps.core.ui.compose.stringResource` alongside the androidx one. Both
   are called `stringResource`; Kotlin picks by parameter type.

Sweep **every** receiver spelling, not just the obvious one: `rh.gs(R.string.x)`,
`resourceHelper.gs(...)`, the fully qualified `app.aaps.plugins.foo.R.string.x`, and any aliased
`FooR.string.x`. Each of these has been missed once and found only by a failing test.

After the swap, unwrap `TextRef.AndroidRes(XxxStrings.x)` - the argument is already a `TextRef`.

**Tests need the same swap**: `whenever(rh.gs(R.string.x))` becomes `whenever(rh.gs(XxxStrings.x))`,
and a blanket `rh.gs(anyInt())` stub becomes `doAnswer { ... }.whenever(rh).gs(any<TextRef>())` -
written that way round because `rh.gs(any<TextRef>())` on its own is ambiguous against the vararg
overload. If the module's owner is not registered in `shared/tests/TextRefStubs.kt`, an unstubbed
name resolves to itself, so expectations like `isNull()` become the string's own name. A Robolectric
Compose test must call `TextRefIdRegistry.register(owner) { XxxStringIds.idOf(it) }` in its setup,
exactly as `MainApp` does.

### An `Int` in an interface is a hard stop

`PumpEnactResult.comment(Int)` and `HardLimits.verifyHardLimits(..., valueName: Int, ...)` take a
resource id in the **interface**. A `TextRef` overload exists for `comment`; where one does not, the
implementation cannot move until the interface changes.

### Other common blockers

`javax.inject` (swap to `dev.zacsweers.metro.Inject` only for a class Metro already builds),
`@Synchronized`, `org.json`, `java.util.Calendar`, and `System.currentTimeMillis()` - the last is
just `Clock.System.now().toEpochMilliseconds()`.

### Lift the platform call out, keep the rule

When a class is blocked by one platform call, put that call behind an interface in commonMain and
implement it in androidMain, rather than leaving the whole class on Android. `PairedBtDevices` and
`LastKnownLocation` in `:plugins:automation` are the pattern: the trigger keeps its inputs,
serialization and matching logic in shared code, and only the Bluetooth or location call is
platform-specific. Implement the Android side straight away; other platforms can follow later.

Two cautions. Keep the platform maths on the platform where an exact result matters -
`LastKnownLocation.distanceTo` still calls `Location.distanceTo`, so no distance changes. And a port
whose implementation on some target would be a silent no-op is a safety problem in this app: a rule
the user relies on would quietly stop firing, so the feature should be visibly absent on that target
instead.

## `:ios:shell:checkMigratedModules` will fail next

Once a module builds for iOS, the ios-branch guard fails until it is listed. It names the module and
what to do: add it to `migratedModules` in `ios/shell/build.gradle.kts` and bump
`ShellInfo.LINKED_MODULES`. Expect this on every flip.

## Do not run a javax-stripping sweep over `:app`'s DI files

`AppRootGraph`, `MetroGraphs`, `AapsLeaves` and `CoreObjectsModule` legitimately import
`javax.inject.Singleton` and `javax.inject.Inject`. A helper that strips javax while adding an import
breaks them with `Unresolved reference 'Singleton'`. Edit those by hand.
