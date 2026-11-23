# iOS blockers

> **Audited 2026-09-21 at `7e303d9bb5`, on macOS.** Only the claims below were checked against the
> tree; everything else in this file is as it was and may be older than it looks. Corrected: the
> alarm entry in "Known gaps" said iOS alarms are silent and they are not, and two rows of the
> "Open" table were already done. Verified unchanged in the same pass: `compileKotlinIosArm64`,
> `:ios:shell:linkDebugFrameworkIosArm64` and `iosSimulatorArm64Test` are all green (714 tests, 0
> failures, 14 modules). Every row of the "Open" table, the dependency-cycle section and the
> `BtConnectionSource` note were then checked too - each carries its own dated result. **Not
> checked:** everything outside the "Open" and "Known gaps" sections, which is most of this file.

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
| `MasterActionExecutorImpl` | still `javax.inject`; also `ConcurrentHashMap` and `AtomicLong` |

Left for the kmp session because it is a sweep across one module rather than iOS work. Worth doing
mostly because each one that moves is one less thing an iOS graph cannot build - `ProtectionCheck`
and `PasswordCheck` in particular sit under the settings screens.

Two warnings from having done several of these:

- `@Synchronized` does not exist in `commonMain`. Use `AapsLock`, as `PreferencesImpl` does.
- `Dispatchers.IO` reports itself as `internal` rather than missing. Use `aapsIoDispatcher` from
  `:core:interfaces`.

## For the iOS side: move the database out of Documents, with a migration that fails closed

> **Done on the iOS side, and the question is answered: yes, a build has reached a tester with real
> data.** The TestFlight build is installed on an iPhone 15, so by this section's own criterion the
> migration wants a second pair of eyes. What to look at first is `resolveDatabasePath`, which
> decides the path from what is on disk rather than from what the move returned - a rollback can
> fail too, and trusting the return value there strands the `.db` at the new path while sending Room
> to the old one, which is the silent empty database this was written to prevent. A test found that;
> it was not spotted by reading.
>
> Also moved: `aaps-ios.db.lck`, which the shipped build leaves beside the database and this section
> did not list. It moves best-effort and its failure is not the migration's failure, because it holds
> no data - but it had to be handled, or the export feature's `UIFileSharingEnabled` would put a
> stray lock file in the user's Files app.
>
> Verified on the simulator against a real database, not only in tests: the four files moved, the
> rows survived, and `aaps.log` recorded the move. Eleven tests in `IosDatabaseMigrationTest` cover
> the move, the journal files, the fail-closed path, the partial move that is put back, and the
> stranded case.

The last open item from the platform parity audit. Everything else from that audit is fixed and
pushed; this one is yours because only the Apple side can test it, and because the decision it needs
is about iOS storage policy rather than about shared code.

**Where it is now.** `AppDatabaseBuilder.ios.kt`, `documentsPath()`: `aaps-ios.db` and its `-wal` /
`-shm` siblings are created in `NSDocumentDirectory`. That directory was not chosen as a policy - the
KDoc above the function says it was settled while fixing a CI failure, where the directory did not
exist on a clean runner.

**What actually makes this urgent is not the backup.** The audit filed it as a privacy problem,
because Documents is included in the iCloud/device backup and nothing calls
`NSURLIsExcludedFromBackupKey`. On reflection that is the wrong way round: a backup is how an iOS user
keeps their treatment history when they replace a phone, which is better than Android manages today.
Do **not** exclude it from backup to "fix" this - that trades a real benefit for a theoretical gain
the user can already get with Advanced Data Protection.

The hazard is the directory itself, and it works like this:

- `Room.databaseBuilder(name = path)` performs **no existence check**. Point it somewhere with no
  file and it does not fail - it creates a new, empty, schema-correct database. To the user that is
  indistinguishable from a fresh install, with the whole treatment history gone and no error anywhere.
- Documents is one build setting away from being user-visible. `UIFileSharingEnabled` and
  `LSSupportsOpeningDocumentsInPlace` appear nowhere today (checked `Info.plist` and the pbxproj), so
  the files are currently safe from the Files app.
- But `IosPrefsFileInfo` is meant to write preference exports into that same directory, and an export
  nobody can reach is useless. So whoever finishes that feature will find `UIFileSharingEnabled`, set
  it, and be right to - at which point a user tidying up their Files app can delete `aaps-ios.db` and
  get a silent empty database on the next launch.

**What to do.** Move to `NSApplicationSupportDirectory`, which is app-private and still backed up, and
migrate the three files once. The existing helper already creates the directory when it is missing, so
that part costs nothing.

**The migration has to fail closed, and this is the whole risk of the change.** If the `.db` did not
arrive at the new path and a file still exists at the old one, keep using the old path for that launch
and log loudly. Failing open lands exactly on the silent-empty-database case above. Move rather than
copy, so it cannot run twice or leave two histories that then diverge. Take `-wal` and `-shm` with it -
a database file separated from its write-ahead log is not the database.

Do not use `NSCachesDirectory`. iOS evicts it under storage pressure, and combined with the missing
existence check the app would come back empty on its own.

**One question the Windows side cannot answer, and it decides how carefully this needs reviewing:**
has any iOS build reached a tester with real data? `git tag -l "ios-testflight-*"` is empty, but the
release workflow also fires on `workflow_dispatch`, which leaves no tag behind - so the repo does not
know. Write the migration either way; if the answer is yes, it wants a second pair of eyes.

### While you are in the notification code: the other half of the actions gap

Not a blocker, and smaller. `IosSystemNotificationPlatform.show()` still ignores
`AapsNotification.actions`, so an alarm's snooze buttons never reach the iOS banner - the user has to
open the app to answer. The dangerous half is already fixed: a swipe can no longer be taken as an
answer (see `clearedByDismissal`), because it used to silence an urgent Nightscout alarm, throw away
the card holding the snooze buttons and never acknowledge Nightscout.

Attaching them properly is a `UNNotificationCategory` per resolved label set, registered before
posting and routed back by instance key. `IosLoopNotifier` already does exactly this for its "ignore
for N minutes" buttons and is the worked example. Android cannot be copied here - it suppresses such
notifications from the tray instead, because its actions are `PendingIntent`s.

## Ready for iOS: the keep-alive checks (2026-11, branch `ios-master-kmp`)

Written by the Windows session. The goal is now a full iOS app, one that runs a pump and the loop, not
only a follower. The Windows session moves the shared parts; the Apple parts are listed here.

`KeepAliveRunner` (`implementation/src/commonMain/.../maintenance/`) has every check `KeepAliveWorker`
made every 5 minutes: the pump status and profile check, the pump unreachable alarm, the zero temp
basal retry, the status upload when the loop does not do it, the clock change check and
`PeriodicMaintenance`. The Android worker keeps only the WorkManager schedule and its database checks.

What the iOS side has to do:

- **Call `keepAliveRunner.runOnce(minInterval = 4.minutes)` whenever the app is woken** - a Bluetooth
  event, a reading, the app coming to the front. Calls that come too soon are skipped, and a second
  call while a pass runs is skipped too, so calling it often is safe.
- **Then stop calling `periodicMaintenance.start(...)`** in that app. The runner already runs
  `PeriodicMaintenance`, and both would run the housekeeping twice. A follower that does not run the
  runner keeps `start` as today.
- **Find out what wakes the app every 5 minutes.** iOS gives a woken app about 10 s and keeps nothing
  running. A connected pump wakes it only when the pump sends something. The Carelevo patch is known
  to send only alarms (0xA1-0xA3), the stop report with auto-resume (0x8A) and the basal restart
  report (0x88), nothing at a fixed interval. A CGM on Bluetooth sends a reading every 5 minutes, so it
  is the likely heartbeat. Not decided yet.

The tests are in `androidHostTest` (`KeepAliveRunnerTest`, 36 tests), so they do not run on iOS yet.

**Done on the iOS side (2026-11-20):** `IosStartPlatform.startKeepAlive` runs `keepAliveRunner.runOnce`
(with `minInterval = 4.minutes`) in a loop that waits on the wall clock in 10 s steps (`delayUntil`),
for the full app and a client, and no longer calls `periodicMaintenance.start`. A plain `delay` counts
only the time the phone is awake, so after the phone slept the next pass could be hours late. Still
open: the 5-minute heartbeat. The plan is the CGM drivers on Bluetooth that come later; until then a
locked phone runs a pass only when something else wakes the app.

### DONE on the iOS side: `RunningModeExpiryTimer`

`RunningModeExpiryScheduler` is in `plugins/aps/src/commonMain` now. It decides when the end of a
temporary running mode (loop suspended, pump disconnected) has to run `RunningModeExpiryJob`, and asks a
`RunningModeExpiryTimer` to run it then. Android's timer is WorkManager.

The mode itself ends on its own time. The job is a safety net: it cancels the zero temp basal that was
set for the mode, so that insulin does not stay off after it. iOS cannot run code at an exact time
while the app sleeps, so the iOS timer should run the job late rather than never: a coroutine delay
while the app is awake, and at the next wake check whether the end has passed and run the job then.
`AppStartSequence` (below) starts the scheduler, so the iOS graph needs the timer as soon as it runs
the sequence, follower or not. The desktop's `DesktopRunningModeExpiryTimer` (a coroutine delay) is
the shape to start from.

**Done (2026-11-19).** `IosRunningModeExpiryTimer`
(`plugins/aps/src/iosMain/.../loop/runningMode/`) is exactly that: a coroutine delay on the app
scope, cancelled and re-armed on each `schedule`. It runs the job late rather than never, as asked -
a delay that fell due while the app was suspended fires on resume. The real net is still
`RunningModeExpiryScheduler.start()` re-reading the database at the next start, which is what covers
a delay the system threw away entirely. Five tests in `plugins/aps/src/iosTest/`; the provider throws
a marker rather than building a real `RunningModeExpiryJob`, which is final and wants five
collaborators.

