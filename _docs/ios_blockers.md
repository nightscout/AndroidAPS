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

Measured 2026-08-27 and refreshed 2026-08-31. This is a survey, not a claim that
anything is broken - but the one module that was converted immediately turned up three real faults,
so it is worth working through.

| module | commonMain files | commonTest | test files that never run on iOS |
|---|---|---|---|
| `plugins/aps` | 32 | 2 files | 33 |
| `core/objects` | 26 | none | 27 |
| `database/persistence` | 32 | none | 16 |
| `core/interfaces` | 255 | none | 13 |
| `plugins/sensitivity`, `smoothing`, `calibration` | 22 | none | 9 |
| `core/utils` | 6 | none | 5 |

Only `core/data`, `shared/impl`, `implementation` and now `plugins/aps` have a `commonTest` at all.
Everything else tests `commonMain` classes from `androidHostTest`, which runs on the JVM only - so
code that ships to iOS is verified only on Android. `plugins/aps` is the dosing algorithm and
`database/persistence` is what writes user data, so those two are worth the most.

`plugins/aps` got its first two `commonTest` files with `PumpEnactResultExtensionTest` (19 tests),
which moved there when the extension became kotlinx and multiplatform. The other 33 test files in
that module still run on the JVM only - `LoopPluginTest` among them, which is now testing a
`commonMain` class.

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

### Six bindings stand between the NS client and iOS

Re-measured with the probe procedure above, against the current tree. The earlier list of twelve was
out of date in both directions - some entries had been cleared, and two of the claims in it were
wrong. What is actually left:

| Missing binding | Whose | Note |
|---|---|---|
| `Autotune` | yours | 3 methods, but the Android class is portable Kotlin computation, not platform. Worth porting rather than stubbing - a no-op would quietly do nothing when a user runs it. |
| `ExportPasswordDataStore` | yours | see the request section above |
| `ImportExportPrefs` | yours | 37 methods, document picker on both sides |
| `IobCobCalculator` | yours | still needs a home outside `:app` |
| `UiInteraction` | shared | the interface is in commonMain already; only `UiInteractionImpl` is in `:app`. The iOS side is ours and is being written - see below. |
| `NsSocketFactory` | ours | wiring only, see the gaps section |

**Correction to what this section used to say.** It listed `BolusWizard`, `QuickWizard`,
`RunningModeGuard`, `L` and `BolusProgressData` as having "no Kotlin implementation anywhere, and
worth questioning rather than porting", and suggested the NS client was dragging in the loop. That
was wrong on both counts and should not be acted on:

- `BolusWizard`, `QuickWizard` and `RunningModeGuard` were never missing. They are built by
  `CoreObjectsGraph`, which is a `@BindingContainer`, so its `@Provides` are invisible to a graph
  that does not include it. The probe did not, and they looked absent. Including it cleared all
  three at once.
- `L` and `BolusProgressData` needed no new code either, only a `@Provides` each. `LImpl` is already
  in `shared/impl` commonMain, and `BolusProgressData` is a commonMain class that carries no
  annotations on purpose. Both are stated in `IosProbeGraph` now.

Only `IobCobCalculator` from that group is real, and it is a packaging problem rather than a missing
implementation.

`Automation` is separately blocked, by five of the six above: `Autotune`, `ExportPasswordDataStore`,
`ImportExportPrefs`, `IobCobCalculator` and `UiInteraction`. Its own three former blockers -
`LocationServiceController`, `LocationPermissions`, `ReminderScheduler` - are all cleared.

### Three dependency cycles, and why Android never sees them

Worth knowing, because each one is a place where Android's platform machinery is quietly acting as
an injection boundary and iOS has nothing in that role. All three are fixed on the iOS side with a
`Provider`, the same tool `TriggerFactory` already uses in commonMain.

