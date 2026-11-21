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

`:plugins:calibration` is the closest thing to the finished shape: most code in commonMain, one
`expect` chart with an `actual` in androidMain, iosMain and jvmMain, and tests in androidHostTest.
Aim at that.

| | target |
|---|---|
| module type | `id("kmp-android-library")`, which applies `kotlin("multiplatform")`, the AGP KMP library plugin and `kmp-test-defaults` |
| flavours | none - only `:app`, `:wear` and `:wear:watchfacepush` have them |
| DI | Metro only. `@Inject`, `@SingleIn(AppScope::class)`, `@ContributesBinding`; a plugin registers itself with `@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())` **from commonMain** |
| UI | Compose Multiplatform in commonMain; `androidx.compose.*` package names are the same |
| strings | `XxxStrings` (`TextRef`) generated into commonMain; no `R.string` and no `@StringRes Int` in any shared signature |
| platform work | behind an interface in commonMain, implemented in androidMain (and iosMain when it exists) |
| source sets | `commonMain` holds the bulk; `androidMain` holds only ports and Android entry points |
| tests | `androidHostTest`, run by `testAndroidHostTest`; instrumented in `androidDeviceTest` |
| targets | `iosArm64()`, `iosSimulatorArm64()` and `jvm()` (desktop), all set by `kmp-android-library`. Every `expect` needs an `actual` in androidMain, iosMain and jvmMain |

A module written against the old architecture - a pump driver from another fork, say - will arrive as
`com.android.library` with an annotation-processor DI framework, XML layouts and ViewBinding,
`R.string` throughout, and a `Context` threaded through its classes. Convert in this order, so each
step is green on its own and the DI stays working:

1. **DI first.** Any annotation-processor DI out, Metro in. Nothing else can proceed while a processor
   is in the module, and this is where the constructor traps below bite.
2. **UI to Compose**, if it still has XML. A View-based screen cannot move to common code at all.
3. **Strings** to the generated `TextRef`s. `registerKeyStrings` configures the multiplatform extension
   and reads `src/androidMain/res`, so it fails on a plain Android library: in practice this step comes
   right after step 5.
4. **Platform ports** - replace `Context` and other Android types with interfaces, checking first
   whether the parameter is used at all.
5. **Flip the module type**, move the sources, add the iOS targets.
6. **Move files to commonMain** and let the iOS compile tell you what is really left.

Then wire it up: `include` in `settings.gradle`. A `:plugins:*` module reaches `:appshell` by
itself; another module with screens the navigation graph reaches needs `api(project(...))` there.
Add it to `migratedModules` in `ios/shell/build.gradle.kts` once it builds for iOS. If it has
strings, add one line to `StringOwnerModules.ALL` and call `registerKeyStrings("xxx")` (see the
strings section below). Nothing is registered by hand in the shells.

## The hard precondition: no annotation-processor DI in the module

No KMP module in this tree runs a DI annotation processor, and that is not a coincidence - a processor
that generates Java has nothing to generate into in a multiplatform module. Metro is a **compiler
plugin**, so it works everywhere and is the only DI here.

An earlier "it still builds" is usually stale generated output. **Always `rm -rf <module>/build`
before believing a processor is unnecessary.**

## Build file

Copy `plugins/smoothing/build.gradle.kts` for a small module, or `core/ui/build.gradle.kts` for one
with resources, Compose and Robolectric.

- `id("kmp-android-library")` replaces `kotlin("multiplatform")` + the AGP KMP library plugin +
  `kmp-test-defaults`. It also sets `compileSdk`, `minSdk`, the `lint { disable += ... }` block and
  the `iosArm64()`, `iosSimulatorArm64()` and `jvm()` targets. **Not** `com.android.library` - AGP 9
  refuses that plugin together with the multiplatform plugin.
- The module keeps only what is its own: `namespace`, `androidResources`, `withHostTest { ... }`, and
  `minSdk = min(Versions.minSdk, Versions.wearMinSdk)` if the watch uses it.
