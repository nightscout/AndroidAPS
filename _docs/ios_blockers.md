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

### 1. The notification cluster - to delete a duplicate, not to unblock anything

`implementation/src/androidMain/kotlin/app/aaps/implementation/notifications/NotificationManagerImpl.kt`
and, in `implementation/src/androidMain/kotlin/app/aaps/implementation/androidNotification/`,
`AlarmNotificationManager`, `AlarmSoundPlayerImpl` and `NotificationHolderImpl`. All four are still
`javax.inject`.

Nothing is blocked by this - iOS has its own binding already - but there are two registries now, and
they should become one. When these move to Metro:

- delete `NotificationManagerImpl` and bind `CommonNotificationManager`
  (`implementation/src/commonMain/kotlin/app/aaps/implementation/notifications/`) instead. It already
  holds all the shared logic: the list and its ordering, `allowMultiple` replacement, expiry and
  `validityCheck`, both `dismiss` overloads, `muteAllAlarms`, and the alarm sound owner handoff.
  18 tests cover it in `implementation/src/commonTest/.../CommonNotificationManagerTest.kt`, and
  they run on the JVM and on the iOS simulator.
- keep the Android specific half - the channel, the dismiss `BroadcastReceiver`,
  `NotificationCompat` and the `PendingIntent` - as an Android `SystemNotificationPlatform`
  (`core/interfaces/src/commonMain/.../notifications/SystemNotificationPlatform.kt`). Drive
  `AlarmSoundPlayer` from its `setAudibleAlarm`, which is called with the key of the alarm that owns
  the sound, or null for silence.

`CommonNotificationManager` deliberately has **no** `@ContributesBinding`, so that it cannot clash
with the Android binding while both exist. Add the annotation when the Android one goes away.

**Windows session, 2026-08-27: started this and stopped, because the interface cannot express what
Android does today.** `CommonNotificationManager.post` called `platform.show(...)` unconditionally.
`NotificationManagerImpl` does not - it picks one of three paths:

1. `level == URGENT && sound != null` -> `alarmNotificationManager.postSilentAlarmNotification(...)`,
   a **silent** heads-up notification, because the ramping audio is owned by `AlarmSoundPlayer`
2. else if `preferences.get(BooleanKey.AlertUrgentAsAndroidNotification) && actions.isEmpty()` ->
   the ordinary `NotificationCompat` notification
3. else nothing at all

**macOS session: agreed, and fixed - the seam now carries the whole notification.**

```kotlin
fun show(notification: AapsNotification, title: String)
```

Rather than adding `sound` and `hasActions` as separate parameters, which is what was suggested,
`AapsNotification` is passed whole. It is `commonMain` already and is the registry's own currency, so
Android can express all three paths, and the signature will not have to be widened a third time when
something needs `date` or `id.category`. `title` stays separate because resolving it needs a
`TextResolver`, which a platform implementation should not have to carry. An implementation may
decide to show nothing at all, which is path 3.

`CommonNotificationManagerTest` has a test - `the platform is given the sound and the actions` -
whose only job is to keep those two fields reaching the platform, so this cannot regress quietly.
iOS ignores them and says why in `IosSystemNotificationPlatform.show`.

The remaining work is unchanged: take the four classes off `javax.inject`, then delete
`NotificationManagerImpl` in favour of `CommonNotificationManager` plus an Android
`SystemNotificationPlatform`. `AlarmSoundPlayerImpl` and `NotificationHolderImpl` are already off
javax; `NotificationManagerImpl` and `AlarmNotificationManager` are not.

## Known gaps on the iOS side

Not blockers, and not for the Windows session to fix. Listed so nobody is surprised by them.

- `IosSystemNotificationPlatform.setAudibleAlarm` only logs. An iOS notification carries its own
  sound, and reposting one every time the owner is recomputed would re-alert the user. A real
  ramping alarm needs a critical alert entitlement or an audio session.
- `IosSystemNotificationPlatform.onDismissed` is not wired. It needs a
  `UNUserNotificationCenterDelegate` set on the shared centre by the app during start up, which that
  class cannot own without two of them fighting over the slot. A notification cleared on the lock
  screen therefore stays in the in-app list.

## Done

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
