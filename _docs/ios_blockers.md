# iOS blockers

Things the iOS branch needs from the phone side. Written by the macOS session, for the Windows
session to pick up. Newest findings at the top of each list.

It now carries a little traffic the other way too - see **Ready for iOS**, written by the Windows
session for the macOS one. Those sections say what has landed in `commonMain` and what is left to
implement on the Apple side, so neither session has to re-derive it from the diff.

This file lives at `_docs/ios_blockers.md`. It arrived once as `_dcs/ios_blockers.md` and was moved -
please write it here, so both sessions look in the same place.

The pattern behind almost every entry: a class can only move to `commonMain` after it is off Android
types. The move is usually small once it is.

## How to find the next one

Do not guess. Ask Metro:

1. Add `val preferences: Preferences` (or whatever is wanted) to `IosProbeGraph`
   in `ios/shell/src/iosMain/kotlin/app/aaps/ios/shell/di/IosProbeGraph.kt`.
2. Run `./gradlew :ios:shell:compileKotlinIosSimulatorArm64`.
3. The `[Metro/MissingBinding]` error names exactly one missing binding. That is the next blocker.
4. Take the accessor out again before committing, so the graph keeps building.

## Where tests for common code go

**A test in `androidHostTest` does not run on iOS.** It is worth stating because the mistake is
invisible: the class is in `commonMain`, the tests are green, and the iOS target is never exercised.
`:implementation:iosSimulatorArm64Test` was `NO-SOURCE` for exactly this reason - the module had no
`commonTest` source set at all, so every test of every common class in it ran only on the JVM.

When moving a class to `commonMain`, move its tests to `commonTest` in the same module. Add the
source set if it is missing:

```kotlin
commonTest {
    dependencies {
        implementation(kotlin("test"))
        implementation(libs.kotlinx.coroutines.test)
    }
}
```

The cost is that Mockito is JVM only, so mocks become hand written fakes. That is usually an
improvement for this kind of test - a recording fake shows the *order* of calls, which is what most
of these assertions are really about.

Moving one test file across immediately found two faults the JVM run had hidden: a fake that did not
implement every interface member (Mockito had been filling it in), and a backticked test name
containing a comma, which Kotlin/Native rejects outright.

## Hint: most common code still has no iOS test coverage

Measured 2026-08-27, after `:implementation` got its `commonTest`. This is a survey, not a claim that
anything is broken - but the one module that was converted immediately turned up three real faults,
so it is worth working through.

| module | commonMain files | commonTest | test files that never run on iOS |
|---|---|---|---|
| `plugins/aps` | 23 | none | 33 |
| `core/objects` | 26 | none | 27 |
| `database/persistence` | 32 | none | 16 |
| `core/interfaces` | 255 | none | 13 |
| `plugins/sensitivity`, `smoothing`, `calibration` | 22 | none | 9 |
| `core/utils` | 6 | none | 5 |

Only `core/data`, `shared/impl` and `implementation` have a `commonTest` at all. Everything else
tests `commonMain` classes from `androidHostTest`, which runs on the JVM only - so code that ships to
iOS is verified only on Android. `plugins/aps` is the dosing algorithm and `database/persistence` is
what writes user data, so those two are worth the most.

What converting one module found, none of which the JVM could have shown: a fake that did not
implement every interface member because Mockito had been filling it in, a backticked test name
containing a comma, which Kotlin/Native rejects, and a segfault in `NSLog` that only appeared when
something called through the injected logger.

This is left for the kmp session because it touches many modules at once. Friction to expect: AAPS
tests lean on Mockito, which is JVM only, so a test that mocks has to be rewritten with hand written
fakes before it can move. Expect each module to split into "moves today" and "needs a rewrite first"
- converting the first group and recording the second is a good outcome, a clean sweep is not
realistic. See `implementation/src/commonTest/.../CommonNotificationManagerTest.kt` for the shape,
and the section above for the source set to add.

## Hint: fourteen classes in :implementation could move to commonMain today