1. `IosLoopNotifier` → `Loop` → `LoopPlugin` → `LoopNotifier`. `AndroidLoopNotifier` never names
   `Loop`: it reaches the loop through a broadcast receiver and an intent, so the edge does not exist
   on that side.
2. `CoroutineNsLoadExecutor` → the nine load runners → `NSClientV3Plugin` → `NsLoadExecutor`. On
   Android the runners are WorkManager workers, built by WorkManager rather than by the graph, so
   they are not graph nodes at all.
3. `IosNsConnection` → `NsIncomingDataProcessor` → `NsClient` → `NsConnection`. `ServiceNsConnection`
   avoids it by holding no such dependency: the socket work lives in `NSClientV3Service`, an Android
   service the system constructs.

The pattern is the same each time, and it is worth expecting more of them as further plugins reach
iOS. Anywhere Android hands construction to the framework - a `Service`, a `BroadcastReceiver`, a
`Worker` - the cycle is real in the object graph and only hidden by who does the building.

### `BtConnectionSource` no longer needs an iOS class

`IosBtConnectionSource` is deleted. Once `AutomationRuntime` moved to commonMain it began
contributing `BtConnectionSource` itself, which made two bindings on iOS and failed the graph. The
shared one already gives the right answer there: its buffer is filled from `EventBTChange` on the
bus, and nothing posts that event on iOS, so the list stays empty without a second class to keep in
step. The behaviour is unchanged and still documented in the gaps section.

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

## Two iOS behaviours a user would notice

Both are implemented and both work as designed. They are here because the design has a cost that is
invisible from the code, and someone should decide whether to accept it before iOS ships.

### A roaming user may be charged for data

`IosReceiverStatusStore.roaming` is **always false**, because iOS exposes no roaming state at all -
not through `CTTelephonyNetworkInfo`, not anywhere public.

That would be harmless if nothing read it, but `ReceiverDelegate` does:

```kotlin
ev.mobileConnected && preferences.get(BooleanKey.NsClientUseCellular) && !ev.roaming || ...
```

So on iOS the first branch always matches: **a user who turned off "sync while roaming" still syncs
over cellular abroad.** False is the least bad of two wrong answers - reporting true instead would
stop cellular sync working for everyone, everywhere.

If that is not acceptable, the options are a preference the user sets by hand when travelling, or
suppressing the cellular-sync option on iOS entirely. Both are product decisions.

### A timed scene activated on iOS never ends

`IosSceneExpiryScheduler` deliberately does not schedule, and logs at error when asked to. Scenes
compile and the editor works, which is what was wanted for now, but activating a **timed** scene on
iOS has a real consequence.

`SceneExpiryRunner` is not a UI refresh. At expiry it reverts the two actions whose effect does not
end on its own:

- the **SMB toggle**, a preference with no duration model
- the **profile switch**, whose `EffectiveProfileSwitch` outlives the timed record that created it -
  `getEffectiveProfileSwitchActiveAt()` picks the newest EPS and ignores `originalEnd`, so the base
  profile only resumes once a new base-profile EPS exists

Without the callback both stay applied indefinitely, and a chained follow-up scene never starts.
Temp target, loop mode and care portal entries are safe - those self-expire from their own
timestamps.

**So activation of a timed scene has to be gated in the UI before scenes ship on iOS.**

A real implementation is possible later, but not as a plain timer - the interface is right to forbid
that. It needs three parts together: an in-process timer (works whenever the app is alive, which for
a looper holding a BLE connection is most of the time), a `UNTimeIntervalNotificationTrigger` at the
deadline (fires even if the app was killed, but only shows a notification - it cannot run code), and
an overdue sweep when the app next comes to the foreground so the runner executes late rather than
never. That ends the scene on time when possible and always tells the user otherwise, which is a
weaker promise than the Android one and should be agreed before it is built.

## Known gaps on the iOS side

Not blockers, and not for the Windows session to fix. Listed so nobody is surprised by them.

