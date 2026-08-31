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
`com.android.library` with an annotation-processor DI framework, XML layouts and ViewBinding,
`R.string` throughout, and a `Context` threaded through its classes. Convert in this order, so each
step is green on its own and the DI stays working:

1. **DI first.** Any annotation-processor DI out, Metro in. Nothing else can proceed while a processor
   is in the module, and this is where the constructor traps below bite.
2. **UI to Compose**, if it still has XML. A View-based screen cannot move to common code at all.
3. **Strings** to the generated `TextRef`s.
4. **Platform ports** - replace `Context` and other Android types with interfaces, checking first
   whether the parameter is used at all.
5. **Flip the module type**, move the sources, add the iOS targets.
6. **Move files to commonMain** and let the iOS compile tell you what is really left.

Then wire it up: `include` in `settings.gradle`; add it to `:appshell` as `api(...)` if it has
screens the navigation graph reaches; add it to `migratedModules` in `ios/shell/build.gradle.kts`
once it builds for iOS; register its string owner in `MainApp` and `BaseTestApp`.

## The hard precondition: no annotation-processor DI in the module

No KMP module in this tree runs a DI annotation processor, and that is not a coincidence - a processor
that generates Java has nothing to generate into in a multiplatform module. Metro is a **compiler
plugin**, so it works everywhere and is the only DI here.

An earlier "it still builds" is usually stale generated output. **Always `rm -rf <module>/build`
before believing a processor is unnecessary.**

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

This is the worst one, and it has happened four times. A class that nothing contributes is **never**
constructed by `app/src/test/.../di/metro/TestRoot.kt`. The moment it gets `@ContributesBinding`,
Metro builds it for real in every graph test, with every dependency resolved for real.

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

Counting files with no `android`/`androidx`/`java` import over-estimates badly: a
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

### Kotlin/Native rejects a comma in a backticked test name

`fun \`the tag is appended, making it longer\`()` compiles on JVM and fails Native with
`Name contains illegal characters: ","`. It only shows up once a test reaches commonTest, so a
JVM-only test can carry one for years. Rewrite the name; do not rename the test's meaning.

### Moving crypto: the provider is stricter than javax was

`javax.crypto` built a fresh `Cipher` on every call, which hid API misuse. A multiplatform provider
reuses objects and enforces the rules, so a migration can fail on something that was always wrong.
Moving `ClientControlCrypto` turned up **two tests reusing one IV with one key** for AES-GCM -
forbidden, and the provider says so (`Cannot reuse iv for GCM encryption`). Production was fine
because the IV is generated per use; only the fixtures were wrong.

Two rules when the format is already on the wire:

- **Keep golden vectors and put them in commonTest.** Digests minted by the old implementation are
  what prove the new one emits the same bytes. On Windows `mingwX64Test` runs them against
  Kotlin/Native, so the Native path is checked long before a Mac is available.
- **Watch the packaging, not the algorithm.** The primitives interoperate by definition; the silent
  breakage is in how they are assembled - whether the AEAD nonce is prepended or stored separately,
  whether the GCM tag is appended, hex case. In cryptography-kotlin the plain `encryptBlocking`
  generates and prepends its own nonce; `encryptWithIvBlocking` (behind `@DelicateCryptographyApi`)
  is the one that matches a format storing the IV separately.

### Positional `mock()` constructor arguments hide a wrong wiring

Tests here build big plugins positionally, with long runs of bare `mock()`. Adding or removing a
constructor parameter shifts everything after it, and nothing complains: `mock()` fits any type.

The failure surfaces far away and looks nothing like the cause. Passing an unstubbed `mock()` where
the class collects a `Flow` gives a **null** upstream, which fails as
`UncaughtExceptionsBeforeTest` in whatever test happens to run next - not in the test that caused
it, and not with a message naming the parameter.

- Do not target these lines with `sed -i '<line>s/.../.../'`. Line numbers shift as soon as an
  import or a field is added above, and the edit then lands on the wrong call.
