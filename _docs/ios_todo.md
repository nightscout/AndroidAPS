# iOS: what is left to build

Replaces the open half of `_docs/ios_blockers.md`. That file was a handoff channel between two
sessions on two branches; the branches are merged into `dev` now and the sessions message each
other directly, so what remains useful is this list.

Every item below was checked against the tree on **2026-09-23** at `d45d3c1860`; the scene and
socket items were re-checked on **2026-10-05** at `74aae7d98a`. Symbols, not line numbers - line
numbers rot. Re-check before starting one: three items in the old file were already done when it was
audited, one said the opposite of what the code did, and the scene item below was wrong twice over -
it described a safety gap that the client/master split makes unreachable.

The iOS side compiles for device, links `AapsShared.framework` and runs **730 simulator tests, 0
failures**. Nothing here is a build problem. These are features that are missing and deliberate costs
that need a decision.

---

## 1. A user would notice these

### Full-screen alarms only log
`IosUiInteraction.runAlarm` calls the logger and returns. **Not the same thing as
`setAudibleAlarm`**, which does play sound through `IosAlarmSoundPlayer` on `AVAudioPlayer` - the two
were conflated before and the old file claimed iOS alarms were silent for ten days after they worked.
What is missing is the full-screen alarm surface Android raises, not the sound.

### A subscribe that never gets its ack leaves the client silently stalled
Measured on 2026-10-05 against Nightscout 15.0.7, on the simulator and on an iPhone 15 Pro Max. The
earlier version of this item guessed at the behaviour; these are the readings.

**What is actually broken.** `SwiftNsSocket.emitWithAck` uses `timingOut(after: 0)`, which means no
timeout at all: if the `subscribe` ack never arrives the closure never runs, so
`NsConnectHandler.onConnectStorage` never clears `initialLoadFinished`, never calls `executeLoop`,
and never reports a result. The socket stays up and answers pings while no data arrives, and nothing
retries. This is the one state found that does not heal itself - every other drop recovered within a
second. A timeout alone only makes the stall visible; recovery needs a resubscribe or a socket
restart.

**What is cosmetic rather than broken.** socket.io-client-swift emits `reconnect`, never
`disconnect`, when a transport drops - confirmed three times (twice on a phone when an interface went
down, once by closing the engine deliberately). `NsSocket.EVENT_DISCONNECT` is `"disconnect"`, so
`onDisconnectStorage` does not run and `SocketNsConnection._connected` keeps its old value.
`disconnect storage event` was logged **zero** times across every run. It stays invisible to the user
because `NSClientV3Plugin.status` tests `isAllowed` - network reachability from `receiverDelegate` -
before it looks at the socket, so a real outage is reported honestly by that signal instead. The
stale flag only shows through when the network is up and the socket is dead. Worth mapping `reconnect`
onto the disconnect path so the flag is honest on its own evidence, but it is not the user-visible
bug it was once written up as.

**Suspension costs liveness, not data.** A phone left suspended for three hours produced no pushes
and no socket events at all; on resume the connectivity flow called `setClient()`, the socket
reconnected, and 36 glucose values and 37 treatments were backfilled within three seconds from the
correct high-water mark. So the shared catch-up logic works. `IosForegroundWatcher` still **exists but
nothing constructs it** (only `SocketNsConnection` mentions it, in a comment); wiring it would shorten
the gap, not create the recovery. Read its KDoc first: closing the socket on backgrounding is only
safe if something reopens it.

**Not worth copying from the issue:** `.forceWebsockets(true)`, dropping `.compress`, and sharing one
`SocketManager` were each measured for 180 s against a healthy server and were indistinguishable from
the current code - 1 connect, 1 upgrade, 7 pings, 0 reconnects. Nightscout behind nginx negotiates
`permessage-deflate` happily. Forcing websockets would also cost the polling fallback that Android
keeps. See issue 5185.

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

- **`IosSceneExpiryScheduler.schedule` only logs an error.** It cannot be reached. `config.AAPSCLIENT`
  is hardcoded `true` in `IosClientConfig`, and `RoleBranch.prepare`/`commit` send the command to the
  master on a client instead of calling the local lambda, while `SceneActions.stop` goes through
  `ClientControlActionDispatcher`. So `SceneExecutor.activate` - the only caller of `schedule` - runs
  on the master, which schedules expiry with its own working scheduler. The client runs
  `validateActivation` locally and nothing else, and that is a pure query. This was written up twice
  as "a timed scene never ends on iOS", which would be true only if iOS ever shipped as a master.
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

## Corrected on 2026-10-05

- **Scene expiry was never a gap.** Verified through `IosClientConfig.AAPSCLIENT`, `RoleBranch` and
  `SceneActionsImpl`: activation is a round-trip to the master on a client. Moved to section 4 with
  the reasoning, so it does not get re-raised a third time.
- **The socket item was guesswork** and is now measurements. The claim that recovery "rests on
  socket.io-client-swift's own reconnect surviving suspension, which nobody has verified" is
  answered: it does not survive suspension, and recovery comes from the connectivity flow instead.
- **The status line does not lie during an outage.** `isAllowed` is checked before the socket, so the
  stale `_connected` flag is masked in every case a user can see except "network up, socket dead".

## Corrected while writing this (2026-09-23)

- Alarms are **not** silent on iOS. Fixed in `e1fa702fc3`; the old file said otherwise until
  2026-09-21.
- `PrefsFileInfo.listPreferenceFiles` is **implemented** on iOS (`IosPrefsFileInfo`, through
  `lister.list()`). The old file lists it as an empty stub.
- `ExportPasswordDataStore`, `ImportExportPrefs` and `IobCobCalculator` were all done while still
  listed as open.