- `IosSecureEncrypt` keeps its AES key in the Keychain, marked `ThisDeviceOnly`, but **not in the
  Secure Enclave** - the Enclave holds EC keys, not the AES key wanted here, so the key is protected
  by the Keychain and device encryption rather than being non-exportable like the Android TEE key.
  Wrapping the AES key with an Enclave EC key would close that gap and is a larger change.
- `IosReceiverStatusStore.ssid` is always empty: reading it needs the Access WiFi Information
  entitlement plus location permission. Until that is arranged, a Wi-Fi SSID automation trigger can
  be configured on iOS and will never match - the same shape as the Bluetooth trigger.
- `NsSocketFactory` can never be bound inside the Kotlin graph. The implementation is
  `SwiftNsSocketFactory`, on the official `socket.io-client-swift` - the same project's client as the
  `socket.io-client-java` Android uses, which is what keeps both platforms speaking to Nightscout
  identically. The graph has to take it as a factory parameter from the app at start up.

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

- **The word "exported" is missing from the export date on iOS.** `IosPrefsFileInfo.formatExportedAgo`
  returns "3 days ago" where Android says "exported 3 days ago". The relative wording itself comes
  from `NSRelativeDateTimeFormatter`, which the system localizes on its own, but the surrounding word
  is one of AAPS's own strings (`exported_ago`, `exported_at`, `exported_less_than_hour_ago`) and iOS
  still has no reader for those. The label sits next to a calendar icon on a row about an export, so
  it reads, but it should be wrapped once app strings can be read on iOS. Both platforms switch from
  relative wording to a plain date at the same age - 60 days - so only the prefix differs.
- **`IosLocationPermissions` returns an empty list on purpose.** `PermissionGroup.permissions` holds
  Android `Manifest.permission` strings and iOS has no equivalent: location is asked for at the point
  of use by `CLLocationManager`. `AutomationRuntime` therefore reports nothing missing on iOS, which
  is correct rather than unfinished. The permission is still requested, by
  `IosLocationServiceController`.


## Request: `ExportPasswordDataStore` needs the same treatment the other big ones got

`ExportPasswordDataStoreImpl` (`implementation/src/androidMain/.../protection/`) is the last small
looking blocker that is not small. The interface is four methods, but the implementation is ~300
lines and almost none of what it does is Android:

- the validity window and the grace period (`isInValidityWindow`, the half window rule),
- resetting the password once the window has passed,
- the master password cross check - decrypt the stored secret through `SecureEncrypt` and compare it
  with `StringKey.ProtectionMasterPassword` through `CryptoUtil`, clearing the store when it no
  longer matches.

Only the storage is Android: Jetpack DataStore, plus `KEYSTORE_ALIAS`. Writing an iOS copy would
duplicate every rule above, which is exactly the failure mode the notification registry avoided.

The ask is the shape that already worked twice: move the logic to `commonMain` behind a small
storage port - something like `fun read(): Pair<String, Long>?` / `fun write(password: String,
timestamp: Long)` / `fun clear()` - and leave the platform side to supply it. `SecureEncrypt`,
`CryptoUtil` and `Preferences` all resolve on iOS already, so nothing else stands in the way.

The iOS half is then a few lines and already has its parts: `Keychain`
(`implementation/src/iosMain/.../protection/Keychain.kt`) is an interface with `AppleKeychain`
behind it, written for `IosSecureEncrypt` and directly reusable here.

`ImportExportPrefs` is a separate matter - 37 methods, document picker work on both sides - and is
not being asked for here.


## DONE: `PasswordCheckImpl` moved to commonMain by swapping one dependency

`AapsAppRoot` landing in `appshell/commonMain` is the reason this is worth doing now. Measured with
the probe against that function's parameters, five of its six injected dependencies already resolve
on iOS - `DecimalFormatter`, `ProfileUtil`, `PasswordHasher`, `VisibilityContext` and
`ClientControlActionDispatcher`. `PasswordCheck` is the only one that does not, and it is a new
entry that was not on the blocker list before.