- The Android convention plugins (`android-module-dependencies`, `test-module-dependencies`,
  `compose-test-module-dependencies`, `jacoco-module-dependencies`) **cannot be applied** - they apply
  `com.android.library`. Write the test dependencies by hand. `all-open-dependencies` can be applied.
  The JaCoCo Robolectric setting and `jvmTarget` come from the root build file; do not restate them.
- `androidResources { enable = true }` - off by default here, unlike a plain Android library.
- There are **no product flavours and no build types**, so `debugImplementation` does not exist.

### Flavours are no longer a problem

Only `:app`, `:wear` and `:wear:watchfacepush` have product flavours, and an unflavoured consumer
resolves them without help. A module from another fork may carry a `ProductFlavorAttr` pin; drop it.

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

Verify locally before pushing - none of this needs a device (`./gradlew` on macOS):

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
| `commonTest`, `jvmTest` | `jvmTest` |
| `commonTest`, `iosTest` | `iosSimulatorArm64Test` (macOS only) |
| plain Android library | `testDebugUnitTest` |
| `:app` / `:wear` | `testFullDebugUnitTest` |

CI finds the modules by itself: `.github/workflows/unit-tests.yml` runs the unit test tasks above by
name, `.circleci/config.yml` runs the instrumented tests of every `androidTest` and
`androidDeviceTest` folder, and `buildSrc/src/main/kotlin/jacoco-aggregation.gradle.kts` asks each
module's plugin. Only a new task name or source-set name needs a change there.

## The Metro construction trap

This is the worst one, and it has happened four times. A class that nothing contributes is **never**
constructed by `app/src/test/.../di/metro/TestRoot.kt`. The moment it gets `@ContributesBinding`,
Metro builds it for real in every graph test, with every dependency resolved for real.

| class | work done at construction | symptom |
|---|---|---|
| `VersionCheckerUtilsImpl` | property init opens `definition.json` | infinite read loop, OOM - looks like a machine problem |
| `OneTimePassword` | `init { configure() }` generates and persists an OTP secret | NPE, 11 graph tests |
| `AuthFlowOut` | property init builds `AuthorizationService`, which inspects installed browsers | `ExceptionInInitializerError` |
| `FabricPrivacyImpl` | `init` set the Firebase flags | done: the work moved to `start()`, which `MainApp.onCreate()` calls - see below |

**Before adding `@ContributesBinding` to a class, read its property initializers and `init` block for
I/O, sockets, or anything blocking.** Usually the fix is `by lazy { ... }`, which is better in
production too, since building the graph should not do file I/O.

**Laziness is not always right.** Where the `init` work is a *startup obligation* - registering a
receiver, setting analytics flags, starting a periodic loop - deferring it changes behaviour. Those
need an explicit `start()` from `MainApp.onCreate()`, as `FabricPrivacyImpl.start()` has now.

**And an explicit `start()` has to be called by three shells, not one.** `MainApp.onCreate()` calls
several; `desktop/shell/.../Main.kt` and `ios/shell/.../IosAppStartup.kt` call almost none. A missed
call is silent - the class exists, the screens render, the work never happens. Prefer `by lazy` unless
the work really is an obligation, and when it is, add the call to all three at the same time.

**The clients make this worse than tests do.** `DesktopAppGraph` and `IosAppGraph` are built
**eagerly at startup, before any window exists**, so a constructor that opens a file or a socket fails
at launch there rather than in a test you can re-run. Anything reachable from `ClientGraphBindings`
gets this treatment on both platforms.

**Constructors must not write.** Two classes still do - `InsulinImpl` (`init { bootstrap() }` can
`putRemote`) and `ProfileRepositoryImpl`. Building a graph should never author a persisted write, and
on a paired client `putRemote` is on the sync path, so a graph build can publish a config change.

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
`XxxStringIds` map (androidMain). Add one line for the module to `StringOwnerModules.ALL` in
buildSrc, then call `registerKeyStrings("xxx")` at the top of the module's build file. The build
fails if the owner is not in the list. Then:

1. Nothing more in the build file. `registerKeyStrings` adds the generated code to `commonMain` and
   `androidMain` by itself. `TextRef` comes from `:core:keys`, which `:core:interfaces` already
   exports with `api`.