### DONE on the iOS side: one start sequence for every app, `AppStartSequence`

`appshell/src/commonMain/.../start/AppStartSequence.kt` is everything `MainApp` did at start, in one
order: the snackbar fallback, the preference migrations (and the simple mode and temp target defaults
of a new master), the profile reload, the monthly database vacuum, the plugins and the wait for them,
the running mode reconciler and expiry scheduler, the profile switch scheduler, automation, the eat
reminder, the old-record insulin migration, then the version record, the "AAPS started" note, the
setup notices, the pre-snooze, the keep-alive, `initCompleted()` and `EventAppInitialized`. A platform
gives only what it alone can do, through `AppStartPlatform`. Android and the desktop run it; iOS does
not yet. Tests: `AppStartSequenceTest` (16, `androidHostTest`).

What the iOS side has to do to run it:

- **Expose it** in `IosAppGraph` (`val appStartSequence: AppStartSequence`) and bind a
  `RunningModeExpiryTimer` (see above), or the graph does not compile.
- **Run it in the background, behind the splash screen.** `IosAppStartup.run()` registers the plugins
  on the main thread before the first screen, because a view model built before that throws "No pump
  selected". The sequence does the registration itself, after the migrations, and can take seconds
  (the vacuum). So `IosClientConfig` has to start with `InitProgress(done = false)`: `AapsAppRoot`
  then shows the splash and composes nothing that reads the pump until `initCompleted()`. Android
  works this way. Then call `appStartSequence.run(plugins, platform)` from a coroutine, with the same
  sorted `contributedPlugins` list.
- **`AppStartPlatform` for iOS**: `versionCode` from `CFBundleVersion`, `deviceName` from the device
  model, `startServices()` for what is iOS-only, `startKeepAlive()` = `periodicMaintenance.start(...)`
  for a follower (the keep-alive runner on wake for a master), `beforeReady()` empty or iOS-only work.
- **Then remove** from `IosAppStartup` and `startApp` what the sequence does now: the plugin
  registration and `announceReadyWhenStarted`, `startAutomation`, `preSnoozeAlarms` before
  `periodicMaintenance.start`, `snackbarNotificationFallback.start()` and `eatReminder.restore()`.
  `announceReadyWhenStarted` in `core/interfaces` is used only by iOS and can be deleted after. Install
  `IosNotificationDelegate` before the sequence runs, because the snackbar fallback posts through it.
- **Run `IosBgQualityCheckWiringTest` and `AppStartSequenceTest`'s scenarios on the simulator** once
  wired; the migrations and the vacuum have never run on Apple.

**Done (2026-11-19), and the migrations and the vacuum have now run on Apple.** Every item above is
in place: `IosStartPlatform` (`ios/shell/src/iosMain/.../start/`), `appStartSequence` on
`IosAppGraph`, `IosClientConfig` starting at `InitProgress(done = false)`, and `IosAppStartup`,
`PluginRegistry` and `IosAppStartupTest` deleted. `IosNotificationDelegate.install()` had to move
**above** the sequence, because the snackbar fallback the sequence starts posts through it.

Observed on the simulator, not merely compiled: `Startup VACUUM done: DONE` (1326 changes), the
old-record insulin migration, `Registered 21 plugins`, `App start finished`, `onEventAppInitialized`,
the "Identification not set in dev mode" banner from `postSetupNotifications()`, zero uncaught
exceptions, and a `versionChanges` row written 10 s after start as `RECORD_DELAY` intends.

Async registration is safe because `AapsAppRoot` composes `content` inside
`AnimatedVisibility(visible = initProgress.done)`, which keeps the subtree out of composition
entirely until the sequence finishes - so nothing reads the pump early. Android has always worked
this way.

One caveat: **`AppStartSequenceTest` still has not run on Apple.** `:appshell` has no iOS test source
set, so its 16 tests remain `androidHostTest` only. What ran on the simulator is the sequence itself,
through the app, not those tests.

### `IosBgQualityCheck` is gone: iOS uses the real `BgQualityCheckPlugin`

The plugin is in `plugins/constraints/src/commonMain` and registers itself on every platform, as a
plugin and as `BgQualityCheck`, as it always did on Android, followers included. So the iOS follower
now shows the doubled or flat readings badge like an Android follower does, and a master iOS app gets
the LGS fall-back on doubled readings. `IosBgQualityCheck` and its test were deleted;
`IosBgQualityCheckWiringTest` checks the new wiring and has not run yet.

### The plugin lists a master needs, and `FreeStorage`

`@APS`, `@PumpDriver` and `@NotNSClient` are all in `core/interfaces/src/commonMain` now, so an iOS
master graph can merge those three plugin lists the way `MetroGraphs` does on Android. `IosAppGraph`
merges only the unqualified list today.

In the `@APS` list, `StorageConstraintPlugin` (stops the closed loop when the phone is nearly full)
is in `commonMain` and reads the free space through `FreeStorage`
(`plugins/constraints/src/commonMain/.../storage/`).

**Done (2026-11-19): `IosFreeStorage` in `plugins/constraints/src/iosMain/.../storage/`.** Four tests
on the simulator.

**It does not use `NSFileSystemFreeSize`, which is what this section used to recommend.** Follow that
advice and the plugin misfires: the plain figure reports only what is free right now, while iOS will
evict gigabytes of purgeable caches to satisfy a write. Against a 200 MB threshold the two disagree
completely, and the smaller number is not the cautious choice - `StorageConstraintPlugin` turns a low
answer into `isClosedLoopAllowed = false`, so it would switch closed looping off on a healthy phone.
It reads `NSURLVolumeAvailableCapacityForImportantUsageKey` instead, which is what the app could
actually write, and reads the plain figure alongside only to log a line when the two differ by more
than a tenth. When it cannot measure at all it returns 0 and logs an error: not knowing how much room
the database has is a fair reason not to run a closed loop, and an invented value would be a silent
no-op in the one class whose job is to notice a full disk.

A probe of the master graph on iOS found exactly **three** missing bindings, not a wall: `FreeStorage`
(now done), `@PumpDriver Map<Int, PluginBase>` and `@NotNSClient Map<Int, PluginBase>`. `Loop` and
`@APS Map` already resolved, and after `IosFreeStorage` the whole `@APS` bucket - loop, openAPS
engines, constraints - builds for iOS. The remaining two are *unpopulated*, not broken: every pump
driver, and `VersionChecker` / OpenHumans / Tidepool, are Android-only. `@Multibinds(allowEmpty =
true)` is what makes an empty bucket legal, with the precedent in `AppRootGraph`.

## Ready for iOS: the encrypted settings store (2026-10, not yet run on Apple)

Written by the Windows session. `IosSp` and `NSUserDefaults` are gone as the settings store. iOS now
uses the same `EncryptedKeyValueStore` (`shared/impl`) as the desktop: one file, AES-256-GCM, written
through DataStore's okio storage, which is the only file storage DataStore has on iOS.

- `ios/shell/.../prefs/IosStorage.kt` opens it once, before any graph. The file is
  `Application Support/aaps/preferences.store`, and that folder is excluded from iCloud and computer
  backups: the key is `ThisDeviceOnly`, so the file could not be read on another phone anyway.
- The key is in the Keychain, service `app.aaps.settingsstore`, account `preferences`
  (`KeychainStoreCipher` in `implementation/src/iosMain`). `Keychain.loadStrict` tells "no key" apart
  from "the Keychain did not answer", so a locked Keychain never leads to a new key.
- The first start moves every text, true/false and number value of the app's own defaults domain into
  the store (`UserDefaultsMigration`), then removes those keys from `NSUserDefaults`. Only once: a
  flag in `NSUserDefaults` closes the move, because Firebase and iOS keep their own values there and
  a move on every start took those too (fixed 2026-11-20).
- A file that does not open with the key shows `StartupFailedScreen` (`appshell`, shared with the
  desktop) with "Start fresh"; a Keychain that does not answer shows "Try again".
- Graph tests call `TestStorage.install()` first: a test binary has no Keychain.

Only cross compiled so far. To check on the Mac: `iosSimulatorArm64Test` (new: `EncryptedStoreFileTest`
in `shared/impl` commonTest on a real file, `KeychainStoreCipherTest`, `UserDefaultsMigrationTest`), the
framework link, and in the app: an existing install keeps its settings after the update, and a second
start is clean.

## Ready for iOS: the Nightscout client

Written by the Windows session, for the macOS one - the other direction from the rest of this file.

`plugins/sync/nsclientV3` is **60 files in commonMain, 17 on Android**. The plugin, the whole
client-control subsystem (receiver, round trip, both repositories, pairing offer fetch/publish,
preferences publisher, orphan detector), the incoming data processor, all sixteen NS extensions,
the nine load/upload bodies and all three screens now build for `iosArm64`.

What is still on Android is there because it has to be: the nine `Worker` shims, `NSClientV3Service`
(wake lock + `START_STICKY`), `SocketIoNsSocket`, `JsonBridge` (org.json), `StoreDataForDbImpl`,
and the Android halves of the two ports below.

### Three interfaces need an iOS implementation

Nothing else blocks the NS client on iOS. All three are in
`plugins/sync/src/commonMain/.../nsclientV3/ws/`, each with a working Android implementation to read.