It does not need an iOS implementation. `PasswordCheckImpl`
(`implementation/src/androidMain/.../protection/`) has **no Android imports at all** - 162 lines
over `Preferences`, `CryptoUtil`, `RxBus` and `TextResolver`. It is in androidMain only because
`CryptoUtil` is, and it uses exactly two things from it:

```
line  74:  cryptoUtil.checkPassword(enteredPassword, password)
line 110:  cryptoUtil.hashPassword(enteredPassword)
```

Both are `PasswordHasher`, which you extracted for the setup wizard and which now has an
implementation on both platforms - `CryptoUtil` on Android, `IosPasswordHasher` on iOS. Swapping the
constructor parameter from `CryptoUtil` to `PasswordHasher` should let the whole class move to
commonMain untouched otherwise, and iOS then gets it for free.

That is one shared class instead of two, which is the better outcome than an iOS copy: the dialog
rules here decide when a user is asked for a master password, and two implementations would be two
places for that to drift.

**Done, exactly as described.** `PasswordCheckImpl` now takes `PasswordHasher` instead of
`CryptoUtil` and lives in `implementation/src/commonMain/.../protection/`. It compiles for Android,
iOS and the JVM desktop target from the one source, unchanged apart from that parameter. The
`IosPasswordCheck` placeholder in `ios/shell/.../missing/` is deleted, and
`:ios:shell:compileKotlinIosArm64` is green without it, so the binding really is satisfied by the
shared class.

### What `AapsAppRoot` still needs beyond that

`ClientControlActionDispatcher` resolves, but pulling it in drags every remaining blocker with it -
`Autotune`, `ExportPasswordDataStore`, `ImportExportPrefs`, `IobCobCalculator`, `NsSocketFactory`
and `UiInteraction` - because it reaches the loop and the NS client. So hosting the real Compose
root on iOS is gated on that whole set, not on any single one.

`ExportPasswordDataStore` is worth pulling forward in that list: `AapsAppRoot` takes it directly, so
it is now on the path to running the real UI on iOS rather than only affecting the export screen.
The request for it is in the section above.

## Fixed on the iOS side: `is24HourClock` answered wrongly in Traditional Chinese

Small change to `core/ui/src/iosMain/.../PlatformTheme.ios.kt`, flagged here because it is your file.

The actual read the AM/PM letter - `dateFormat?.contains("a") != true` - which is the usual advice
and is wrong for at least one locale. `zh_TW` asks for the short time pattern `Bh:mm`: `B` is the
flexible day period (上午 / 下午) and `h` is a 12 hour hour. There is no `a` in it, so the check
answered "24 hour" and a Traditional Chinese user with the "24-Hour Time" switch **off** would have
been given a 24 hour picker on the profile activation screen.

It now reads the hour field instead, which the Unicode standard fixes rather than the locale: `h`
and `K` count to twelve, `H` and `k` count to twenty four. The parser is `usesTwelveHourClock` in
`core/ui/src/iosMain/.../ClockPattern.kt`, kept apart from the composable so it can be tested, and
it skips quoted literals so the `'h'` in a pattern like `HH'h'mm` is not mistaken for a field.

Found by running the real formatter over 40 locales and comparing the two readings, not by
inspection - `zh_TW` was the only disagreement, and nothing but running it would have shown that.
`ClockPatternTest` keeps both the parser cases and the live `zh_TW` formatter check.

Worth knowing if Android does the same thing anywhere: `DateFormat.is24HourFormat(context)` is a
direct flag and has no such problem, so this is an iOS only trap.


## `PasswordHasher` and `PluginPermissions` - iOS side done

Both ports landed and both have an iOS implementation with tests.

