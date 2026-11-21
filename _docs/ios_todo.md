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

### FIXED: the alarm surface Android raises exists on iOS now
This entry used to say `IosUiInteraction.runAlarm` only logs, and that the full-screen alarm surface
was still missing. Both are wrong now, and reading them as current would mean redoing finished work.

`IosUiInteraction` is deleted. `runAlarm` raises a `PumpAlert` with `PumpAlertLifetime.EVENT` through
the one shared `UiInteractionImpl` in commonMain, so the app's own alarms are cards in the
notification list exactly like a pump's alert. The screen is `PumpAlertScreen` in `ui/commonMain`,
hosted on iOS as an overlay in `AapsAppHost` ahead of the protection gate - the same way
`ErrorActivity` ignores application protection on Android. Step 6 of `_docs/PUMP_DESIGN.md`,
2026-11-19.

Sound was always a separate question, and `_docs/ios_blockers.md` answers it: the ramping player
covers the foreground, a `UNNotificationSound` covers the background, and only breaking through
silent and Focus still needs the Critical Alerts entitlement.

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

### Time Sensitive alarms need the capability on each App ID
Phone builds sign with `Device.entitlements` (`AAPS_DEVICE_ENTITLEMENTS` in `Signing.xcconfig`), which
asks for Time Sensitive notifications so alarms come through Focus and Sleep. Without it iOS quietly
posts them as normal notifications. A wildcard development profile cannot carry the capability, so each
bundle id needs an explicit App ID with it. Automatic signing does that by itself - from Xcode with the
account signed in, or from `xcodebuild` with `-authenticationKeyPath/-ID/-IssuerID` and the App Store
Connect key - once the account holder has accepted the current Program License Agreement (on
2026-11-20 Apple refused with "PLA Update available"). Until then a local
`AAPS_DEVICE_ENTITLEMENTS =` in `Signing.local.xcconfig` builds without it. A command-line override
of that setting is not enough: the signing check does not see it.

---

## 2. Features not built yet

**The full app exists, and is not finished.** The `AAPS` target (2026-11-19) is the full app: it
runs the loop and drives a pump. `IosClientConfig` reads which build it is from the bundle
identifier, mirroring Android's `ConfigImpl`, and `AapsAppHost` merges the `@APS` and `@PumpDriver`
plugin buckets only there. The two client targets are unchanged. What the full app still lacks:

- **Nothing on iOS calls `KeepAliveRunner`.** So on the full app the pump status check, the
  pump-unreachable alarm and the zero temp basal retry do not run at all. It needs `runOnce` on every
  wake, and something has to wake it every five minutes - see the keep-alive section of
  `_docs/ios_blockers.md`. This is the first thing to do before the full app drives a real pump.
- `RoleBranch.prepare`/`commit` call the local lambda on the full app instead of sending to a master.
  That path has never run on iOS.
- `SignatureVerifierPlugin` is Android-only, so the full app's `@APS` bucket has two plugins of three.
  Probably right - there is no APK to verify - but nobody has decided it.
- **TEMPORARY: Nightscout is the full app's default glucose source.** iOS has no other source, and on
  Android the full app defaults to `DexcomPlugin`, which is Android-only - so without this the full app
  has no default source and its start stops. `NSClientSourcePlugin` claims the default on iOS through
  `config.platform == AppPlatform.Ios`. Remove that half of its `setDefault` once iOS has a glucose
  source of its own; `NSClientSourcePluginTest` pins it, so the removal is a decision, not an accident.

A Debug build of the full app may close the loop only in engineering mode, the same as Android. Put an
`engineering_mode` file in **On My iPhone → AAPS → extra**; add `emulate_carelevo` there to drive the
emulated patch instead of a real one. With no Nightscout there is no glucose, so pick **Random BG** as
the source - it is shared code now, on every platform. It is offered only on the virtual pump in
engineering mode, or with an `unfinished_mode` file, so with the emulated patch add that file too.

Seen on the simulator on 2026-11-19, with Random BG and the emulated patch: the wizard runs to the end,
objective 1 starts against `time.google.com`, and after switching to open loop OpenAPS SMB runs on each
reading. Accepting its suggestion sent a zero temp basal through the command queue to the patch, and the
patch reported it enacted. Not yet seen: closed loop (objective 6 and engineering mode), SMB, a bolus.
Two things to know when setting it up again:

- A fresh install starts with the loop **disabled** (`RM.DEFAULT_MODE`), as on Android. Choose open
  loop from the loop icon on the overview after objective 1 is started.
- A first run used to open the setup wizard **twice** on iOS: `AapsAppHost` had its own trigger from
  before `AapsAppRoot` opened the wizard on every platform. That trigger was removed on 2026-11-20, so
  `AapsAppRoot` is the only one now, as on Android and the desktop.

| What | Where | Note |
|---|---|---|
| ~~Autotune~~ | `AutotunePlugin` | Ported: common code, the iOS part is `IosAutotuneFileStore`. Not yet run on the simulator. |
| Document picker | `AapsAppHost`, three `reportNotReady` callbacks | Directory selection for exports. Shows "not ready on this platform yet" instead of doing nothing, which is the interim state, not the fix. |
| Quick wizard | `AapsAppHost`, `onExecuteQuickWizard` | Same interim message. |
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

**Bundle identifiers - decided, the way Trio does it.** The full app is always
`app.aaps.<teamid>.full`. A client somebody builds themselves is `app.aaps.<teamid>.client` or
`.client2`. `app.aaps.client` and `app.aaps.client2` stay as they are: they are the project's own
published apps, and their builds set `AAPS_CLIENT_ID_PREFIX = app.aaps` in `Signing.local.xcconfig`,
as `.github/workflows/ios-testflight.yml` does. Because the team id is part of the identifier, every
build needs a team now, the simulator too; `ios/app/Signing.xcconfig` says how to set one.

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

---

## Corrected on 2026-11-19

Two entries left section 4, because neither was true any more. Both were also repeated as "real bugs
the day `AAPSCLIENT` is false", which was wrong:

- **`IosSceneExpiryScheduler`** is a real implementation now, not a stub that only logs. A timer ends
  the scene on time while the app is alive, a sweep ends an overdue one when the app comes to the
  front, and the loop's own `endIfOverdue` is the backstop that does not depend on this process.
- **`IosAppStartup` does not exist any more.** The shared `AppStartSequence` runs on iOS from the app
  scope, off the main thread, and waits for every plugin's start job under a timeout.

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