> **Worked through 2026-08-29 by the kmp session. Three moved; the rest do not qualify.** The survey
> below was import-based, and an import list does not see two things: a symbol from *another*
> androidMain class, and JVM-only constructs that are not imports at all (`@Synchronized`,
> `@Volatile`, `String.format`, `R`). Corrected results:
>
> | class | actual state |
> |---|---|
> | `TemporaryBasalStorageImpl` | **moved** - `@Synchronized` -> `AapsLock` |
> | `DexcomTirImpl`, `DexcomTirCalculatorImpl` | **moved** - `Calendar` -> `kotlinx.datetime`, keeping the local hour, which picks the day/night threshold |
> | `ProtectionCheckImpl` | still blocked: also needs `R` and `@Volatile`. `AtomicLong` is converted, so only those remain |
> | `SceneActionsImpl` | needs `SceneExecutor`, which is WorkManager + `Context` |
> | `PasswordCheckImpl` | needs `CryptoUtil`, which is the whole `javax.crypto` stack. Now feasible - cryptography-kotlin is in the build for the client-control crypto - but it hashes stored passwords, so it needs golden vectors first, exactly like `ClientControlCrypto` did |
> | `DetailedBolusInfoStorageImpl` | uses **Gson**. The survey missed it because `com.google.*` was not in the grep |
> | `LoggerUtilsImpl` | logback / slf4j |
> | `IconsProviderImplementation` | Android `R` |
> | `InsulinImpl` | `ResourceHelper`, `ApplicationScope`, `@Synchronized`, `@Volatile` |
> | `CloudDirectoryManagerImpl` | needs `CloudStorageManager` |
> | `SceneAutomationApiImpl` | 21 errors, not surveyed in detail |
> | `AutosensDataObject` | `String.format` with twelve `%.02f` in `toString()`. `DecimalFormatter.to2Decimal` is in commonMain but is not injected here, so it is a constructor change for one debug string |
> | `ActiveSceneManager` | `org.json` -> kotlinx, but it is a **stored** format (scene records in preferences), so it wants the same care as the profile migration |
>
> Lesson worth keeping: to find what can move, compile for iOS. An import grep gives a candidate
> list, not an answer.

Surveyed 2026-08-29. These are in `implementation/src/androidMain` and import **nothing** from
`android.*` or `androidx.*`. They are Android only because nobody has moved them, not because of
anything they do. Nine need no other change at all:

| class | what still stands in the way |
|---|---|
| `SceneActionsImpl` | nothing |
| `SceneAutomationApiImpl` | nothing |
| `IconsProviderImplementation` | nothing |
| `InsulinImpl` | nothing |
| `PasswordCheckImpl` | nothing |
| `DetailedBolusInfoStorageImpl` | nothing |
| `TemporaryBasalStorageImpl` | nothing |
| `CloudDirectoryManagerImpl` | nothing |
| `LoggerUtilsImpl` | nothing |
| `DexcomTirCalculatorImpl` | nothing |
| `ProtectionCheckImpl` | `java.util.concurrent.atomic.AtomicLong` -> `kotlin.concurrent.atomics` |
| `AutosensDataObject` | `java.util.Locale` |
| `ActiveSceneManager` | `org.json.JSONObject` -> kotlinx, as `ProfileRepositoryImpl` did |
| `WizardBolusExecutorImpl` | still `javax.inject`; also `ConcurrentHashMap` and `AtomicLong` |

Left for the kmp session because it is a sweep across one module rather than iOS work. Worth doing
mostly because each one that moves is one less thing an iOS graph cannot build - `ProtectionCheck`
and `PasswordCheck` in particular sit under the settings screens.

Two warnings from having done several of these:

- `@Synchronized` does not exist in `commonMain`. Use `AapsLock`, as `PreferencesImpl` does.
- `Dispatchers.IO` reports itself as `internal` rather than missing. Use `aapsIoDispatcher` from
  `:core:interfaces`.

## Ready for iOS: the Nightscout client

Written by the Windows session, for the macOS one - the other direction from the rest of this file.

`plugins/sync/nsclientV3` is **60 files in commonMain, 17 on Android**. The plugin, the whole
client-control subsystem (receiver, round trip, both repositories, pairing offer fetch/publish,
preferences publisher, orphan detector), the incoming data processor, all sixteen NS extensions,
the nine load/upload bodies and all three screens now build for `iosArm64`.