2. Nothing to register by hand. Every shell (`:app`, its instrumented tests, desktop, iOS) generates
   `GeneratedStringOwners` from `StringOwnerModules.ALL`. Add one of the module's strings to
   `DesktopStringOwnersTest` and `IosStringOwnersTest`. A module the desktop shell does not link, as
   a pump driver, gets `onDesktop = false` in the list, or the desktop registry imports an object it
   cannot see; then its string goes in the iOS test only.
3. Swap `R.string.foo` for `XxxStrings.foo`. The substitution is name-preserving, so a wrong mapping
   cannot happen silently - it fails to compile.
4. In Composables import `app.aaps.core.ui.compose.stringResource`. In androidMain it can sit next
   to the androidx one: both are called `stringResource`, and Kotlin picks by parameter type.
   commonMain has no androidx one.

Sweep **every** receiver spelling, not just the obvious one: `rh.gs(R.string.x)`,
`resourceHelper.gs(...)`, the fully qualified `app.aaps.plugins.foo.R.string.x`, and any aliased
`FooR.string.x`. Each of these has been missed once and found only by a failing test.

After the swap, unwrap `TextRef.AndroidRes(XxxStrings.x)` - the argument is already a `TextRef`.

**Tests need the same swap**: `whenever(rh.gs(R.string.x))` becomes `whenever(rh.gs(XxxStrings.x))`,
and a blanket `rh.gs(anyInt())` stub becomes `doAnswer { ... }.whenever(rh).gs(any<TextRef>())` -
written that way round because `rh.gs(any<TextRef>())` on its own is ambiguous against the vararg
overload. `stubTextRefResolution(rh)` in `shared/tests/TextRefStubs.kt` knows only five owners
(`keys`, `coreUi`, `interfaces`, `implementation`, `aps`). For any other owner, a string that no
test stubbed throws. Use `generatedTextResolver("xxx" to XxxStringsValues::textOf)` for the real
English text, or stub the string. Do not add the module to `TextRefStubs`: that needs a new
dependency. A Robolectric Compose test must call
`TextRefIdRegistry.register(owner) { XxxStringIds.idOf(it) }` in its setup. In the app,
`GeneratedStringOwners.registerAll()` does this for every owner.

### An `Int` in an interface is a hard stop

A resource id (`Int`) in an **interface** blocks every caller in commonMain. The fix is a `TextRef`
overload in the interface, as `PumpEnactResult.comment(TextRef)` and
`HardLimits.verifyHardLimits(..., valueName: TextRef, ...)` have now. Until such an overload exists,
the caller cannot move.

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
  what prove the new one emits the same bytes, and in commonTest they run on every target the module
  builds for rather than only on the JVM.
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

### `@IntKey` collides with the preference keys

Registering a plugin needs `dev.zacsweers.metro.IntKey`, and any class that also reads preferences
imports **`app.aaps.core.keys.IntKey`**. Kotlin then refuses both: `Conflicting import: imported name
'IntKey' is ambiguous`. The fix is an alias, which `LoopPlugin` uses:

```kotlin
import dev.zacsweers.metro.IntKey as MetroIntKey
...
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@MetroIntKey(310)
```

This is worth knowing because the clash has been misdiagnosed before: `SyncPluginsBindings` recorded a
**Dagger** error (`InjectProcessingStep`, `error.NonExistentClass`) as proof a plugin "cannot" be
annotated, and that false reason was then copied to another module. Metro has no annotation processor
and emits no such diagnostic. If a class seems to refuse `@ContributesIntoMap`, read the actual
compiler error before writing down a reason.

### Two Metro forms this version will not take

Neither is a blocker, but both look like the obvious idiom and both fail:

- **`@Binds` cannot live in an `object`** - "Extension property must have accessors or be abstract".
  Every binding container here is an `object`, so an alias `@Provides fun x(impl: XImpl): X = impl` is
  the right form, not a thing to convert.
- **`binding<@Qualifier Type>()` is rejected** - `Inapplicable candidate(s): constructor(scope:
  KClass<*>, binding: binding<*> = ...)`. So a **qualified** map entry still needs a stated
  `@Provides` in a container; only unqualified ones can move onto the class.

### Other common blockers