**`IosPasswordHasher`** (`implementation/src/iosMain/.../protection/`) copies `CryptoUtil` byte for
byte, because the hash is stored and travels between platforms in an export. One detail is worth
repeating since it is easy to "correct" into a bug: the HMAC key is the **UTF-8 text of the salt's
hex**, not the 32 raw salt bytes. Using the raw bytes is the reading that looks more correct and
would reject every password ever set on Android.

The tests check three reference hashes produced independently with Python's `hmac` module rather
than by running this code, so they would catch the salt and the message being swapped - a mistake
that a round trip test cannot see, because it is symmetric.

**`IosPluginPermissions`** (`implementation/src/iosMain/.../plugin/`) returns empty from both
methods, which is what `PluginPermissionsImpl`'s own docs already predicted. It asks the plugins
first and logs an error if any of them declares a group, so the emptiness stays a checked fact
rather than an assumption: today every source is empty on iOS - `bluetoothPermissionGroup()` is
null, `IosLocationPermissions` returns nothing, and the rest are androidMain.

### One gap this opened: `PrefsFileInfo.listPreferenceFiles` is empty on iOS

Finding the files would be easy. Building a `PrefsFile` is not: it carries parsed metadata, and both
`EncryptedPrefsFormat` and `PrefsMetadataKeyImpl` are androidMain. Returning files without metadata
would be worse than none, since the import screen sorts and filters on the flavour key and would
drop every row after showing it. There is also nothing to list yet - `ImportExportPrefs` has no iOS
implementation, so iOS cannot write an export either. This clears up on its own if that ever moves.

## Fixed on the iOS side: a non-cryptographic random was generating an AES key

Ours, not yours, but worth knowing if similar code appears on your side. `IosSecureEncrypt` used
`kotlin.random.Random` for both the AES-256 key and the GCM IV. That is a plain PRNG seeded from the
clock, not a CSPRNG, so both were predictable to anyone who could guess roughly when they were made
- which defeats the point of encrypting the secret at all. Now `CryptographyRandom` from
cryptography-kotlin, which is backed by the platform CSPRNG and is a drop-in for the same calls.

Checked the rest of the tree while there: the only other `kotlin.random.Random` uses are a virtual
pump serial number and a date helper, neither of which is security.


## Before anyone writes `IosUiInteraction`: the alarm owner tag is a trap

Not a live bug - nothing on iOS plays with `OWNER_FULLSCREEN` today - but the next person to write
`UiInteraction.runAlarm` for iOS will walk into it, so it is written down before that happens.

`AlarmSoundPlayer` records who started a sound. Two owners exist: `OWNER_INTERNAL`, used by the
notification registry through `setAudibleAlarm`, and `OWNER_FULLSCREEN`, used by Android's
`AlarmNotificationManager` for the full screen alarm that `runAlarm` posts.

`stopAlarm` is `notificationManager.muteAllAlarms()` on both platforms, and that ends in
`refreshAlarmSound()` plus `platform.cancelAll()`. The difference is what `cancelAll` does:

- `AndroidSystemNotificationPlatform.cancelAll()` calls `alarmNotificationManager().cancelAlarm()`,
  which stops the **`OWNER_FULLSCREEN`** audio and cancels the notification.
- `IosSystemNotificationPlatform.cancelAll()` only removes pending and delivered notifications.
  Nothing on the iOS side stops `OWNER_FULLSCREEN`, because nothing starts it.

So an iOS `runAlarm` that plays with `OWNER_FULLSCREEN` would produce a **ramping alarm that
`stopAlarm` cannot silence**. In a medical app that is the worse of the two failure directions, and
it would not show up in a build or in any test that does not actually let the sound run.

Two ways out, and the choice is a design decision rather than a porting one:

1. play with `OWNER_INTERNAL` and let the registry own the sound, accepting that
   `setAudibleAlarm`'s `soundingKey` bookkeeping may silence it when the notification list changes;
2. give iOS its own counterpart of `cancelAlarm()` so `cancelAll()` stops the full screen owner too,
   which is the shape Android already has.