What is still on Android is there because it has to be: the nine `Worker` shims, `NSClientV3Service`
(wake lock + `START_STICKY`), `SocketIoNsSocket`, `JsonBridge` (org.json), `StoreDataForDbImpl`,
`NSAlarmObject`, and the Android halves of the two ports below.

### Three interfaces need an iOS implementation

Nothing else blocks the NS client on iOS. All three are in
`plugins/sync/src/commonMain/.../nsclientV3/ws/`, each with a working Android implementation to read.

| interface | what it does | iOS side |
|---|---|---|
| `NsSocket` | one Nightscout websocket namespace. `on(event, listener)`, `connect`, `close`, `emitWithAck`, `emitAlarmAck`. Payloads cross as **JSON text**, so no socket.io types leak | a Ktor websocket client. `SocketIoNsSocket` shows the exact event names and the ack shape |
| `NsLoadExecutor` | runs the load round: `runChain(steps)`, `runReplacing`, `runDetached`, `cancel`, `isRunning`, `idle` | a coroutine sequence over `NsLoadStep`, calling the shared `XxxRunner` for each step. `CoroutineCalculationExecutor` in `:workflow` is the same shape |
| `NsConnection` | owns the connection's lifetime: `start`, `stop`, `connected`, `socketConnected`, `hasLiveSocket` | **needs a product decision first - see below** |

Two behaviours are part of the `NsConnection` contract and are easy to lose:

- **`start(reason)` is idempotent.** On Android both the service's own creation and the bind callback
  ask for it, and calling it on a live connection must not tear anything down.
- **`stop()` closes the sockets *before* releasing whatever carries them.** A quick restart otherwise
  races the teardown and finds the old sockets still attached. `ServiceNsConnectionTest` pins both,
  and mutating either one fails exactly one test.

### The decision that is blocking `NsConnection` on iOS

iOS will not hold a websocket open in the background, so the port cannot simply be implemented the
way Android does it. Three options, and this is a product call rather than a porting one:

1. **Foreground only** - connect when active, disconnect on background, and *show* that state. Honest
   and simple; a follower is stale while backgrounded.
2. **Foreground websocket plus REST polling when backgrounded** - reuses the `Load*Runner` chain,
   which is already shared, and `initialLoadFinished` already handles backfilling the offline window.
   More work, no silent gap.
3. **Push-driven** - needs Nightscout-side push. Out of scope today.

Option 1 then 2 is the suggested order. Whichever is chosen, the rule this codebase already follows
applies: an implementation that silently does nothing is worse than a feature that is visibly absent,
because a user relying on NS data would simply stop receiving it without being told.

### Wire formats that must not drift

Two things in this subsystem are exchanged with deployed AAPS instances, so they are contracts:

- **The client-control crypto** is now shared (`:core:nssdk`, cryptography-kotlin over
  JCA / CryptoKit / OpenSSL 3). `ClientControlCryptoVectorsTest` lives in `commonTest`, so it runs on
  every target - `mingwX64Test` already proves the Kotlin/Native path on Windows. Run
  `iosSimulatorArm64Test` on the Mac to cover CryptoKit; a failure there is a real incompatibility,
  not a stale test.
- **The pairing offer's Base64** (`kdfSaltB64`, `ivB64`, `wrappedB64`) moved from
  `android.util.Base64` with `NO_WRAP` to `kotlin.io.encoding.Base64.Default`. Both are RFC 4648 with
  padding and no line breaks, so they agree - but that is currently reasoned, not pinned by a vector.
  Worth adding one next to the crypto vectors.

## Gotchas in iOS interop

Collected so nobody pays for them twice. All were found by tests or a crash, not by review.

- **`NSLog("%@", someKotlinString)` segfaults.** `NSLog` is a C varargs function and `%@` wants an
  Obj-C object pointer; a Kotlin `String` is not boxed into an `NSString` on the way through. Pass
  `NSString.create(string = line)`. It compiles and links either way.
- **Obj-C class methods arrive as extensions and need their own import.**
  `NSFileHandle.fileHandleForWritingAtPath` is an extension on `NSFileHandleMeta`, so
  `import platform.Foundation.fileHandleForWritingAtPath` is required. Same for `closeFile`. When a
  symbol looks like it should exist, dump the metadata rather than guessing:
  `~/.konan/<dist>/bin/klib dump-metadata <klib path>`.