`javax.inject` (in a module from another fork: replace it with `dev.zacsweers.metro.*` in step 1 -
this repo has no javax.inject dependency), `@Synchronized`, `org.json`, `java.util.Calendar`, and `System.currentTimeMillis()` - the last is
just `Clock.System.now().toEpochMilliseconds()`.

Smaller ones: `Throwable.localizedMessage` is JVM only (use `message`); `@Synchronized` becomes
`AapsLock` + `withLock` from `:core:interfaces`; `@Volatile` needs `kotlin.concurrent.Volatile`;
`Dispatchers.IO` needs `import kotlinx.coroutines.IO`.

`java.io.Serializable` with `ObjectOutputStream` becomes `@Serializable` with `Json` (the `kotlinx-serialization`
plugin in the module). A plain class with `var`s in its body serializes as it is: properties with a backing field are
written, computed getters are not, and an enum from another module needs no annotation. A type from another module
that is part of the saved object needs `@Serializable` there too, with the plugin in that module.
`:pump:carelevo:emulator` saves its patch this way.

### RxJava to coroutines

RxJava has no Kotlin/Native artifact, so a module that uses it must move to coroutines first. The Carelevo driver
(`405beac0b1`, 48 files) found these traps:

- **An inner time limit is not a cancel.** `withTimeout` throws `TimeoutCancellationException`, a
  `CancellationException`. A `catch (e: CancellationException) { throw e }` around a call that has its own time limit
  passes a timed-out BLE write up as if the caller was cancelled. The queue then drops the command and AAPS never
  learns the result. Rx code with `runCatching` around `runBlocking` turned it into a normal failure. Write
  `catch (e: CancellationException) { currentCoroutineContext().ensureActive(); /* handle as a failure */ }`.
  Only an emulator scenario with a lost answer found this; every unit test passed.
- **`BehaviorSubject` is not a `StateFlow`.** A `StateFlow` drops a value equal to the last one and starts with a value.
  A faithful replacement is `MutableSharedFlow(replay = 1, extraBufferCapacity = 64, DROP_OLDEST)`, as
  `latestValueFlow()` in `:pump:carelevo`; `replayCache.isEmpty()` is the old `value == null`. Without the buffer a
  collector that is behind gets only the newest value, where `observeOn` queued them all.
- **Trampolined tests become unconfined ones.** Give each class that launches collectors an
  `internal var dispatcher` (default `aapsIoDispatcher`), and set `Dispatchers.Unconfined` in its test right after
  construction. View models use `viewModelScope`; their tests already replace Main.
- **A blocking call stays blocking.** A lambda that runs `runBlocking` inside cannot be stopped by an outer
  `withTimeout`. Only a suspend call is cut by it.
- **Look for Rx chains that never really ran.** A `subscribeOn(io)` chain added to a `CompositeDisposable` that is
  cleared on the next line is almost always disposed before it starts. Port what the code does, not what its comment
  says, and report it.
- **The compiler can show one error per run.** In this build the Kotlin compile of a test source set reported only
  the first error, also with `kotlin.incremental=false`. Convert by pattern, not by compile error.
- **Never sort imports with PowerShell `Sort-Object -Unique`.** It is case-insensitive and drops `flow.Flow` when
  `flow.flow` is there too.

### Pin what the code writes and shows before you move it

The safe order is: tests that pin the current output first, green on the old code, then the move,
then the same tests unchanged. Autotune was moved that way (`AutotuneGolden` in `:plugins:aps`
host tests: a missing golden file is written from the run and the test fails, so nothing is
accepted unseen). What those tests found is worth knowing before the next move:

- **The `org.json` of the JVM tests is not the phone's.** `com.vaadin.external.google:android-json`
  is Android's code, but it keeps object keys in a hash map, while Android keeps insertion order.
  A golden of `JSONObject.toString()` therefore pins a key order no phone writes. Compare JSON with
  the keys sorted and every value's text kept (`AutotuneGolden.json` does that, and checks the
  layout first).