| interface | what it does | iOS side |
|---|---|---|
| `NsSocket` | one Nightscout websocket namespace. `on(event, listener)`, `connect`, `close`, `emitWithAck`. Payloads cross as **JSON text**, so no socket.io types leak | a Ktor websocket client. `SocketIoNsSocket` shows the exact event names and the ack shape |
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

### DONE: nothing stands between the NS client and iOS any more

**Re-measured 2026-11-19.** This section was called "Six bindings stand between the NS client and
iOS". Five of the six are done and the sixth is deliberate, so the list below is a record rather
than work. `UiInteraction` was the last one, cleared by step 6 of `_docs/PUMP_DESIGN.md`.

It is kept because the corrections under the table are still worth reading: two of the entries the
earlier list of twelve named as missing were never missing at all, for a reason that will happen
again.

| Missing binding | Whose | Note |
|---|---|---|
| ~~`Autotune`~~ | done | `AutotunePlugin` is common code and registers itself on every platform; iOS supplies only `IosAutotuneFileStore` (`Documents/autotune`, zip written by `StoredZip`). `IosAutotune` is deleted. Compiled for iOS, not yet run on the simulator (2026-11-15). |
| ~~`ExportPasswordDataStore`~~ | done | Shared `ExportPasswordDataStoreImpl` is in commonMain and the iOS half is `IosExportPasswordPlatform`. This section already says so further down ("`ExportPasswordDataStore` - iOS side done"); the table simply was not updated. Checked 2026-09-21. |
| ~~`ImportExportPrefs`~~ | done | `LocalImportExportPrefs` in commonMain serves iOS and desktop; settings export and import were run end to end on the simulator, including Google Drive. A document picker is still missing, but that is a directory-chooser gap, not a missing binding. Checked 2026-09-21. |
| ~~`IobCobCalculator`~~ | done | `IobCobCalculatorPlugin` is in `plugins/main/src/commonMain` now, and iOS builds it through `ClientHistoryWindowGraph` (shared with desktop). The "needs a home outside :app" problem is solved. Checked 2026-09-21. |
| ~~`UiInteraction`~~ | done | There is no iOS implementation any more, and that is the fix rather than a gap. `runAlarm` raises a `Alert` with `AlertLifetime.EVENT`, so the app's own alarms are cards in the notification list like a pump's alert, and the one implementation - `app.aaps.implementation.ui.UiInteractionImpl` in commonMain - serves both platforms. `IosUiInteraction`, whose `runAlarm` only wrote a line in the log, is deleted. The two `KClass` members that had no iOS meaning are `AndroidActivities` now. Step 6 of `_docs/PUMP_DESIGN.md`, 2026-11-19. |
| `NsSocketFactory` | ours, by design | Correct as written, and not a missing class: there is deliberately no `IosNsSocketFactory`. The implementation is Swift and enters as a factory parameter at start up. Checked 2026-09-21. |

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

> **Still true, and now evidenced rather than argued (2026-09-21).** A Metro cycle fails the graph
> at compile time, so it cannot be present in a build that works. At `7e303d9bb5` the iOS graph
> compiles, links into `AapsShared.framework` and runs 714 tests green, which means all three are
> genuinely broken by the `Provider` indirection described below. `TriggerFactory` still carries the
> comment naming the `BtConnectionSource` cycle it breaks. Expect more of these, as this section
> says - the reasoning holds.

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

> **Verified 2026-09-21.** No `IosBtConnectionSource` file exists anywhere in the tree, and
> `AutomationRuntime` in commonMain carries the binding
> (`(AppScope::class, binding = binding<BtConnectionSource>())`). Accurate as
> written.

`IosBtConnectionSource` is deleted. Once `AutomationRuntime` moved to commonMain it began
contributing `BtConnectionSource` itself, which made two bindings on iOS and failed the graph. The
shared one already gives the right answer there: its buffer is filled from `EventBTChange` on the
bus, and nothing posts that event on iOS, so the list stays empty without a second class to keep in
step. The behaviour is unchanged and still documented in the gaps section.

## Ready for Android: what the iOS side has built

### Worth knowing: Google deprecates the loopback redirect for the Android and iOS client types

Not a bug, not caused by any recent change, and not urgent - but all three platforms now depend on it,
so it should be a known risk rather than a surprise.

Google's native-app OAuth guide says the loopback redirect
(`http://localhost:PORT`) is **deprecated for the Android, Chrome app and iOS OAuth client types**,
and supported for **Desktop app** clients on macOS, Linux and Windows. The related out-of-band
copy/paste flow is already discontinued outright.

AAPS uses one client id on every platform against `http://localhost:8080/oauth/callback`. By
elimination it must be registered as a **Desktop app** client: an Android client type cannot use
loopback at all, and would be tied to a package name and signing certificate, which would make it
unusable from iOS and the desktop. A Desktop-type client is accepted whatever device presents it,
which is why Android has worked for years and why the iOS sign in succeeded.

So nothing is broken. What is worth doing is confirming the registered client type in the Cloud
console, because that single fact decides whether this is a non-issue or a thing to plan for. Nobody
on either session can see it from the code.

Google's error guide also settles the 403 question in the shared client's favour: 401 is invalid
credentials and should be answered by refreshing first, while 403 is permission and quota
(`rateLimitExceeded`, `storageQuotaExceeded`, `insufficientFilePermissions`) and is **not** token
expiry. `GoogleDriveManager` used to treat 403 as a dead sign in, so a full Drive or a rate limit
signed the user out; the shared client does not.

### Two things the Windows session found in the Drive merge

Written back for the macOS session. Both are fixed; neither was visible from your side.

**`IosGoogleDriveProvider` was dropping the forced token refresh.** It passed
`accessToken = { tokenClient.validAccessToken() }`, which compiles - the lambda's `it` is the
`forceRefresh` flag and is simply unused - so the retry a 401 triggers re-sent the *same* stale token,
was refused again, and cleared the sign in. That is exactly the clock-skew bug the retry was added to
prevent, reintroduced at the construction site. `GoogleDriveProviderTest` could not see it because it
builds its own wiring rather than using the platform class.

The construction is shared now: `googleDriveProvider()` in `commonMain` takes the engine and does the
rest, so `IosGoogleDriveProvider` and `DesktopGoogleDriveProvider` are one line of engine choice each,
and the client id is stated once. `GoogleDriveProviderFactoryTest` covers it and fails if the flag is
dropped again - verified by putting the bug back.

**Moving `CloudDirectoryManagerImpl` to `commonMain` broke the desktop build.** It carries
`@ContributesBinding(AppScope::class)`, so it collided with `DesktopCloudDirectoryManager`
(`[Metro/DuplicateBinding]`), and desktop contributed no `CloudStorageProvider`
(`[Metro/MissingBinding]`). Both are `:desktop:shell` compile errors, which the macOS session never
builds. Worth remembering: **a commonMain class with a contributed binding lands on all three shells
at once.** Desktop is fixed - the stub is deleted and it runs the real shared manager.

### Google Drive: the shared client is proven, Android is the last one not using it

`GoogleDriveManager` in `implementation/androidMain` is 1400 lines, and everything it does now
exists in `commonMain` and has been run against a real Google account from an iPhone. Moving Android
onto it is the last piece of the Drive work, and the only reason it has not been done from the macOS
session is that **an Android change cannot be runtime-tested there** - the SDK is installed but there
is no system image or AVD, and the behaviour at risk is user-facing.

What already exists, all shared and tested on Android, desktop and iOS:

- `GoogleAuthRequest` (PKCE, pinned to the RFC 7636 vector), `GoogleTokenStore`/`GoogleTokenClient`,
  `GoogleDriveApi` and `GoogleDriveProvider` - about 65 tests, all against Ktor's mock engine, so
  they run with no network and no account.
- `JvmAuthRedirectListener` in `jvmSharedMain` - the loopback socket, already compiled into the
  Android target. `AndroidAuthRedirectListener` is a one-line binding, the way `IosAuthRedirectListener`
  is.
- `OAuthCallback` and `ExportMetadata`, which Android already uses.

What Android still needs:

1. An `AuthBrowser` - a Custom Tab. **Not** the plain "open a link" opener: the sign in ends with a
   redirect to a port this app is listening on, and a browser that switches away lets the OS suspend
   the app and take the listener with it. Android is more forgiving than iOS here, but the shape is
   the same and the interface's KDoc explains it.
2. Construction, contributed into the `CloudStorageProvider` set. `IosGoogleDriveProvider` is thirty
   lines and is the worked example.
3. The behaviour `GoogleDriveManager` has and the shared provider does not: it raises **user-visible
   notifications** on connection errors and pulls resource strings for them, where the shared one
   keeps an in-memory flag and lets the caller decide what to show. That is the part worth reviewing
   rather than porting mechanically - it is the only real behaviour difference, and it is the reason
   a straight swap is not obviously safe for existing users.

Three traps already paid for on the iOS side, all fixed in shared code, none of them obvious:

- **Stored key names must be the ones Android already writes.** A name invented for the folder
  (`google_drive_selected_folder_id` instead of `google_drive_folder_id`) kept the user signed in and
  silently lost the folder they chose, so the next export went to the root of their Drive.
  `GoogleDriveProviderTest` now asserts the *complete* set of stored names, not individual ones -
  checking three of them is what let the fourth through.
- **A 401 is not always a dead sign in.** It often just means the access token went stale early, so
  it is retried with a forced refresh before the credentials are cleared.