- **Enum constants are not top level.** `UNNotificationInterruptionLevelTimeSensitive` is an entry on
  `UNNotificationInterruptionLevel`, not a standalone `val`.
- **A backticked test name cannot contain a comma** on Kotlin/Native.
- **Applying the Compose plugin without a multiplatform Compose runtime breaks the iOS build.**
  `androidx.compose.*` artifacts are Android only, so a module with iOS targets needs
  `libs.cmp.runtime` (and `cmp.foundation` / `cmp.ui` / `cmp.material3` if it draws) on a source set
  the iOS compilation can see. Hit twice now: `ios/shell`, and `:plugins:automation`, which could not compile for iOS
  at all until `cmp.*` reached its `commonMain`. The failure is
  `IncompatibleComposeRuntimeVersionException` at compile time, or an `IrLinkageError` at run time if
  only the declarations resolved.

## Open

### Request: StoreDataForDb blocks the whole NS client on iOS

`plugins/sync/src/androidMain/.../nsclientV3/StoreDataForDbImpl.kt` - 543 lines, **one** Android
import.

This is the one to do first, ahead of the service itself. Every incoming websocket event ends in
`storeDataForDb.requestStoreX(...)` - glucose values, treatments, food, calibrations, device status.
An iOS `NsConnection` written today would connect, subscribe, receive and parse, and then drop every
record, because there is nothing to hand them to. That is the silent no-op the migration rules warn
about, and on the data path.

Two smaller ones go with it, both reached from the same handlers:

| class | lines | what is in the way |
|---|---|---|
| `StoreDataForDbImpl` | 543 | 1 android import, 3 jvm |
| `NSAlarmObject` | 48 | 1 jvm import |
| `JsonBridge` | 32 | `org.json`, by definition - it is the bridge |

`JsonBridge` may simply not need an iOS counterpart: the iOS handlers will parse with
kotlinx.serialization directly, the way `ProfileRepositoryImpl` was converted, so the bridge is only
needed while Android still speaks `org.json`.

### Then: the socket wiring in NSClientV3Service

`plugins/sync/src/androidMain/.../services/NSClientV3Service.kt` - 494 lines, 6 Android imports
(`Intent`, `Binder`, `IBinder`, `PowerManager`, two annotations).

The iOS side will write its **own** `NsConnection` rather than wait for this to be lifted - a
separate implementation is the point of the port, and the two platforms genuinely differ here. What
it needs from you is only the three classes above; the wiring itself will be rewritten on the iOS
side with kotlinx.serialization instead of `org.json`.

For reference while that is written, these are the parts of the contract that are easy to get wrong,
and both are already pinned by `ServiceNsConnectionTest`:

- `start(reason)` is idempotent - calling it on a live connection must not tear anything down.
- `stop()` closes the sockets **before** releasing whatever carries them, or a quick restart races
  the teardown.

One more, from reading the handlers: `onDataCreateUpdate` must not advance
`lastLoadedSrvModified` until `initialLoadFinished` is true, or the next load chain skips exactly the
offline window it is supposed to backfill. That one is a comment in the Android code rather than a
test, and it would be easy to lose in a rewrite.

## Ready for Android: what the iOS side has built

- **`NsLoadExecutor` is done** - `CoroutineNsLoadExecutor` in `plugins/sync/iosMain`. The nine steps
  run as a coroutine sequence under one `Job`, so a new round replaces one in flight. Two details
  worth keeping if it is ever moved to `commonMain`: the chain stops at the first step that does not
  succeed, as the WorkManager chain does, and `idle` is emitted from `invokeOnCompletion` so a
  *cancelled* round reports idle too - otherwise the plugin's follow-up queue sticks.
- **`NsSocket`/`NsSocketFactory` are done, in Swift.** `ios/app/Shared/NsSocketBridge.swift` on
  `socket.io-client-swift`, the same project's official client as the `socket.io-client-java` used on
  Android, so both platforms speak to Nightscout the same way. It is added through Swift Package
  Manager - the first external dependency in the iOS app - and the Kotlin side never mentions
  socket.io, because `NsSocket` was already exported as an Obj-C protocol for Swift to conform to.
