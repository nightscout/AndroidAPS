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
  18 tests cover it in `implementation/src/androidHostTest/.../CommonNotificationManagerTest.kt`.
- keep the Android specific half - the channel, the dismiss `BroadcastReceiver`,
  `NotificationCompat` and the `PendingIntent` - as an Android `SystemNotificationPlatform`
  (`core/interfaces/src/commonMain/.../notifications/SystemNotificationPlatform.kt`). Drive
  `AlarmSoundPlayer` from its `setAudibleAlarm`, which is called with the key of the alarm that owns
  the sound, or null for silence.

`CommonNotificationManager` deliberately has **no** `@ContributesBinding`, so that it cannot clash
with the Android binding while both exist. Add the annotation when the Android one goes away.

**Windows session, 2026-08-27: started this and stopped, because the interface cannot express what
Android does today.** `CommonNotificationManager.post` calls `platform.show(...)` unconditionally.
`NotificationManagerImpl` does not - it picks one of three paths:

1. `level == URGENT && sound != null` -> `alarmNotificationManager.postSilentAlarmNotification(...)`,
   a **silent** heads-up notification, because the ramping audio is owned by `AlarmSoundPlayer`
2. else if `preferences.get(BooleanKey.AlertUrgentAsAndroidNotification) && actions.isEmpty()` ->
   the ordinary `NotificationCompat` notification
3. else nothing at all

`show(instanceKey, title, text, urgent)` carries neither `sound` nor whether there are actions, so an
Android implementation of it cannot reproduce 1 or 2 - it would either double-notify an alarm that is
supposed to be silent, or ignore the user's preference. This is alarm behaviour, so it should not be
guessed at.

Suggestion: extend the interface to `show(instanceKey, title, text, urgent, sound: AlarmSound?,
hasActions: Boolean)` and let each platform decide. iOS ignores the two new parameters. Whoever owns
the iOS side should confirm that is the shape they want before it is built.

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
- `ConstraintsCheckerImpl` - moved to `commonMain` (`a76cca9e41`)
- `ProfileRepositoryImpl` - moved to `commonMain`, off `org.json` (`3252f044b1`)
- `PluginStore` / `PluginPermissions` - split so the registry is no longer Android
- `DateUtilImpl`, `PreferencesImpl`, `ProfileUtilImpl` - moved to `commonMain`
