# iOS blockers

Things the iOS branch needs from the phone side. Written by the macOS session, for the Windows
session to pick up. Newest findings at the top of each list.

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

### 1. ProfileStoreObject - blocks `Preferences`, and so the whole AAPS theme on iOS

`implementation/src/androidMain/kotlin/app/aaps/implementation/profile/ProfileStoreObject.kt`

This is the one that matters most. `AapsTheme` reads `LocalPreferences`, so no real `Preferences`
means no AAPS theme on iOS - the Compose screen there runs on a bare `MaterialTheme` today. Every
other link in that chain (`PreferencesImpl`, `PersistenceLayerImpl`, `ProfileFunctionImpl`,
`ProfileUtilImpl`, `HardLimitsImpl`, `PluginStore`, `ConstraintsCheckerImpl`, `DetermineBasalResult`)
already resolves on iOS. This is the last one.

Three things needed:

- still `javax.inject`, so it needs the Dagger to Metro move
- `androidx.collection.ArrayMap` -> `LinkedHashMap` (the map is small and never a hot path)
- `ResourceHelper` -> `TextResolver`, with `R.string.x` -> `CoreUiStrings.x`

The last point is the same swap already done in `ProfileRepositoryImpl` and `ConstraintsCheckerImpl`
- see those two files for the pattern, including `TextRef.withArgs` for a format string.

### 2. The notification cluster - to delete a duplicate, not to unblock anything

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

### 3. AutotunePlugin - the last caller of the org.json adapters

`plugins/aps/src/androidMain/kotlin/app/aaps/plugins/aps/autotune/AutotunePlugin.kt`, 3 calls to
`blockFromJsonArray`.

`core/objects/src/androidMain/.../extensions/BlockJsonAdapters.kt` exists only to bridge `org.json`
to the kotlinx readers in `commonMain`, and its own doc says it goes away with the last caller.
After `ProfileRepositoryImpl` moved, this plugin is the only production caller left. The kotlinx
equivalents are `blockFromJson` and `targetBlockFromJson` in `BlockExtension.kt`.

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

- `ConstraintsCheckerImpl` - moved to `commonMain` (`a76cca9e41`)
- `ProfileRepositoryImpl` - moved to `commonMain`, off `org.json` (`3252f044b1`)
- `PluginStore` / `PluginPermissions` - split so the registry is no longer Android
- `DateUtilImpl`, `PreferencesImpl`, `ProfileUtilImpl` - moved to `commonMain`