- **`IosForegroundWatcher`** drives start/stop from the app lifecycle, closing the socket inside a
  background task assertion so it is not cut mid-frame.


Nothing right now.

## Known gaps on the iOS side

Not blockers, and not for the Windows session to fix. Listed so nobody is surprised by them.

- `IosSystemNotificationPlatform.setAudibleAlarm` only logs, so **an urgent alarm makes no sound on
  iOS today**. There are two separate paths and they are easy to confuse:
  - *While the app is alive* - an `AVAudioPlayer` on an `AVAudioSession` with category `.playback`,
    which ignores the hardware mute switch. **No entitlement needed.** This is the counterpart of
    `AlarmSoundPlayerImpl`, and it is the missing piece: writing an iOS `AlarmSoundPlayer` and
    driving it from `setAudibleAlarm` would make alarms work whenever AAPS is running. The four
    sounds live in `core/ui/res/raw` as Android resources, so they would first have to reach the iOS
    bundle.
  - *While the app is not running* - only a Critical Alerts entitlement lets a notification break
    through silent and Focus. Apple grants it to medical apps on application. This is a project
    decision, not code.

  Earlier notes here said the entitlement was the only route. That was wrong: it is the only route
  for a notification-delivered alarm, not for one the app plays itself.
- `IosSystemNotificationPlatform.onDismissed` **is** wired now. Two things were needed and either
  one missing makes it silently never fire: a delegate on the shared centre, held in a property
  because that slot is weak, and a `UNNotificationCategory` carrying `customDismissAction`, without
  which iOS reports taps but not dismissals. The one caveat left is that it calls
  `setNotificationCategories` with only its own category, so it would clobber categories registered
  elsewhere - nothing else registers any today.
- **The pairing PIN is not protected from screenshots on iOS.** `blockScreenshotsWhileVisible()`
  (`plugins/sync/.../clientcontrol/compose/ScreenshotBlocking.kt`) is an `expect` that returns
  whether the platform really blocked capture. Android applies `FLAG_SECURE` and returns true; the
  iOS `actual` returns **false**, deliberately, because Apple has no equivalent and a silent no-op
  would imply a protection that is not there. That PIN wraps the shared secret a paired client signs
  commands with, so a screenshot sitting in a gallery or a cloud backup is a real exposure. Two
  things are worth doing on the iOS side: cover the window on `willResignActive` so the app-switcher
  snapshot does not hold the PIN, and warn on the dialog while the value is false. The screen already
  has it (`screenshotsBlocked` in `AuthorizedClientsScreen`); it just has nowhere to show it yet, and
  choosing that wording is a product decision rather than a porting one.

## Done

- **The notification cluster - done.** There is one registry now.
  `AndroidSystemNotificationPlatform` (`implementation/src/androidMain/.../notifications/`) holds the
  Android half - channel, dismiss `BroadcastReceiver`, `NotificationCompat`, `PendingIntent` - and
  makes the three way decision the old class made inline: URGENT **with a sound** is posted silently,
  because `AlarmSoundPlayer` owns the ramping audio; anything else is shown only when
  `AlertUrgentAsAndroidNotification` is set **and** there are no actions; otherwise nothing.
  `NotificationManagerImpl` is deleted and `CommonNotificationManager` is bound on both platforms.
  Notes for whoever reads this next:
  - the `show(notification, title)` shape is what made it possible. A signature naming a subset of
    the fields could not express the three way decision, which is why this sat open.
  - it is a `@Provides` in `ImplementationBindings`, not annotations on the class: building the
    registry registers the receiver and creates the channel, and that must not happen while the
    graph is being assembled.
  - `AlarmNotificationManager`, `AlarmSoundPlayer` and `NotificationHolder` are injected as
    `Provider`s for the same reason. `AlarmNotificationManager` calls `createChannels()` in its own
    constructor, so injecting it directly makes the plain-JVM graph tests fail on `getSystemService`.
  - the channel is created on the first `show()` rather than at start up, so its entry in the system
    notification settings appears only after the first notification.
  - `AlarmSoundPlayerImpl` and `NotificationHolderImpl` are on Metro now. `AlarmNotificationManager`
    is the last one still on javax.
  - 9 Robolectric tests pin the gating in `AndroidSystemNotificationPlatformTest`. The paths that
    need a real alarm are not covered on device yet.