- **The sign in wait was sixty seconds.** That expires while a person is still typing a password, and
  when it does the listener closes, so the redirect that arrives a moment later hits a dead port. It
  is `AUTH_WAIT_MS` now, five minutes, shared - Android had the same bug, less visibly.


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
- **`IosForegroundWatcher` exists but is wired to nothing.** It was written to drive start/stop from
  the app lifecycle, closing the socket inside a background task assertion so it is not cut
  mid-frame. Nothing constructs it, so no lifecycle event reaches the Nightscout connection today.
  Its KDoc explains why it must not be wired as written: closing the socket on backgrounding is only
  safe if something reopens it, and the REST polling fallback its design assumed does not exist.


- **A dropped websocket may never be noticed on iOS.** The catch-up itself is shared and correct - a
  connect clears `initialLoadFinished` and refetches the missed window from the persisted high-water
  mark, verified on Android. What iOS lacks is anything that makes the connect happen: the process is
  suspended in the background (no `UIBackgroundModes`, no `BGTaskScheduler`), the foreground watcher
  above is inert, and the connectivity trigger only fires when `ReceiverDelegate`'s allowed verdict
  flips, not on every network change. Recovery therefore rests entirely on socket.io-client-swift's
  own reconnect surviving suspension, which is unverified. A shared watchdog - force a load when
  `connected` has been false for longer than N - would close this on every platform.

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

- **FIXED, and this entry used to say the opposite.** Until 2026-09-21 this said `setAudibleAlarm`
  only logs and that an urgent alarm makes no sound on iOS. That is no longer true, and reading it
  as current would mean either redoing finished work or shipping an URGENT notification believing it
  will be silent. `IosSystemNotificationPlatform.setAudibleAlarm` drives
  `alarmSoundPlayer.play/stop`, guarded on `instanceKey == soundingKey` so a second alarm does not
  cut the first off. `IosAlarmSoundPlayer` is real `AVFAudio.AVAudioPlayer`: it resolves the sound
  with `NSBundle.mainBundle.URLForResource(..., withExtension = "mp3")`, sets `numberOfLoops = -1`
  for a continuous alarm, ramps the volume, and logs diagnostics when `AVAudioPlayer` refuses to
  start. Last changed `e1fa702fc3`, 2026-09-11. Verified by reading the code at `7e303d9bb5`.
  Only the second path below is still a project decision:
  - *While the app is not running* - only a Critical Alerts entitlement lets a notification break
    through silent and Focus. Apple grants it to medical apps on application. This is a project
    decision, not code.

  Earlier notes here said the entitlement was the only route. That was wrong: it is the only route
  for a notification-delivered alarm, not for one the app plays itself.

  **The gap between those two was open until 2026-11-19 and is now closed.** "While the app is not
  running" is not only the suspended case: the ramping player cannot start *at all* while the app is
  in the background, because `AVAudioSession` needs the process running and AAPS has no `audio`
  background mode. So the common case - a phone in a pocket - got no sound from either path, since
  every notification was posted silently on purpose. `IosSystemNotificationPlatform` now attaches
  `UNNotificationSound.defaultSound()` to a notification that carries an `AlarmSound` while no AAPS
  screen is in front (`audibleInBackground`), and starts the ramping player when the app comes to the
  front with an alarm still ringing (`onAppForeground`) - which the registry never did on its own,
  because `refreshAlarmSound` only calls `setAudibleAlarm` when the *top* alarm changes. A default
  notification sound is one shot rather than a ramp, and Focus still applies unless
  `AlertOverrideDoNotDisturb` is on, so the entitlement above remains the only way to break through
  silent mode. What changed is that silence is no longer the default outcome.
- `IosSystemNotificationPlatform.onDismissed` **is** wired now. Two things were needed and either
  one missing makes it silently never fire: a delegate on the shared centre, held in a property
  because that slot is weak, and a `UNNotificationCategory` carrying `customDismissAction`, without
  which iOS reports taps but not dismissals. That caveat is now fixed: categories are
  accumulated by `IosNotificationDelegate` (`core/interfaces/src/iosMain/.../notifications/`) and
  registered together, because `setNotificationCategories` replaces the whole set and iOS gives the
  app only one delegate slot. Register through it rather than calling the centre directly.
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


## DONE: `ExportPasswordDataStore` needs the same treatment the other big ones got

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

### FIXED: none of `:ios:shell`'s eleven tests had ever run

Fixed since this was written. `:ios:shell` now runs in `.github/workflows/ios-ci.yml` and reports
**88 tests** on the simulator. The account below is kept because the shape of the hole came back
twice more, in a form the fix did not cover - see the note at the end of this section.

Raised rather than fixed, because it is your workflow and only your CI can prove the change works.

`:ios:shell/src/iosTest/` holds eleven test files - `GuardedByTest`, `IosClientFlavorTest`,
`IosProbeGraphTest`, `IosHistoryWindowScopingTest`, `IosAppStartupTest`, `IosStringOwnersTest`,
`IosSpTest`, `IosBgQualityCheckTest`, `AppDatabaseIosTest`, `ProbeTextResolverTest`,
`PlatformActualTest`. **No machine runs any of them.**

### The evidence

On Windows the task is disabled and the build still succeeds - so a green local run says nothing:

```
> Task :ios:shell:iosSimulatorArm64Test SKIPPED
BUILD SUCCESSFUL in 13s
```

And `.github/workflows/ios-ci.yml`, the only job that can execute Kotlin/Native tests, is scoped
twice over so it never picks them up:

- its `paths:` triggers are `core/data/**`, `core/nssdk/**`, `core/keys/**`,
  `gradle/libs.versions.toml`, `buildSrc/**` and the workflow file. Nothing under `ios/shell/**`,
  `plugins/**` or `shared/**` starts it, which is most of what changes the iOS app.
- the tasks it runs are `:core:data`, `:core:nssdk` and `:core:keys` only. `:ios:shell` appears
  nowhere in the file, and no other workflow or CircleCI job mentions it.

This is the trap your own header warns about - *"it reports SKIPPED rather than failing, so a green
build elsewhere says nothing about iOS behaviour"* - and the workflow already carries an assertion
step written to catch it. It is just applied to `:core:data` alone.

### Why it is worth fixing rather than noting

`GuardedByTest` is the one that stings. It guards the fix in `e82a044293` where `withProtection`
was `{ _, action -> action() }` and ran **every** protected action without asking, bolus entry
included. That is invisible on a device with protection turned off, which is why it was worth a
test - and the test currently cannot fail. `IosHistoryWindowScopingTest` and `IosProbeGraphTest`
are in the same position: they cover whether the graph builds and whether the history window keeps
its own calculation state.

### The change, if you want it

Add `ios/shell/**`, `plugins/**` and `shared/**` to the `paths:` lists, add
`:ios:shell:iosSimulatorArm64Test` to the test task line, and extend the existing
`SKIPPED|NO-SOURCE` assertion and the results-file check to that module - otherwise it can quietly
return to running nothing. The compile side is already fine from here:
`:ios:shell:compileKotlinIosArm64`, `compileKotlinIosSimulatorArm64` and
`compileTestKotlinIosSimulatorArm64` all pass on Windows, so the sources are real and only
execution is missing.

### The same hole came back twice, in a form the fix did not cover (2026-11-19)

That fix watched `iosTest` source sets. Both of the next two escapes were `commonTest`:

- **Ten modules had a populated `commonTest` and were absent from the run step**, so their shared
  tests ran on the JVM only: `ui`, `database:impl`, `database:persistence`, `workflow`,
  `shared:impl`, `shared:clientbindings`, `core:ui`, `core:objects`, `core:utils` and
  `pump:carelevo`. All ten pass on Apple - 339 tests, 0 failures - so nothing was broken; the point
  is that nothing would have said so if something had been. `:ui` was among them, and its
  `DiacriticsTest` is the only thing that makes "the same search matches on both platforms" true
  rather than assumed.
- **`:core:keys` was in the run step but not in the `check()` list**, and its task is `SKIPPED`
  because its only tests are `androidHostTest`. Listing it read as Apple coverage it never had.

Both are fixed, and so is the cause: the auto-guard now finds `commonTest` as well as `iosTest`, and
skips a module that does not build for iOS (no `kmp-android-library`, no declared iOS target) so a
`commonTest` in a JVM-only module cannot fail it. Keep the run step and the `check()` list in the
same order - they are compared by eye, and that is how `:core:nssdk` came to run without being
checked.

**And a third time, through the guard itself (same day).** `:pump:carelevo:protocol` has a populated
`commonTest` (`DoseEncodingTest`, which checks the bytes a bolus is encoded into) and was in neither
list. The auto-guard did not catch it either, and that is the part worth remembering: it searched
with `find . -maxdepth 4`, and a nested module puts its source set at
`pump/carelevo/protocol/src/commonTest`, one level deeper than every other module in the repo. So
the step written to make this hole impossible had a hole of exactly the same shape. It is
`-maxdepth 5` now, and the derived list and the two hand lists agree at 20 modules. If a module is
ever nested deeper than that, raise it again rather than adding the module by hand - the hand lists
are the thing that keeps failing.

**A cast warning in that batch is not a bug.** `ui/src/iosMain/.../Diacritics.ios.kt` compiles with
"This cast can never succeed" on `(this as NSString)`. That cast is the Kotlin/Native bridge between
mapped types and converts at run time; `DiacriticsTest` passes on `iosSimulatorArm64`, Czech and the
Turkish capital-I case included. It carries `@Suppress("CAST_NEVER_SUCCEEDS")` now. Do not "fix" it.

## Done, with one thing you did not have visibility of

The port is `ExportPasswordPlatform` (`:core:interfaces` commonMain): `read()`, `write(secret,
timestamp)`, `clear()`. `ExportPasswordDataStoreImpl` moved to `:implementation` commonMain with
every rule intact - the validity window, the grace period, the master password cross check, and the
legacy alias handling that also deletes the old key. `CryptoUtil` became `PasswordHasher` there too,
the same swap as `PasswordCheckImpl`.

