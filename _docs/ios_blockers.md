# iOS blockers

Things the iOS branch needs from the phone side. Written by the macOS session, for the Windows
session to pick up. Newest findings at the top of each list.

This file lives at `_docs/ios_blockers.md`. It arrived once as `_dcs/ios_blockers.md` and was moved -
please write it here, so both sessions look in the same place.

The pattern behind almost every entry: a class can only move to `commonMain` after it is off Dagger
and off Android types. The Metro migration has to land first, then the move is usually small.

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

## Open

Nothing open. The notification cluster was the last one - see Done.

## Known gaps on the iOS side

Not blockers, and not for the Windows session to fix. Listed so nobody is surprised by them.

- `IosSystemNotificationPlatform.setAudibleAlarm` only logs. An iOS notification carries its own
  sound, and reposting one every time the owner is recomputed would re-alert the user. A real
  ramping alarm needs a critical alert entitlement or an audio session.
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
- `ConstraintsCheckerImpl` - moved to `commonMain` (`a76cca9e41`)
- `ProfileRepositoryImpl` - moved to `commonMain`, off `org.json` (`3252f044b1`)
- `PluginStore` / `PluginPermissions` - split so the registry is no longer Android
- `DateUtilImpl`, `PreferencesImpl`, `ProfileUtilImpl` - moved to `commonMain`