Related, and part of the same decision: `runAlarm` takes a `title`, and the shared registry does not
carry one - `CommonNotificationManager` derives the title from the level ("Urgent alarm" / "Info").
Posting an alarm through the registry therefore loses the caller's title, while posting outside it
is what raises the owner tag question above. Android sidesteps both by not using the registry for
`runAlarm` at all.

Worth knowing while deciding: `UiInteraction` is injected on iOS but never called. `LoopPlugin` and
`TreatmentsViewModel` hold it without using it in commonMain, and every reader of `mainActivity` and
`errorHelperActivity` is androidMain - checked, not assumed. So the iOS implementation is needed to
satisfy the graph, and has no caller to satisfy yet.



## Two notes from the desktop-target work

**`ClockPattern`'s tests followed it to commonMain.** Moving the reader to `commonMain` so the
desktop target could use it left its tests behind in `iosTest`, which meant a shared parser was
being checked on only one of the targets that use it - the exact thing the "Where tests for common
code go" section above warns about. The six pure parser cases are now in
`core/ui/src/commonTest/`, and run on iOS and the Android host; the two that drive a real
`NSDateFormatter` over 34 locales stay in `iosTest`, because they cannot be shared and they are what
found `zh_TW` in the first place. This needed one line in `core/ui/build.gradle.kts` -
`implementation(kotlin("test"))` on `commonTest`, copying the pattern already in `ui`.

**A stale comment, left for you rather than edited.** DONE - deleted, and the same wording in
`core/nssdk/build.gradle.kts` found by the sweep you suggested. Both modules have a real `jvm {}`
target now, so both comments described a `mingwX64()` call that no longer exists. The original note:
`core/utils/build.gradle.kts` still describes
mingw as "The only Kotlin/Native target whose tests can actually RUN on a Windows machine", which
stopped being true with `fa8800bdde`. Worth a sweep for the same wording elsewhere - the two-host
testing rule it refers to no longer exists, and it is the kind of comment that keeps a wrong idea
alive long after the code is gone.


## DONE: real strings for iOS and desktop, from one generator

`DesktopTextResolver` is now a verbatim copy of `IosTextResolver`, KDoc and all. Two identical
placeholders is the signal that this stopped being an iOS problem: both non-Android platforms render
every label as its string **name** - a settings screen reads `configbuilder_general` and
`pref_title_low_mark` - and that is the single biggest thing standing between the shared UI and
looking like a real app on either.

Your note in `DesktopTextResolver` points at the fix and it checks out, with one correction:
`GenerateKeyStringsTask` currently reads only the **names**. `readStringNames(baseDir)` is the only
parse it does, and both generated files it emits - the `TextRef` object and the Android id map - are
keyed on names alone. The text values are never read, so a `name -> text` map is a real addition to
that task rather than a wiring job on something that already exists.

What that buys, and why it is worth doing once rather than twice:

- one generated map serves **three** platforms, and the two placeholder resolvers collapse into it,
- the English `strings.xml` is already the single source both platforms would read, so there is no
  new place for wording to drift,
- format strings start working. Both resolvers currently append arguments rather than substituting
  them, because there is no `%1$s` to substitute into - so today a dose or a count appears bolted
  onto the end of a label.

Left with you rather than done here: it is a `buildSrc` change that affects the Android build too,
which is exactly the kind of cross-cutting task this split keeps on your side. The iOS half
afterwards is deleting `IosTextResolver` and providing the shared one, which is a few lines.

Localisation is a separate question and not part of this - the first step only needs the English
values that are already parsed.

### Done. What is there now, and the few lines left on your side

Your correction was right: the task only read names, so reading the values was a real addition. It
now writes a third file next to the other two, from the same pass:

- `GenerateKeyStringsTask` emits `XxxStringsValues` - a `name -> English text` map with
  `textOf(name)` - into the **common** output directory. That directory is already registered as a
  commonMain source dir, so no module needed a build change. Android never reads it, and R8 removes
  it because nothing there references it.