- **`org.json` text is not kotlinx text.** A whole double is `8` there and `8.0` in kotlinx, `/` is
  escaped, a null value removes the key, and NaN throws at `put`. Where the text leaves the app (a
  file, an upload), write it with a renderer that copies `org.json` - `OrgJsonText` in
  `:plugins:aps` - and keep the old `try`/`catch` shape where a NaN used to cut an object short.
- **`String.format("%.2f")` does not round like `NumberFormat`.** The JVM formatter rounds the
  shortest decimal text (1.005 shows 1.01, 0.15 at one decimal shows 0.2); `DecimalFormat` HALF_UP
  rounds the double (1.00 and 0.1). `roundHalfUpAsText` in the Autotune view model keeps the old
  text and is checked against `String.format` over thousands of values.
- **`BigDecimal.valueOf(x).setScale(2, HALF_UP)` rounds the shortest decimal text too**, so a plain
  `round(x * 100)` turns 1.005 into 1.00 where it gave 1.01. The exact common replacement compares x with the double
  nearest each half-way decimal, `(2c + 1) / 200.0` (one correctly rounded division): `hundredthsHalfUp` in
  `:pump:carelevo:protocol`. For a dose, keep the old `BigDecimal` code in a JVM test as the reference and compare
  every hundredth, every thousandth, the doubles next to each half-way point and random values
  (`DoseEncodingEquivalenceTest`), with the hard values also in a common test that runs on iOS.
- **A test that changes the default time zone must call `SystemTimeZone.invalidate()`.** Code moved
  off `Calendar`/`TimeZone.getDefault()` reads the zone through `SystemTimeZone`, which keeps a read
  for 10 s, so the test would otherwise run in the zone of the test before it.
- **kotlinx can write exactly what Gson wrote.** Gson leaves a null field out, writes every other field
  (also one at its default), skips unknown keys, and with `serializeSpecialFloatingPointValues` keeps
  NaN. The same bytes come from `Json { explicitNulls = false; encodeDefaults = true;
  ignoreUnknownKeys = true; allowSpecialFloatingPointValues = true }` (`carelevoJson` in
  `:pump:carelevo`). Pin it through the DAOs with JSON that the Gson code wrote
  (`CarelevoStoredJsonGoldenTest`). Two reads differ, and both are better: a missing field takes the
  Kotlin default (Gson gave 0 or null, also in a non-null field), and an enum name the build does not
  know throws, where Gson gave null - give such an enum a serializer that falls back to an "unknown"
  entry, or one bad entry makes the whole stored list unreadable.
- **`java.time.LocalDateTime` maths is clock maths.** `plusDays`, `plusHours` and
  `ChronoUnit.MINUTES.between` ignore clock changes. In kotlinx-datetime keep that in the move by doing
  the sum on the date, or through `TimeZone.UTC`, not with a `TimeZone` that has summer time. Pin it
  with a test that crosses a clock change. Then ask whether clock maths was right at all: a device that
  counts its own time, as a patch counts its 7 days, needs real hours on an `Instant`. The Carelevo
  overview was wrong this way and was fixed after the move, as its own change.
- **Moving `rh.gs(R.string.x)` to `TextRef` makes missing stubs fail.** The shared `TextRefStubs`
  throw for a string no test stubbed, where the mock used to answer null. Expect to add stubs.

A golden file of the old output can show a bug in the old code. The move keeps it, because a move
must not change results: report it, and fix it as its own change with its own test.

### A port every shell implements: where the code goes

Autotune is the worked example. The plugin registers itself from commonMain, so the iOS and desktop
graphs get it too, and each shell must bind its port (`AutotuneFileStore`) or the graph does not
compile - delete the shell's placeholder for the interface (`IosAutotune`, `DesktopAutotune`) in the
same change, or Metro reports a duplicate binding.

- **The interface goes to `:core:interfaces`**, so `:implementation` can implement it without
  depending on the plugin's module.
- **Android and the desktop share one `java.*` implementation** in `:implementation`'s
  `jvmSharedMain`, an abstract class each platform extends only to say where (`JvmAutotuneFileStore`,
  with `AndroidAutotuneFileStore` and `DesktopAutotuneFileStore`). Bind each with
  `binding = binding<Port>()`: the direct supertype is the abstract class, not the port.