- After changing a constructor, grep every construction site and check the argument that matters is
  the **named field**, not a fresh `mock()`.
- `git stash` and re-run to tell "my change broke this" from "this was already flaky". The suite has
  a real `UncaughtExceptionsBeforeTest` flake, so the two are easy to confuse.

### Splitting a WorkManager worker

A worker is almost always a body wrapped in a class WorkManager can construct. `RunnerWorker` and
`WorkOutcome` in `:core:objects` exist for this: the body becomes a `XxxRunner` in commonMain with
`suspend fun run(): WorkOutcome`, and the worker keeps only the `@AssistedInject` scaffolding.

The nine NS client workers all transformed the same way, so it is scriptable - drop the
`@Assisted context`/`params` and `fabricPrivacy` parameters, make `aapsLogger` a `private val`, swap
`@AssistedInject constructor` for `@Inject`, drop the `LoggingWorker` supertype and the
`@AssistedFactory`, and map the returns:

| worker | runner |
|---|---|
| `Result.success()` | `WorkOutcome.Success` |
| `Result.success(workDataOf("Result" to x))` | `WorkOutcome.Skipped(x)` |
| `Result.failure(workDataOf("Error" to x))` | `WorkOutcome.Failure(x)` |

**Review the mapping table by hand afterwards** - it is the only part that carries meaning. A
`Result.success` with output data is not the same as a bare one: `WorkOutcome.Skipped` was added
precisely because `LoadBgWorker` reported "Load not enabled" that way, and collapsing it into
`Success` silently dropped a signal a test was asserting on.

Worker tests construct the worker directly, so each needs its argument list wrapped:
`XxxWorker(appContext, params, aapsLogger, fabricPrivacy, XxxRunner(aapsLogger, ...rest))`.

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

### Test libraries are JVM-only, so a fixtures module barely moves

JUnit 5, Mockito and RxJava have no Kotlin/Native artifacts. Anything built on them is Android by
nature, not by accident, and no amount of work moves it. In `:shared:tests` that left exactly one
file in commonMain out of eleven:

| stays on Android | why |
|---|---|
| `TestBase`, `TestBaseWithProfile` | `@ExtendWith(MockitoExtension)`, JUnit 5 lifecycle |
| `TestAapsSchedulers` | RxJava |
| `TextRefStubs` | the generated `*StringIds` maps only exist in androidMain |
| `TestPumpPlugin` | `ResourceHelper` is androidMain; `PumpEnactResultObject` is in `:implementation` |
| `HardLimitsMock` | `HardLimits` still has abstract `Int` (resource id) overloads |
| `BundleMock`, `SharedPreferencesMock` | Android types are the point of them |
| `MemberInjectorCoverage`, `SplitBrainCoverage` | `JarFile` reflection over compiled output |

Flip such a module for the module type and the processor removal, not for the sharing. Say so up
front rather than discovering it file by file.

**A fixtures module must be excluded from `checkMigratedModules`.** It declares `iosArm64()` so that
common tests can use it, but `migratedModules` feeds the exported framework header, and test helpers
do not belong in the API Swift sees. There is a `filterNot` in `ios/shell/build.gradle.kts` for this.

## `:ios:shell:checkMigratedModules` will fail next

Once a module builds for iOS, the ios-branch guard fails until it is listed. It names the module and
what to do: add it to `migratedModules` in `ios/shell/build.gradle.kts` and bump
`ShellInfo.LINKED_MODULES`. Expect this on every flip.

## Do not run a javax-stripping sweep over `:app`'s DI files

`AppRootGraph`, `MetroGraphs`, `AapsLeaves` and `CoreObjectsModule` legitimately import
`javax.inject.Singleton` and `javax.inject.Inject`. A helper that strips javax while adding an import
breaks them with `Unresolved reference 'Singleton'`. Edit those by hand.