- The text is unescaped the way AAPT does it: whitespace collapsed unless the value is quoted, then
  the backslash escapes resolved. Format placeholders are left exactly as written.
- The map is written in chunks of 200 entries across several private functions. `:core:ui` has over
  eleven hundred strings, and a single `mapOf` of that size is one method against the 64K limit.
- `TextRefValueRegistry` (`:core:interfaces`, beside `TextRefIdRegistry`) is where a shell says which
  owners it can resolve.
- `GeneratedTextResolver` (`:implementation` commonMain) is the shared `TextResolver`. It carries
  **no** `@ContributesBinding` on purpose - that would collide with `ResourceHelperImpl` on Android -
  so each shell provides it.
- `formatTemplate` beside it does the substitution: `%s`, `%d`, `%f`, the uppercase forms, explicit
  argument indexes, precision, width and a literal per cent sign. Numbers go through `NumberFormat`,
  so a separator here matches one produced anywhere else in the app. A missing argument leaves the
  placeholder visible instead of throwing the way Java does, because a crash while drawing a label is
  worse than a visible placeholder.

**Your half.** Delete `IosTextResolver`, provide `GeneratedTextResolver()` from the iOS graph, and add
an `IosStringOwners` mirroring `DesktopStringOwners` - one `TextRefValueRegistry.register(owner)` line
per module `:ios:shell` depends on. The desktop one registers `keys`, `interfaces`, `coreUi`,
`implementation` and `ui`.

**Copy `DesktopStringOwnersTest` too.** The owner is a plain String on both sides - `owner.set("coreUi")`
in the build file against `register("coreUi")` in the shell - so a typo compiles and the only symptom
is a screen still showing names, which reads as unfinished UI rather than as a bug. The test asserts
one real string per registration, and takes each owner from that module's generated object instead of
repeating the literal.

Verified: 28 tests in `:implementation` commonTest, so they run on iOS as well, plus 4 in the desktop
shell; `:app:assembleFullDebug` and `:implementation:compileKotlinIosArm64` both green. The desktop
app now renders "General" where it read `configbuilder_general`.

## Done

- **`UrlOpener` and `PrefsFileInfo` - iOS side done.** Both ports landed from `kmp` and both have an
  iOS implementation with tests. `IosUrlOpener` (`implementation/src/iosMain/.../ui/`) goes through
  `UIApplication` on the main queue, which is where the hop in `SystemUrlLauncher` comes from -
  `sharedApplication` may not be touched off the main thread and the callers are view models. It
  refuses text that is not an address and an address with no scheme, because `openURL` drops both
  without a word and the user would only see nothing happen. `IosPrefsFileInfo`
  (`implementation/src/iosMain/.../maintenance/`) answers `isDirectoryAccessGranted` by actually
  looking at the app's Documents directory - iOS has no grant to obtain, but the directory can still
  be absent, and a fixed `true` would be a lie the maintenance screen acts on.

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
- The map picker is a fourth automation seam, added from the Android side: `MapPickerScreen` is an
  `expect` composable (osmdroid on Android), with `expect val isMapPickerAvailable` next to it. iOS
  returns false and `TriggerLocationEditor` hides the "pick from map" button, because the editor has
  **no manual latitude/longitude field** - so on iOS a location trigger can only be set from the
  current position until a MapKit picker exists. Same reasoning as the Bluetooth trigger above, but
  the opposite decision: the feature is hidden rather than present and dead.
- `ConstraintsCheckerImpl` - moved to `commonMain` (`a76cca9e41`)
- `ProfileRepositoryImpl` - moved to `commonMain`, off `org.json` (`3252f044b1`)
- `PluginStore` / `PluginPermissions` - split so the registry is no longer Android
- `DateUtilImpl`, `PreferencesImpl`, `ProfileUtilImpl` - moved to `commonMain`