- **Reuse the folders import/export already defined**: `DesktopFolders` on the desktop, the
  `Documents` subfolders of `IosPrefsFileAccess` on iOS, so the user finds files where the others are.
- **iOS has no zip API.** `StoredZip` in `:implementation` commonMain writes a stored (uncompressed)
  zip, checked by `java.util.zip` in jvmTest.
- **Run it on the desktop for real** without the window: a throwaway test that sets `user.home` to a
  *copy* of `~/.aaps` (and clears `aaps.desktop.keyVault`, so the copied DPAPI keys open), calls
  `GeneratedStringOwners.registerAll()` and drives the plugin from `contributedPlugins`. Delete the
  test and the copy afterwards; the copy holds the user's keys.

`Provider<T>` is deprecated: the compiler says *"Using the desugared `Provider<T>` type is
discouraged. Prefer the function syntax form `() -> T`."* Write `() -> T` in new code. Call sites are
identical - `provider()` either way - so only the type and the import change.

### A pump driver: what moves now, and what stays until the platform can run a pump

`:pump:carelevo` was the first driver to flip. Its logic (BLE session, commands, coordinators, use cases, storage)
went to commonMain first; the plugin class, its view models, its screens, the Android notifications and the broadcast
receiver stayed in androidMain until the iOS app was ready to run a pump. Then they moved too - see "Moving the plugin
and its screens" below.

- **Find what can move by its dependencies, not file by file.** A file stays when it imports a platform API, or when it
  uses a file that stays. Compute that closure first; a handful of blocking calls usually hold back most of a module.
  In Carelevo they were `java.util.UUID`, `SystemClock.sleep`, `TimeUnit`, `BigInteger`, `String.format`,
  `synchronized`, `java.lang.Float.MIN_NORMAL` and `ResourceHelper` (use `TextResolver`, which is common).