**The thing that was not in your analysis:** the class also injects `FileListProvider`, which is
`DocumentFile` and `java.io.File` throughout and cannot move. It is used by a dev-only block that
shortens the validity window to minutes so the expiry can be tested without waiting five weeks. I did
not want to delete a testing tool that is not mine, so the port carries a fourth method,
`shortenedValidity()`, returning null on every platform but Android. It is documented as going away
with the debug mode it serves. If you would rather it were a separate interface, say so - it was a
close call.

The iOS side is one class over `AppleKeychain`, as you expected. Desktop's is a properties file next
to the database holding the same encrypted envelope; that also let the
`DesktopExportPasswordDataStore` placeholder be deleted, so the remember-password feature is
genuinely available there now instead of reporting itself off.

**The old test only ever reached the disabled path** - it had two commented out cases admitting
`SecureEncrypt` could not be instantiated. With a fake platform there are now 10 tests covering the
window, the grace period, both master-password failure modes and the legacy alias. They sit in
`androidHostTest` rather than `commonTest` only because `Preferences` has seventy methods and Mockito
is not available in common code; the class itself is pure Kotlin.

Verified: `:implementation:compileKotlinIosArm64`, `:app:assembleFullDebug`, and 93 app tests plus
the 10 above, all green.


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

### DONE: `AapsAppRoot` is hosted on iOS, and a master graph resolves as well

This section used to say that pulling in `ClientControlActionDispatcher` drags every remaining
blocker with it, so hosting the real Compose root on iOS was gated on the whole set. Do not plan
from that. `AapsAppRoot` is called in `ios/shell/.../ui/AapsAppHost.kt`, and
`ClientControlActionDispatcher` is one of the accessors on `IosAppGraph`.

**Measured 2026-11-19 with the probe procedure above, and the answer is wider than this section.**
Nothing a *master* build needs is missing either. `IosProbeGraph` was given accessors for `Loop`,
`LoopPlugin`, the three openAPS plugins, `CommandQueue`, `CarelevoPumpPlugin`, `ObjectivesPlugin`,
`StorageConstraintPlugin`, `IobCobCalculator` and the `@APS` plugin map, and
`:ios:shell:compileKotlinIosSimulatorArm64` was green. The one binding it reported, `PumpEmulators`,
was a gap in the probe and not in the app - `IosAppGraph` already provides it - so the probe needs
that one provider copied in before it can answer this question again.

So what kept iOS a follower was not a missing implementation. It was three fixed answers in
`IosClientConfig` - `APS = false`, `PUMPDRIVERS = false`, `AAPSCLIENT = true` - and what read them.

**Done the same day: the `AAPS` target is the full app.** `IosClientConfig` now reads which build it
is from the bundle identifier (`flavorFor`), with the same expressions as Android's `ConfigImpl`, and
`AapsAppHost` merges the `@APS` and `@PumpDriver` buckets only when the build asks for them. CareLevo
was fully wired through `CarelevoIosRegistration` already and was invisible for that reason alone.
`IosAppGraph` gained the `@APS` accessor it never had. On iOS that bucket holds two plugins of three:
`SignatureVerifierPlugin` is Android-only, and whether the full app should have an equivalent is not
decided.

Reading the code rather than the lists turned up one gate that would have been skipped:
`isEngineeringModeOrRelease()` was a fixed `true`, which is right for a follower and wrong for the full
app - `SafetyPlugin` uses it to keep a development build from closing the loop. It follows Android now.

An earlier version of this section said three entries filed as deliberate would become bugs once
`AAPSCLIENT` was false. Two of the three were already solved: `IosSceneExpiryScheduler` is a real
implementation, and the shared `AppStartSequence` waits for plugin start off the main thread. The one
real gap on the full app is the keep-alive section above - nothing on iOS calls `KeepAliveRunner`, so
there is no pump-unreachable alarm yet. `_docs/ios_todo.md` section 2 keeps the current list.

**A resolving graph is not a working app.** The probe proves Metro can build these objects. It says
nothing about whether the loop runs, the patch connects, or the UI offers either.

## Fixed on the iOS side: `is24HourClock` answered wrongly in Traditional Chinese

Small change to `core/ui/src/iosMain/.../PlatformTheme.ios.kt`, flagged here because it is your file.

The actual read the AM/PM letter - `dateFormat?.contains("a") != true` - which is the usual advice
and is wrong for at least one locale. `zh_TW` asks for the short time pattern `Bh:mm`: `B` is the
flexible day period (上午 / 下午) and `h` is a 12 hour hour. There is no `a` in it, so the check
answered "24 hour" and a Traditional Chinese user with the "24-Hour Time" switch **off** would have
been given a 24 hour picker on the profile activation screen.

It now reads the hour field instead, which the Unicode standard fixes rather than the locale: `h`
and `K` count to twelve, `H` and `k` count to twenty four. The parser is `usesTwelveHourClock`, kept
apart from the composable so it can be tested, and it skips quoted literals so the `'h'` in a pattern
like `HH'h'mm` is not mistaken for a field.

**Since this was written, the Windows side found the same bug a second time and moved the parser.**
`DateFormatPlatform` asks the same question for `DateUtil`, and `IosDateFormatPlatform` still used the
AM/PM reading - so iOS gave two different answers at once, printing `HH:mm` next to a twelve hour
picker on a `zh-Hant` phone. The JVM had a third copy of the parse, written out again. There is now one
implementation, in `core/interfaces/src/commonMain/.../utils/ClockPattern.kt`, used by the theme on
both iOS and desktop and by `DateFormatPlatform` on iOS and the JVM. `:core:interfaces` is the lowest
module `:core:ui` and `:shared:impl` already share, so this needed no new dependency. The tests moved
with it, and the live `zh_TW` case now covers the `j` template that `IosDateFormatPlatform` asks for as
well as the short-time pattern the theme uses - they are different formatter calls, which is how the
two answers were allowed to disagree.

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

**`IosPluginPermissions`** (`implementation/src/iosMain/.../plugin/`) used to return empty from both
methods, on the idea that iOS has nothing to list. That was wrong in the way that matters: iOS asks
the user itself, but the answer can be read at any time, and a refusal is silent - refused
notifications meant alarms that never showed, with nothing on screen to say so. Since 2026-11-20 it
lists notifications always, Bluetooth from every enabled pump (`bluetoothPermissionGroup()` now
returns a group with `IosPermission.BLUETOOTH`) and location while automation uses a location
trigger, reads their state through `IosPermissionStatus`, and the shell shows the shared
`PermissionsSheet`. "Grant" asks iOS once and after a refusal opens the app's page in Settings.
Notifications are asked for at the first start, so a first alarm in the background is not lost.

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


## HISTORY: the alarm owner tag was a trap, and the trap is gone

Nothing on iOS ever played with `OWNER_FULLSCREEN`, and nothing anywhere does now: the tag is
deleted. This was written as a warning for whoever wrote `UiInteraction.runAlarm` for iOS. Nobody
had to in the end, because step 6 made `runAlarm` shared code over `Alerts`.

**This is history now, and the answer was route 1.** Step 6 of `_docs/PUMP_DESIGN.md` landed on
2026-11-19: every alarm is a card, the registry under `OWNER_INTERNAL` is the only sound owner, and
the alarm screen is a *view* of the sounding card rather than a second owner. `OWNER_FULLSCREEN` is
deleted, so the trap below can no longer be walked into. The title problem at the end of this
section is answered too: a card carries its own `alarmTitle`, which the system notification and the
watch both use. The account is kept because it explains why the other route was tempting - and
because the same shape will come up again on the next platform.

`AlarmSoundPlayer` records who started a sound. Two owners exist: `OWNER_INTERNAL`, used by the
notification registry through `setAudibleAlarm`, and `OWNER_FULLSCREEN`, used by Android's
`AlarmNotificationManager` for the full screen alarm that `runAlarm` posts.

`stopAlarm` is `notificationManager.muteAllAlarms()` on both platforms, and that ends in
`refreshAlarmSound()` plus `platform.cancelAll()`. The difference is what `cancelAll` does:

- `AndroidSystemNotificationPlatform.cancelAll()` calls `alarmNotificationManager().cancelAlarm()`,
  which stops the **`OWNER_FULLSCREEN`** audio and cancels the notification.
- `IosSystemNotificationPlatform.cancelAll()` removes only the delivered notifications it posted
  itself, matched by the instance-key parse. Nothing on the iOS side stops `OWNER_FULLSCREEN`,
  because nothing starts it. (It used to call `removeAllPendingNotificationRequests()` as well,
  which deleted the scheduled automation reminders - they share the `aaps-` prefix. Whatever
  `runAlarm` ends up doing, it must not widen this back out.)

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


## The strings work is wired on iOS, with one gap left on your side

`GeneratedTextResolver` and the generated value maps landed and they work: `IosStringOwners`
registers all sixteen modules the iOS framework links, and the settings screen now reads "Settings",
"General", "Absorption settings" and "Master device unreachable. Editing is disabled until the
connection is restored." instead of string names. `IosTextResolver` is deleted.

One thing was missing and is worth knowing about, because desktop has it too: **the Compose path was
still a placeholder.** `TextResolver` is not what a composable calls - `stringResource(TextRef)` in
`:core:ui` is, and both the iOS and the JVM `actual` still returned `ref.name`. The iOS one now
looks the text up through `TextRefValueRegistry`; `TextRefResource.jvm.kt` is still the placeholder,
and its KDoc still says the machinery "is not built yet", which stopped being true in the same
commit that built it.

