# iOS: what is left to build

Replaces the open half of `_docs/ios_blockers.md`. That file was a handoff channel between two
sessions on two branches; the branches are merged into `dev` now and the sessions message each
other directly, so what remains useful is this list.

Every item below was checked against the tree on **2026-09-23** at `d45d3c1860`. Symbols, not line
numbers - line numbers rot. Re-check before starting one: three items in the old file were already
done when it was audited, and one said the opposite of what the code did.

The iOS side compiles for device, links `AapsShared.framework` and runs **720 simulator tests, 0
failures**. Nothing here is a build problem. These are features that are missing, deliberate costs
that need a decision, and one safety gap.

---

## 1. A user would notice these

### Full-screen alarms only log
`IosUiInteraction.runAlarm` calls the logger and returns. **Not the same thing as
`setAudibleAlarm`**, which does play sound through `IosAlarmSoundPlayer` on `AVAudioPlayer` - the two
were conflated before and the old file claimed iOS alarms were silent for ten days after they worked.
What is missing is the full-screen alarm surface Android raises, not the sound.

### A timed scene never ends
`IosSceneExpiryScheduler.schedule` logs at error instead of scheduling. Scenes compile and the editor
works, so this looks finished. `SceneExpiryRunner` reverts two actions at expiry whose effect does not
stop on its own, so on iOS a timed scene stays active indefinitely.

### A dropped Nightscout socket may never be noticed
The catch-up logic is shared and correct - a connect clears `initialLoadFinished` and refetches from
the high-water mark. What iOS lacks is anything that makes the connect happen: the process suspends in
the background, `IosForegroundWatcher` **exists but nothing constructs it** (only `SocketNsConnection`
mentions it, in a comment), and the connectivity trigger fires on the allowed-verdict flip rather than
on network changes. Recovery rests on socket.io-client-swift's own reconnect surviving suspension,
which nobody has verified. A shared watchdog - force a load when `connected` has been false for longer
than N - would close this on every platform, which is the better shape.

Read `IosForegroundWatcher`'s KDoc before wiring it: closing the socket on backgrounding is only safe
if something reopens it.

### The pairing PIN is not protected from screenshots
`blockScreenshotsWhileVisible` returns **false** on iOS on purpose - Apple has no `FLAG_SECURE`, and a
silent no-op would imply protection that is not there. That PIN wraps the shared secret a paired
client signs commands with, so a screenshot in a gallery or a cloud backup is a real exposure. Two
things help and neither is a product decision: cover the window on `willResignActive` so the
app-switcher snapshot does not hold the PIN, and show the warning the screen can already render
(`screenshotsBlocked` in `AuthorizedClientsScreen`) when the value is false.

---

## 2. Features not built yet

| What | Where | Note |
|---|---|---|
| Autotune | `IosAutotune` | Bound but does nothing. It is portable Kotlin arithmetic over treatment history, not platform code, so port it rather than stub it. Not reachable from the iOS UI today, so nothing lies yet - it would the moment autotune reaches the shared nav graph. |
| Document picker | `AapsAppHost`, three `reportNotReady` callbacks | Directory selection for exports. Shows "not ready on this platform yet" instead of doing nothing, which is the interim state, not the fix. |
| Quick wizard | `AapsAppHost`, `onExecuteQuickWizard` | Same interim message. |
| Send logs | `IosMaintenance.executeSendLogs` | Needs a mail composer. Throws today and the UI reports it. |
| Notification actions | `IosSystemNotificationPlatform` | Buttons need a `UNNotificationCategory` per resolved label set, registered before posting and routed back by instance key. `IosLoopNotifier` already does exactly this for "ignore for N minutes" and is the worked example. Android's approach cannot be copied - its actions are `PendingIntent`s. |

---

## 3. Decisions, not code

**Critical Alerts entitlement.** Only this lets a notification break through silent and Focus while
the app is *not* running. Apple grants it to medical apps on application. Sound while the app *is*
running already works and needs no entitlement.

**Roaming.** `IosReceiverStatusStore.roaming` is always false because iOS exposes no roaming state
anywhere public. `ReceiverDelegate` reads it, so **a user who turned off "sync while roaming" still
syncs over cellular abroad**. False is the least bad of two wrong answers - true would stop cellular
sync working for everyone, everywhere. The options are a manual preference for travelling, or hiding
the cellular-sync option on iOS. Both are product calls.

**Secure Enclave.** `IosSecureEncrypt` keeps its AES key in the Keychain as `ThisDeviceOnly`, not in
the Enclave, which holds EC keys rather than the AES key wanted here. Wrapping the AES key with an
Enclave EC key would close the gap and is a larger change.

---

## 4. Deliberate - do not "fix" these

They look like gaps in the code and are not. Each has been proposed as a bug at least once.

- **`IosLocationPermissions` returns an empty list.** `PermissionGroup.permissions` holds Android
  permission strings; iOS asks at the point of use through `CLLocationManager`. `AutomationRuntime`
  reporting nothing missing on iOS is correct. `IosLocationServiceController` still requests it.
- **No `IosBtConnectionSource`.** `AutomationRuntime` in commonMain contributes `BtConnectionSource`
  itself; a second binding failed the graph. The shared one gives the right answer because nothing
  posts `EventBTChange` on iOS, so the list stays empty without a class to keep in step.
- **`NsSocketFactory` has no Kotlin implementation on iOS.** It is `SwiftNsSocketFactory` on
  socket.io-client-swift, entering as a factory parameter at start up, so both platforms use the same
  project's client and speak to Nightscout identically.
- **`IosReceiverStatusStore.ssid` is always empty.** Reading it needs the Access WiFi Information
  entitlement plus location permission. A Wi-Fi SSID automation trigger can be configured and will
  never match - the same shape as the Bluetooth trigger.
- **`IosAppStartup.run()` does not wait for plugin start jobs**, where Android does. `run()` is on the
  iOS main thread, so waiting would be a `runBlocking` gated on the slowest driver's `onStart`.
  Whoever wires the iOS splash should move it off the main thread first, then wait.

---

## Corrected while writing this

- Alarms are **not** silent on iOS. Fixed in `e1fa702fc3`; the old file said otherwise until
  2026-09-21.
- `PrefsFileInfo.listPreferenceFiles` is **implemented** on iOS (`IosPrefsFileInfo`, through
  `lister.list()`). The old file lists it as an empty stub.
- `ExportPasswordDataStore`, `ImportExportPrefs` and `IobCobCalculator` were all done while still
  listed as open.