- `AutotunePlugin` - off the `org.json` adapters. `ATProfile.basal()/ic()/isf()` return kotlinx
  `JsonArray` directly now; they were already built with `jsonArrayOf` and only wrapped in a
  `JSONArray` through a `toString()` round trip, so the three private `jsonArray(...)` wrappers went
  with them. **`BlockJsonAdapters.kt` still exists**: all five of its functions now have zero
  production callers, but six tests still use them - `BlockRenderTest` and
  `ProfileJsonCharacterizationTest` look like deliberate org.json-vs-kotlinx parity checks, so
  deleting the file means deciding what those tests are for. Left for whoever knows.
- `ProfileStoreObject` - moved to `commonMain` (`220357539a`). `Preferences` now resolves on iOS -
  verified with the probe procedure above. Notes for whoever reads this next:
  - the map became `mutableMapOf` rather than `LinkedHashMap` by name; that is the same class
  - there were no `R.string` uses, so no `CoreUiStrings` swap was needed - only `ResourceHelper` ->
    `TextResolver`, which `validateSemantic` already took
  - two unused constructor params went with it (`config`, `notificationManager`)
  - `@Synchronized` -> `AapsLock`, and `getSpecificProfile` was rewritten because a `var` mutated in
    a closure could not smart cast
  - the kotlinx half of `JsonHelper` had to move to `commonMain` first, as `JsonHelperKtx.kt` - a
    Kotlin `object` cannot span source sets and the `org.json` half has to stay on Android
  - `getDefaultProfileJson()` was dead (test-only) and was removed from the `ProfileStore` interface
- The five plugins that this file used to list as blocked - `SensitivityOref1Plugin`,
  `SensitivityAAPSPlugin`, `SensitivityWeightedAveragePlugin`, `UnscentedKalmanFilterPlugin` and
  `LinearCalibrationPlugin` - are built by `IosProbeGraph` and verified running on the simulator
  (`59fe8102c3`). Nothing was left blocked by a missing common implementation.
- `AAPSLoggerIos` (`59fe8102c3`) - `NSLog` plus a size rotating file in Documents, replacing the
  `ProbeLogger` fake. Only `ProbeTextResolver` is still a stand-in.
- `AapsTheme` renders on iOS (`048cebb42f`) - the AAPS colour scheme and typography, resolved through
  a real `Preferences` on `NSUserDefaults`, not a bare `MaterialTheme`.
- `BlockJsonAdapters.kt` is **deleted**. `AutotunePlugin` was the last production caller
  (`47a7a8984d`), so `:core:objects` no longer holds `org.json` for schedules at all. Three test
  files still build `org.json` fixtures, which is right - that is the shape a stored or downloaded
  profile arrives in - so each now has a small private bridge to the kotlinx readers instead of a
  shared production one. `BlockRenderTest`'s `the org json adapter matches the kotlinx renderer` went
  with the adapter it was guarding.
- The three automation platform interfaces have iOS implementations. `LastKnownLocation` is real -
  Core Location, with the distance left to `CLLocation.distanceFromLocation` so the geodesic maths
  stays on the platform, as the migration rules ask. `PairedBtDevices` and `BtConnectionSource`
  return empty and log why: iOS cannot read the phone's paired devices, and cannot see Bluetooth
  connections made by anything other than this app. **A Bluetooth automation trigger can be
  configured on iOS and will never fire** - decided deliberately, and written in both KDocs so it is
  not mistaken for an oversight later.
- `ConstraintsCheckerImpl` - moved to `commonMain` (`a76cca9e41`)
- `ProfileRepositoryImpl` - moved to `commonMain`, off `org.json` (`3252f044b1`)
- `PluginStore` / `PluginPermissions` - split so the registry is no longer Android
- `DateUtilImpl`, `PreferencesImpl`, `ProfileUtilImpl` - moved to `commonMain`