**The gap that needs you: format arguments.** `stringResource(ref, vararg)` folds the arguments into
the `TextRef` and the actual has to substitute them. `formatTemplate` does exactly that and is
already written - but it is `internal` to `:implementation`, and `:core:ui` sits below that in the
dependency graph, so neither the iOS nor the JVM actual can call it. Today a format string renders
with its placeholders visible on both platforms, while the same string through `TextResolver` comes
out correct.

Moving `formatTemplate` beside `TextRefValueRegistry` in `:core:interfaces` would close it for both
platforms at once and leave `GeneratedTextResolver` calling the same function it calls now. It is a
move rather than new code, which is why it is left with you rather than done here - `:core:interfaces`
is shared, and `NumberFormat` (which it uses) is already in `:core:data`, below it.


## `ExportPasswordDataStore` - iOS side done

The shared move landed and the iOS half is `IosExportPasswordPlatform`
(`implementation/src/iosMain/.../protection/`). `IosExportPasswordDataStore`, the placeholder that
reported the store as switched off, is deleted.

One decision worth recording, because the interface allows the weaker answer. `ExportPasswordPlatform`
says an implementation "does not need secure storage of its own - it needs somewhere durable that
survives a restart and is private to this user". `NSUserDefaults` meets that and breaks the sentence
after it: a remembered password **must not travel to another device**, and preferences are carried in
an iCloud or iTunes backup and restored onto a new phone. So this uses the Keychain, where
`AppleKeychain` already writes `AfterFirstUnlockThisDeviceOnly` - never synced, never restored
elsewhere, still readable to a backgrounded export. A different service name from the encryption keys,
so clearing one cannot delete the other.

The secret and its timestamp are one entry, `<timestamp>:<secret>`, rather than two. Two entries can
be left half written, and the shared rules measure the validity window from the timestamp - so a
secret that came back without one would read as either infinitely fresh or infinitely stale, and both
are silent. Anything in the store that is not in that form is dropped and logged rather than guessed
at, which is most of what `IosExportPasswordPlatformTest` covers.

## Two flaky tests, noted rather than fixed

Both cost a false alarm today, so they are worth writing down even though neither is ours:

- `SerialIOThreadTest.testThreadLifecycle` (`:pump:dana:danar`, unit) failed one full gate run and passed
  the four runs after it, on a tree whose only changes were iOS files.
- `EquilEmulatorActivationTest.activatedPod_readsCancelsAndTogglesMode` (instrumented, CI shard C; the
  class was removed 2026-11-08, its checks are in `EquilScenarioTest` and `EquilUiTest`)
  failed on a push whose only non-desktop change was a markdown file and a test moving between source
  sets. Build 41072 on `kmp` failed the DanaRS instrumented shard the same way, on an About-dialog
  commit.

Both are timing-sensitive tests in pump drivers. The cost is not the runs themselves - it is that a
red gate stops meaning "you broke something", and the next real failure gets waved through.


## DONE (both halves): `ProtectionCheckImpl` can move too - but not on its own

**Both halves landed together, so the hang you warned about never exists.** Your reasoning was right
and the warning was the important part of the request.

1. `ProtectionCheckImpl` is in `implementation/src/commonMain/.../protection/`. One correction to the
   survey: it does **not** have "no Android imports at all" - the import list is clean, but it used
   `app.aaps.core.ui.R.string.*` fully qualified inline in six places, exactly the trap recorded
   further up this file. They are now `CoreUiStrings` values, so `title` is a real `TextRef` instead
   of `TextRef.AndroidRes(id)` - which also means the prompt title renders as words on iOS rather
   than `res:2131…`. `@Volatile` needed `kotlin.concurrent.Volatile`; `AtomicLong` was already the
   multiplatform one.
2. `ProtectionHost` is in `core/ui/src/commonMain/.../compose/`, and **`AapsAppRoot` places it**
   beside `PasswordCheckHost`. Your read of `FragmentActivity` was exactly right: it was only used to
   root the prompt, so it came out of the lambda types and `ComposeMainActivity` captures the
   activity itself. The host is otherwise unchanged.

`IosProtectionCheck` is deleted, `IosAppGraph` exposes `protectionCheck`, and `AapsAppHost` passes it
to `AapsAppRoot`. `:ios:shell:compileKotlinIosArm64` is green.

**What iOS gets without doing anything:** password and PIN protection, working properly. The
biometric lambdas are parameters of `AapsAppRoot` with defaults - `showBiometricSimple` falls back to
the credential dialog, and `showBiometric` cancels. So a platform with no biometric prompt asks for a
password instead of granting access, and never silently succeeds.

**What is still yours:** pass an `LAContext` prompt into `AapsAppRoot` as `showBiometric` /
`showBiometricSimple` when you want Face ID. Nothing breaks until then; it just falls back.

Desktop takes the defaults deliberately - there is no OS prompt worth wiring for a follower window.

`PasswordCheckImpl` moving to commonMain unblocked the class above it, and the same reading applies:
`ProtectionCheckImpl` (`implementation/src/androidMain/.../protection/`, 317 lines) has **no Android
imports at all**. Its dependencies are `Preferences`, `PasswordCheck` and `DateUtil`, all three of
which now resolve on iOS.

Biometrics does not block it. The class never prompts - `ProtectionType.BIOMETRIC` is only an enum
value it counts, and it publishes a `HierarchicalProtectionRequest` carrying `hasBiometric` for the
UI to act on. The prompting lives in `ProtectionHost`, which is a different question (below).

**The trap: moving the impl alone would make iOS worse, not better.** `IosProtectionCheck` currently
grants everything, which is wrong but at least passable while iOS reaches no pump. The real impl
publishes to `pendingAuthRequest` and waits for a host to call back. `AapsAppRoot`
places `PasswordCheckHost` but **not** `ProtectionHost` - only `ComposeMainActivity` does. So on iOS
the request would be published, nothing would render it, and the callback would never fire: every
protected action would hang rather than be granted or refused. That is worse than either.

So the two halves have to land together:

1. **Yours:** move `ProtectionCheckImpl` to commonMain. It needs nothing else.
2. **Ours:** a protection host for iOS.

### And `ProtectionHost` is closer to portable than it looks

It takes the biometric prompt as a lambda already -
`showBiometric: (FragmentActivity, String, () -> Unit, () -> Unit, () -> Unit) -> Unit` - so the
Android-only part is almost entirely in that one parameter's type. If `FragmentActivity` came out of
the signature, the host would be ordinary Compose and could sit in commonMain beside
`PasswordCheckHost`, with each platform passing its own prompt: `androidx.biometric` on Android,
`LAContext` on iOS, and a lambda that reports "no biometrics" on desktop.

That would be the fourth port of the same shape as `UrlOpener` and `PasswordHasher`, and it would put
real protection on both new platforms instead of one. Worth checking whether the `FragmentActivity`
is used for anything the caller could supply instead - if it is only there to root the prompt, the
platform lambda can capture it.

Until both halves exist, `IosProtectionCheck` stays as it is and stays documented as unsafe once iOS
can reach a pump.


## DONE (see below): move `formatTemplate` so format strings work off Android

The one part of the strings work still missing, and it is now the only reason a label on iOS or
desktop can still look wrong. Raised before as a note inside another section; promoting it, because
all four earlier requests are done and this is what is left.

`stringResource(ref, vararg)` folds the arguments into the `TextRef`, and the platform `actual` has
to substitute them. `formatTemplate` does exactly that, is already written and already tested - but
it is `internal` to `:implementation`, and `TextRefResource.ios.kt` / `.jvm.kt` live in `:core:ui`,
which sits below `:implementation` in the dependency graph. So neither actual can call it.

The effect is narrow but visible: a label with no arguments is correct on both platforms, and one
with arguments renders with its placeholders showing - "Exported %1$s ago". The same string through
`TextResolver` comes out right, because `GeneratedTextResolver` is in `:implementation` and can call
it. So the two paths disagree, which is worse than either being uniformly short.

Moving `formatTemplate` beside `TextRefValueRegistry` in `:core:interfaces` closes it for both
platforms at once and leaves `GeneratedTextResolver` calling the same function. `NumberFormat`, the
only thing it depends on, is in `:core:data` - below `:core:interfaces` - so the move should be
mechanical. Left with you because `:core:interfaces` is shared and the move touches Android's build.

## Application protection is wired on iOS, with one question left

Both halves landed and the iOS side is done: `AapsAppHost` now requests
`ProtectionCheck.Protection.APPLICATION` and draws nothing until it is granted. Verified on the
simulator - set a master password, set Level 0 to "Master password", relaunch, and the app opens to
an "Application password" prompt with the app hidden behind it.

Worth knowing why that wiring was needed at all: merging the shared classes did **not** make
protection work. `ProtectionHost` renders a prompt when something *requests* protection, and nothing
on iOS ever asked - Android asks from `ComposeMainActivity.onResume`. So the setting saved, the host
was in place, and the app still opened straight to the screen it was meant to protect. It looked
finished from the code and was not, which is the shape of failure this whole port keeps producing.

**Answered: iOS re-prompts on returning to the foreground, whenever app protection is on.** It now
asks at launch and on every `UIApplicationDidBecomeActiveNotification`, matching Android's
`onResume`. Verified on the simulator: unlock, press Home, come back, and the prompt is there again
with the app hidden behind it.

Two details worth knowing if this is ever changed:

- **It costs nothing when protection is off.** `requestProtection` grants straight away for "No
  protection", so no dialog is created and the gate is invisible to anyone who did not ask for it.