- **Do not let the plugin or its view models register themselves from commonMain while iOS cannot run the pump.** A
  plugin registered from commonMain joins the iOS graph, and its view models join the iOS view model map, so iOS would
  need every pump binding. The classes can still move: register them per platform (below), so the driver is visibly
  absent on iOS rather than present and dead. Plain `@ContributesBinding`
  classes (the driver's storage) can move: Metro accepts them in the iOS graph while nothing there injects them.
  Compile `:ios:shell:compileKotlinIosArm64` to prove it, since that is where the graph is checked.
- **A blocking sleep is platform code.** `SystemClock.sleep` keeps sleeping through an interrupt; `runBlocking { delay }`
  throws on one. Use an `expect fun` with the platform call on each target, as `sleepBlocking` in `:pump:carelevo`.
- **A commonTest also runs in `testAndroidHostTest`, where Android calls do nothing.** `isReturnDefaultValues` makes
  `SystemClock.sleep` return at once, so a timing test of an Android `actual` cannot live in commonTest.

#### Moving the plugin and its screens

How the Carelevo plugin, its 6 view models and 15 screens went to commonMain while iOS still has no Carelevo:

- **Register per platform, not on the class.** Take `@ContributesIntoMap` off the plugin and off every view model, and
  list them as `@Provides @IntoMap` in one commonMain `@BindingContainer` that is *not* contributed
  (`CarelevoRegistration`: `@PumpDriver @MetroIntKey(1190)` for the plugin, `@ViewModelKey(X::class)` for each view
  model). Each platform switches it on with a contributed container that only includes it
  (`@ContributesTo(AppScope::class) @BindingContainer(includes = [CarelevoRegistration::class]) object ...`). A view
  model contributed from commonMain is enough to drag the whole driver into `IosAppGraph` - it failed on
  `PumpEmulators`, which iOS does not provide.
- **`PumpDriverBucketTest` reads driver keys from annotations.** It now also reads them from `@PumpDriver` providers in
  binding containers. A new driver registered this way needs nothing more.
- **Put what the platform does behind small ports in the driver**, each with the Android implementation in androidMain:
  foreground state (`CarelevoAppForeground`), the Bluetooth check before pairing (`CarelevoBleReadyCheck`, a
  `@Composable` member that wraps `BlePreCheckHost` on Android). The Bluetooth adapter state needs no port: read
  `BleCentral.adapterState` from the driver's own central, which also covers the emulated patch. Alarms and the phone
  notification need no port either: they go through `PumpAlerts` (`_docs/PUMP_DESIGN.md`, section 2).
- **What failed to compile, and the fix:** `ResourceHelper` → `TextResolver` (also in view models and the notifier);
  `AnnotatedString.fromHtml` → `htmlToAnnotatedString()` from `:core:ui` (knows only `<b>` and `<br>`); `LiveData` →
  `StateFlow`; `java.util.concurrent.atomic.AtomicBoolean` → `kotlin.concurrent.atomics.AtomicBoolean` with
  `@OptIn(ExperimentalAtomicApi::class)` (`set` is `store`); `BigDecimal.setScale` for display → `Round.roundTo`;
  `Dispatchers.IO` → `aapsIoDispatcher`; an Activity result launcher → an `expect @Composable` with a null `actual` on
  iOS and JVM.
- **Images:** `painterResource(R.drawable.x)` cannot be common. Move the files to `src/commonMain/composeResources/drawable`
  and add `libs.jetbrains.compose.components.resources` with a `compose.resources { packageOfResClass = ... }` block;
  each accessor (`Res.drawable.x`) needs its own import. Android vector XML with `aapt:attr` gradients renders.
- **The module also builds for `jvm()`**, so every `expect` needs a `jvmMain` actual, even though desktop links no pump.

### Lift the platform call out, keep the rule

When a class is blocked by one platform call, put that call behind an interface in commonMain and
implement it in androidMain, rather than leaving the whole class on Android. `PairedBtDevices` and
`LastKnownLocation` in `:plugins:automation` are the pattern: the trigger keeps its inputs,
serialization and matching logic in shared code, and only the Bluetooth or location call is
platform-specific. Implement the Android side straight away; other platforms can follow later.

A port must express *intent*, not steps. If the caller is coordinating platform timing on the
implementation's behalf, the port is drawn in the wrong place. `LocationServiceController` was
`startService(): Boolean` / `stopService()`, and `AutomationRuntime` wrapped it in a
`DeferredForegroundStart` (Android 12 blocks `startForegroundService` from the background) plus its
own latch to retry after a location permission grant - two Android rules living in the rule engine.
Collapsing it to one idempotent `setLocationUpdatesEnabled(enabled)`, with the deferral and the latch
inside the Android implementation, removed the last non-UI blocker from the class. The test for a
suspicious port: ask whether iOS would need the same dance. If not, it belongs on the other side.

A related tell is an event that carries a platform type it does not need. `EventLocationChange` held
an `android.location.Location` but only ever fed a debug log - the distance a trigger compares comes
from `LastKnownLocation.distanceTo`, set separately. Check what a payload actually decides before
assuming it has to stay.

Two cautions. Keep the platform maths on the platform where an exact result matters -
`LastKnownLocation.distanceTo` still calls `Location.distanceTo`, so no distance changes. And a port
whose implementation on some target would be a silent no-op is a safety problem in this app: a rule
the user relies on would quietly stop firing, so the feature should be visibly absent on that target
instead.

### Test libraries are JVM-only, so a fixtures module barely moves

JUnit 5, Mockito and RxJava have no Kotlin/Native artifacts. Anything built on them is Android by
nature, not by accident, and no amount of work moves it. In `:shared:tests` almost everything stays
in androidMain; only `AAPSLoggerTest` is in commonMain:

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

`ios/shell` must link every module that builds for iOS. Once a module does, `checkMigratedModules`
fails the framework link until the module is listed. It names the module and what to do: add it to
`migratedModules` in `ios/shell/build.gradle.kts`. Then update `ShellInfo.LINKED_MODULES`: nothing
checks the count, so set it to the number of entries in `migratedModules`. Expect this on every flip.

## Do not run a javax-stripping sweep over `:app`'s DI files

`AppRootGraph`, `MetroGraphs`, `AapsLeaves` and `CoreObjectsModule` legitimately import
`javax.inject.Singleton` and `javax.inject.Inject`. A helper that strips javax while adding an import
breaks them with `Unresolved reference 'Singleton'`. Edit those by hand.