- **The request is guarded and re-locks first**, the same shape as Android's `isProtectionCheckActive`.
  `didBecomeActive` can fire while a prompt is already up, and without the guard a second dialog
  stacks on the first; without the re-lock the app stays readable behind the prompt on the way back
  from the background.

The setting still says "App launch. Independent from other levels", which now understates what it
does on both platforms. Worth a wording change, on Android too.


## Three answers that turned placeholders into decisions

Not ports, and not gaps - answers about what an iOS client is. Moved out of the `missing` package
into `platform`, which now means "this is what this build does" rather than "this is not written
yet". The two packages log at different levels on purpose: `missing` at **error**, because a call is
a screen reaching for something that should exist; `platform` at **debug**, because logging correct
behaviour at error teaches a reader to ignore the level that matters.

- **`BgQualityCheck` answers UNKNOWN.** The loop never doses on a client, and the badge that reads it
  judges *this device's* sensor - but a client's glucose comes from Nightscout, already judged by the
  master that owns it. Worth knowing: the APS plugins **are** registered on a client, deliberately
  (`ApsPluginRegistrations` puts them in the unqualified map so a follower still lists them), which is
  precisely why this binding has to exist at all.
- **The xDrip and Dexcom integrations report themselves disabled.** `NSClientSourcePlugin` is the only
  source plugin in commonMain; every other one is androidMain, because they are reached by Android
  broadcast intents. The bindings still exist because shared UI asks - `CalibrationDialogViewModel`,
  `ElementAvailability`, `TreatmentViewModel`, `MaintenanceViewModel` - and each hides its option when
  the answer is no.
- **Screen usage statistics are empty.** The collector is an `ActivityLifecycleCallbacks` and a client
  is not meant to collect them at all, so the statistics screen shows an empty table, which is
  accurate rather than broken.

## History browsing works on iOS

`IosHistoryScope` used to throw on every property - the right placeholder, because handing back the
app's calculation objects would have satisfied the types and let browsing history rewrite the state
the loop calculates on. It is real now: `ClientHistoryWindowGraph` is a `@GraphExtension` with its own
scope, the counterpart of `HistoryWindowGraph`, so each window owns its calculator, cache, signals
and overview data while sharing the database and preferences with the app.

It started as `IosHistoryWindowGraph` and `IosHistoryScope` in `:ios:shell`. Both moved to
`:shared:clientbindings` as `ClientHistoryWindowGraph` and `ClientHistoryScope` when desktop needed
the same thing, so there is one copy for the two shells. Each shell still states its own
`historyWindowFactory` accessor.

Verified on the simulator - the History Browser opens, with the date picker, day navigation and the
BG, IOB/BAS and COB graphs. `IosHistoryWindowScopingTest` pins the isolation by identity rather than
behaviour, because behaviour only differs once both are mid-calculation, which is too late to notice.

### Two things that were needed and are worth knowing

**A graph extension is only reachable through an accessor its parent states.** `IosAppGraph` declares
`historyWindowFactory`, the same way `AppRootGraph` does. Without it the factory reads as an ordinary
missing binding, which is how it first failed.

**Registering a notification category no longer attaches to the notification centre.**
`IosNotificationDelegate.register` used to call `install()` immediately, and
`CommonNotificationManager` registers one while it is being *constructed* - so simply building the
object graph touched `UNUserNotificationCenter`, which throws `bundleProxyForCurrentProcess is nil`
outside an app bundle. The whole app graph therefore could not be built in a test, which is how this
surfaced: a history scoping test died on a notification API it never meant to use. `install()` is now
called once from `startAapsApp` (at `App.init`, before launching ends, as Apple asks of a notification
delegate), and registering only records. Declaring what a category is,
and claiming a process-wide delegate slot, are different acts and only one of them needs an app.


## DONE: move the Main route into `appNavGraph` so both new platforms get the overview

Nothing is missing from `MainScreen` itself. Checked rather than assumed:

- `MainScreen`, `ManageSheetHost` and `SearchViewModel` are all in `commonMain`.
- All seven view models it takes - `Main`, `Manage`, `Maintenance`, `Status`, `Treatment`, `Scenes`,
  `LoopAction` - are `commonMain` and `@ContributesIntoMap`.
- Every one of them is **already constructible on iOS**: `IosAppGraph` compiles with
  `MetroViewModelMultibindings`, which means Metro validated each of their dependency graphs.
- `statusLightsDef` and `treatmentButtonsDef` come from `BuiltInSearchables`, which the iOS graph
  already exposes.

A trial call from the iOS shell produced only `Unresolved reference` errors - missing imports. Not
one "no binding found", which is what a real gap looks like.

What is missing is that **nobody has wired it**. The Main route is not in `appNavGraph`; it stays in
`ComposeMainActivity` because of its Activity-context callbacks. So each shell would have to call
`MainScreen` directly with about fifty arguments, thirty of them callbacks - and then iOS and desktop
would each own a copy of that wiring, which is the duplication this split keeps trying to avoid.

Moving the route into `appNavGraph`, the way every other screen went, gives all three platforms the
overview from one place. The callbacks that genuinely differ are few and already have the shape used
elsewhere: `onLaunchBrowser` is `UrlOpener`, navigation is the nav controller, and
`onRecreateActivity` / `onBringToForeground` have no meaning off Android and can be no-ops per
platform.

### Desktop has the same back-button dead end iOS had

`Main.kt` opens on `AppRoute.Preferences.route`, and its KDoc says it is "for the same reason the
Apple shell does". That has a consequence worth knowing before someone reports it as a bug: the
settings screen's back arrow calls `safePopBackStack()` on an empty stack, so **the arrow renders and
does nothing**. It reads as broken rather than as missing.

### Both done, and your reading was right on every point

The route is in `appNavGraph` now, as an `overview` slot rather than another twenty parameters: the
assembly is `OverviewScreen` in `:ui`, which takes the ten view models and about thirty dependencies,
and each platform hands the built composable in. Threading those through `appNavGraph` would have
taken it past sixty parameters for one screen.

That assembly is the 173 lines that were inline in `ComposeMainActivity`, and you were right that
none of it is Android - it is plugin state, objectives progress and glucose quality. Only five
callbacks are per platform: browser, directory picker, bring-to-front, recreate, and quit after a
failed authorization.

`AapsAppHost` can pass `overview = { OverviewScreen(...) }` and switch its start destination to
`AppRoute.Main.route`. Two things it needs that iOS did not have before: `Objectives` and
`PumpCommunicationStatus`, both now provided in `IosMainPluginsBindings`.

**The back arrow.** Fixed before this landed, by the shared home screen - and now properly, because
desktop opens on `AppRoute.Main.route` with a real root beneath it. `ShellHomeScreen` stays for the
moment as a way to reach screens the drawer does not list; it should go when it stops earning that.

**Drawer clicks were the next dead end**, found by running it: every item routed into a placeholder
because the `ElementType` to route mapping was also inline in the activity. That is shared now too -
see the note above on `ElementNavigation`.

iOS now starts on `IosHomeScreen` instead - a plain list of every argument-free route, marked as
scaffolding, which gives back somewhere to go and makes the other twenty-odd screens reachable for
testing at all. Desktop is welcome to the same thing, and if it wants one, the list belongs in shared
code rather than written twice. It should be deleted from both the day the Main route moves.
**That day came - see Done.**


## Done

- **`ObjectivesPlugin` in commonMain - done, and the casts are safe now.** The plugin moved to
  `plugins/constraints/src/commonMain/.../objectives/ObjectivesPlugin.kt` with the eleven
  objectives and their Compose content, so `Objectives` resolves from the shared
  `ClientGraphBindings` and iOS hosts the overview. The two Android pieces were lifted out rather
  than left behind: `SntpClient` is an interface with a shared `JvmSntpClient`, and `DurationText`
  is an interface because only Android has plural resources.

  The cast the request warned about is fixed too, and so are its two neighbours. `OverviewScreen`
  now uses `as?` for all three - objectives, BG source and pump - because each cast runs *before*
  the condition that decides whether its badge is drawn, so a hard cast turned "this platform has
  no such plugin" into a crash of the whole overview at compose time. A missing badge is the right
  failure.

- **The Nightscout registration is shared now.** `NSClientV3Plugin` is registered once in
  `ClientGraphBindings` (`:shared:clientbindings`) at `@IntKey(310)`, which both the iOS and
  desktop graphs include, so the iOS copy in `IosMainPluginsBindings` is gone. Two registrations
  remain rather than three: that one, and the one in `SyncPluginsBindings`, which stays because
  the Android graph does not include the client bindings. The plugin still cannot carry
  `@ContributesIntoMap` itself - the annotation processor fails on it.

- **`ShellHomeScreen` is deleted.** Both shells start on `AppRoute.Main.route` now, nothing
  navigated to `SHELL_HOME_ROUTE` on either of them, and the scaffolding said it should go the
  day the Main route moved. `:appshell` no longer carries it.

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

## Heads up: `LazyCalculationExecutor` moved into `:workflow`

Your copy in `ios/shell/.../di/` is deleted and `IosPlatformBindings` now points at
`app.aaps.workflow.LazyCalculationExecutor`. Nothing about the behaviour changed.

The reason is that the desktop target hit the identical cycle - `CalculationExecutor` ->
`PostCalculationRunner` -> `IobCobCalculator` -> `CalculationWorkflow` -> `CalculationExecutor` - for
the identical reason, no WorkManager to build the runners outside the graph. Your KDoc called it "an
iOS-only concern" that shared code should not carry; with two platforms hitting it, it is a
not-Android concern, and two identical copies in two shells is how the two quietly stop matching.

One small change: the runners are `() -> T` lambdas rather than `Provider<T>`, so `:workflow` keeps
no dependency on a DI library. Each shell passes `{ provider() }`. The KDoc explaining the cycle is
yours, kept as it was.

**Update 2026-10-13: Android uses this executor too.** On Android 16 the system stopped the
WorkManager calculation jobs once the app had used its job time quota, so the loop did not run for
long periods. Android now binds `AndroidCalculationExecutor` (`:workflow` androidMain), which is
`LazyCalculationExecutor` plus a partial wake lock per run, and the WorkManager executor and its two
workers are deleted. What reaches iOS and desktop:

- `CoroutineCalculationExecutor` catches a throw in a phase and logs it as a failed phase, instead of
  letting it reach the scope.
- The post phase (the loop) runs only after a prepare phase that reported `Success` or `Skipped`.
- `start` and `startPostOnly` are `suspend` now and register the run before they return, so starts
  replace each other in call order.
- `ClientGraphBindings` passes `wakeLock = null`.

Desktop now depends on the same 26 modules `:ios:shell` lists, so the plugin bindings are in its
graph too, and `DesktopMainPluginsBindings` is a near copy of `IosMainPluginsBindings`. If that pair
starts to drift it should become one container in a shared module that both graphs `@Includes` -
worth doing when a third thing needs it, not before.

## DONE: `SafetyPlugin` moved to commonMain

`IosSafetyPlugin` is deleted. The real plugin carries
`@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())` already, so it registers
itself on every platform - iOS and desktop now apply the actual limits (maximum bolus, maximum basal,
whether closed loop is allowed, whether the pump can do temp basals) instead of none.

Deleting the placeholder was not optional once the real one was in commonMain: both implement
`Safety`, `PluginStore.activeSafety` takes `.first()` of that list, and the placeholder applies no
constraints at all. Two entries would have made which one wins a matter of ordering.

Your note said "nothing in it is Android", which was nearly right - two things stood in the way, and
neither was in the import list:

- `override val rh: ResourceHelper`. `PluginBase` already declares `rh` as `TextResolver`, so the
  plugin was narrowing it for no reason; every call was `gs(TextRef)`, which `TextResolver` has.
- `java.lang.Double.valueOf(x).toInt()`, a boxing round trip that truncates exactly as `x.toInt()`.

Both are the same lesson as `ProtectionCheckImpl`: an import grep does not see a fully qualified name
or an over-specified supertype. Compiling for iOS is what finds them.

Verified: `:plugins:constraints:compileKotlinIosArm64`, `:ios:shell:compileKotlinIosArm64`,
`:app:assembleFullDebug`, and 115 `:plugins:constraints` tests.

## DONE: move `formatTemplate` so format strings work off Android

Moved to `core/interfaces/src/commonMain/.../resources/TextFormat.kt`, public rather than internal,
with its 18 tests. `GeneratedTextResolver` calls the same function, so the two paths cannot disagree.
`NumberFormat` resolved from `:core:interfaces` exactly as you expected.

Both actuals now substitute: `TextRefResource.ios.kt` and `.jvm.kt` do
`formatTemplate(TextRefValueRegistry.textOf(ref) ?: ref.name, ref.args)`.

**One thing your note did not cover, found while doing it.** The desktop actual was not merely
missing the arguments - it was still the original placeholder returning `ref.name`, so *no* Compose
string resolved on desktop even without arguments. The `TextResolver` path had been fixed and the
Compose path had not, which is the same "two paths disagree" problem, a step earlier. Fixed in the
same change; your iOS actual was already correct.

Gate: 102 tests in `:core:interfaces` (18 of them moved in), 794 in `:implementation`,
`:app:assembleFullDebug`, `:ios:shell:compileKotlinIosArm64`, desktop shell tests.

## DONE: `ConfigBuilderImpl` ported to commonMain

`IosConfigBuilder` is deleted. Your read was right on both counts: the plugin bookkeeping is ordinary
logic, and `exitApp` is the one part that will always need a platform half.

That half is now `AppExit` (`core/interfaces/.../configuration/AppExit.kt`), one method. The shared
part of `exitApp` - the bus event, the log line and the user entry - stays in `ConfigBuilderImpl`, so
only the final step is platform specific:

- `AndroidAppExit` keeps the `AlarmManager` + `PendingIntent` relaunch exactly as it was.
- `IosAppExit` **refuses**, and the KDoc says why it is a class rather than a one-liner:
  `exitProcess` exists on Kotlin/Native and would "work", so the tempting implementation ships a
  crash. Nothing is exited and the refusal is logged. Written from here only so the graph builds -
  if iOS should tell the user to close the app themselves, that belongs in this class.
- `DesktopAppExit` exits, and logs that it cannot relaunch itself.

Two things that were not in the import list, the same lesson as before:

- `rh: ResourceHelper` narrowed what `PluginBase` already declares as `TextResolver`.
- `p.javaClass.simpleName` builds a **stored preference key** (`"<type>_<simple name>"`), so it could
  not just be swapped. It is now one documented helper on `KClass.simpleName`, which gives the same
  string for a named class - which every plugin is - so existing installs keep their per-plugin
  enabled state. The two forms only diverge for anonymous classes.

Gate: `:plugins:configuration` 32 tests, `:app:assembleFullDebug`, `:ios:shell:compileKotlinIosArm64`,
desktop shell.

## Heads up: the home screen moved into `:appshell`

`IosHomeScreen` is deleted; `ShellHomeScreen` in `appshell/commonMain/.../navigation/` replaces it and
`AapsAppHost` now points at it. The route constant moved with it as `SHELL_HOME_ROUTE`. Nothing about
the behaviour changed, and the reasoning in your KDoc is kept.

Desktop hit the identical problem - settings as the start destination left the back arrow inert - so
this was about to be a second copy of the same 104 lines and the same list of 22 routes. That list is
exactly the kind of thing that stops matching once it exists twice: a screen added to `appNavGraph`
would be reachable on one platform and not the other, and nothing would fail.

Only the title changed, from "AAPS on iOS" to "AAPS", since both shells show it now.

## Heads up: objectives are commonMain, and drawer navigation is shared

Two moves that reach into files the iOS shell uses. Both were needed to make the overview work off
Android, and both follow the usual rule - the class moves, only the platform call is lifted out.

**Objectives.** `ObjectivesPlugin`, all eleven objectives and their Compose screens are now in
`plugins/constraints/commonMain`. Two Android dependencies came out:

- `SntpClient` is an interface in commonMain. The implementation is `JvmSntpClient` in a new
  `jvmSharedMain`, so Android and desktop share the actual SNTP exchange - the sockets were always
  plain JVM, only `SystemClock.elapsedRealtime()` was Android, and that is `System.nanoTime()` now.
  `IosSntpClient` was a placeholder until 2026-11-19, and that mattered more than it looked: objective 1
  could not start on iOS, so the full app's loop was always refused with "Objective 1 not started". It
  now sends the request over a POSIX UDP socket (`PosixSntpTransport`), with the shared `SntpPacket`
  arithmetic. Its KDoc says why it is not Apple's Network framework: three Kotlin/Native problems,
  one of which stopped the process on any network error.
- `DurationText` is the "3 d / 5 h / 20 min" text. Android keeps its plural resources;
  `PlainDurationText` in commonMain says it without plural forms, and `IosDurationText` delegates to
  it. If plurals ever land in `TextRef`, this interface should disappear.

`IosMainPluginsBindings` gained the two providers `:app` has - the ordered objectives list and the
unqualified `Objectives` - so the graph still builds.

**Drawer navigation.** `handleNavigationRequest` and the `ElementType` to route mapping are now
`appshell/commonMain/.../navigation/ElementNavigation.kt`. That mapping was inline in
`ComposeMainActivity` and nothing in it was Android, which is why desktop's drawer did nothing when
the overview first rendered there - every item routed into a placeholder. Only opening a CGM app and
closing the app are passed in.

`AapsAppHost` can now pass `onNavigate = { handleNavigationRequest(...) }` and get a working drawer.

**Worth knowing:** a unit test caught that the SNTP fake set `ntpTimeReference = 0`, which only
worked because `SystemClock.elapsedRealtime()` starts near zero in a unit test. `System.nanoTime()`
has an arbitrary origin, so the reference has to be read from the same clock. The production code was
right; the fake was leaning on the old clock's origin.

## Heads up: string owner registration is generated now

`IosStringOwners` is deleted. `GeneratedStringOwners` replaces it, emitted into `iosMain` by a new
`GenerateStringOwnerRegistryTask` from `StringOwnerModules.ALL` in buildSrc. `IosAppStartup` and
`IosPlatformBindings` call it exactly as before; `IosStringOwnersTest` still guards it and still
passes.

There were **four** hand written copies of the same list - `MainApp.registerStringOwners`, its copy in
`BaseTestApp`, yours, and the desktop one - and nothing compared them with each other or with the
build. They had drifted: the desktop copy carried five of sixteen modules, so the plugin list rendered
`objectives_shortname` instead of plugin names. Android's was split across two files as well, because
`ResourceHelperImpl` registers `coreUi` and `implementation` from its own start.

One list now, one task, three consumers. Android generates the `R.string` id variant so AAPT and every
translation keep working; iOS and desktop generate the English text variant. Adding a module with
strings is one line in `StringOwnerModules.ALL` plus `registerKeyStrings("xxx")` in its build file.

Both directions fail the build now. A module listed there but not generating strings breaks the
generated registry's imports. A module generating strings but missing from the list fails in
`registerKeyStrings`, which takes every value from its entry in the list.
