# Pump driver design

How a pump driver does the parts that every driver has. `PUMP_DRIVER_ACCEPTANCE.md` says what a driver must pass;
this file says how to build it. When the two disagree, the acceptance file wins and this file is fixed.

**Status:** the driver contract (section 1) describes the code as it is. Pump alerts (section 2) are agreed
(2026-11-19); the core, the Android side and Carelevo are built (steps 1 and 2 of section 5) but not yet tested on a
device, and the iOS side and the other drivers are not built. The decisions are in section 7.

## 1. The driver contract

### What a driver implements

| What | How | Notes |
|---|---|---|
| `Pump` | extend `PumpPluginBase` | `PumpPluginBase` adds the plugin, its preferences and the command queue. `PumpPluginAbstract` in `pump/common` is Android only; a driver in shared code cannot use it. |
| Its own pump steps | `CustomCommand` classes | Clear an alarm, the steps of an activation. The queue runs them through `Pump.executeCustomCommand`, on the queue's thread. |
| Its own buttons, optional | `getCustomActions` and `executeCustomAction` | Buttons in the manage sheet. The button calls the driver directly, so `executeCustomAction` only queues a `CustomCommand`; it never talks to the pump itself. No driver has one today; kept for drivers in progress. |
| Its screen | `ComposablePluginContent`, set with `PluginDescription.composeContent` | |
| Its ports | interfaces in its own commonMain | Platform work, implemented per platform (section 3). |
| Its emulator | `PumpEmulatorControl`, a key in `PumpEmulators`, a `<Pump>Accessors` class | Acceptance E1 to E6, the `pump-emulator-scenarios` skill. |
| Its registration | `@PumpDriver` and a number key, in a binding container | Section 3. |
| Limits of the pump | `PluginConstraints`, only when the pump has them | Maximum bolus or basal (Combo, Dana, Diaconn, Insight). |

### What a driver uses

- `CommandQueue`: all talk to the pump goes through it (acceptance D2).
- `PumpSync`: records what the pump did, and `connectNewPump` when the pump is removed (D9). `PumpSuspendSync` records
  a stop the user made on the pump.
- `DetailedBolusInfoStorage` and `TemporaryBasalStorage`: match a record from the pump history to what AAPS asked for.
- `PumpAlerts` for every pump alert (section 2). `NotificationManager` only for plain cards that are not alerts.
- `BleCentral` with `QueuedBleLink` for Bluetooth. `BleTransport` is version 1 and is deleted when the last driver
  has moved.
- `BlePreCheck` with `BlePreCheckHost` before the driver talks to the pump: Bluetooth there, on and allowed. When it is
  off, the host asks the system to turn it on (`rememberBluetoothEnableRequest` in `:core:ui`) and goes on after a
  yes; only a no, or a platform that cannot ask (iOS), ends in the message. A driver that needs Bluetooth on from
  somewhere else, as Carelevo when its Bluetooth-off alarm is cleared, uses the same request. It has no code of its
  own for this.
- `Preferences` with generated keys, `TextRef` strings, `TextResolver`, and `PumpEnactResult` for every result.

Not the driver's job: `PumpStatusProvider` (the status text for SMS and the watch), `PumpWithConcentration` (the
wrapper around the active pump), the unreachable alarm, the loop.

### Rules

- **No new core interface for one pump.** `Dana`, `Medtrum`, `Insight`, `Diaconn`, `OmnipodDash`, `OmnipodEros` and
  `VirtualPump` let core call into one driver. A new driver uses a `CustomCommand` instead.
- **A value the driver does not have is null** (reservoir, battery), never a number made up to fill the field.
- **A driver setting that core reads stays a member of `Pump`, not of `PumpDescription`.** `PumpDescription` is filled
  from the `PumpType` (`fillFor`). A client does that with the master's type for its virtual pump, and the
  concentration wrapper works on a copy. A user setting kept there would come from the type, not from the setting. This
  holds for `isFakingTempsByExtendedBoluses` (DanaR and Insight settings, sent to clients), `setNeutralTempAtFullHour`
  (Medtronic) and `isBatteryChangeLoggingEnabled` (Diaconn, Eros).
- **The virtual pump behaves like the type it emulates**, on a client too. Whatever core decides from the type must
  give the right answer for a virtual pump that has the type and nothing else.
- **The battery level is shown only when `Pump.reportsBatteryLevel()`.** By default the `PumpType` says it
  (`supportBatteryLevel`): the virtual pump always reports 50 %, and Combo reports 0, 25 or 100 from a battery state,
  and neither is a real level. A driver with a level its type does not have overrides it (Eros, with the RileyLink
  level turned on). Core does not name a pump for this.

## 2. Pump alerts

### Today

Every driver builds its own alarm path from three core pieces:

- `UiInteraction.runAlarm(status, title, sound)`: the full-screen alarm. On Android it plays its sound as
  `AlarmSoundPlayer.OWNER_FULLSCREEN`, outside the notification list. On iOS it only writes a log line.
- `NotificationManager.post(...)`: a card in the notification list. A card plays its sound (`OWNER_INTERNAL`) only at
  level URGENT; a sound on any other level is never played.
- Own pieces: a notification channel (Carelevo, Insight), an alarm screen and vibration (Insight), a foreground check
  (Carelevo), an alarm table in storage (Carelevo, EOPatch, Medtrum, Omnipod pod state), announcements written by the
  driver (Dana, Medtrum, Omnipod) or by `runAlarm` (Android, when the build runs APS).

The result is that each driver chooses a level and a sound on its own, and the same pump event is shown differently
by each driver. Some use `runAlarm`, some an URGENT card with a sound, some both for one event, some a card with no
sound. The pieces also work against each other:

- `AlarmSoundPlayer` plays one sound at a time, and `play` stops whatever was playing. The full-screen alarm and a card
  alarm take the player from each other, and stopping one does not bring the other back.
- The OK and Mute buttons of the full-screen alarm cannot name their own alarm, so they call `muteAllAlarms()`, which
  removes every URGENT card that has a sound. Carelevo and EOPatch keep their cards below URGENT to avoid that, and
  lose the card sound with it.
- `runAlarm(sound = null)` is documented as silent, but Android plays `AlarmSound.ERROR` for it.
- On iOS nothing can stop a sound played as `OWNER_FULLSCREEN` (see `_docs/ios_blockers.md`, "Before anyone writes
  `IosUiInteraction`"), so the Android shape cannot simply be copied.

What is the same in every driver: ring, show, silence, cancel, tap opens the app, tell the watch, write an
announcement. What is the driver's own: how an alert is identified, its text, how bad it is, the pump-side step
(clear, confirm, silence, resume, discard), and when the pump says it is gone.

### Design: `PumpAlerts` in core

The driver says **what** is wrong and **how bad** it is. The core decides how it is shown, how it sounds, and how it
is silenced. A driver never passes a notification level or a sound.

```kotlin
// core/interfaces commonMain, package app.aaps.core.interfaces.pump.alerts

enum class PumpAlertSeverity {
    /** Insulin delivery has stopped or is not safe. Rings and opens the alarm screen until the user acts. */
    CRITICAL,
    /** The user must act soon: low reservoir, low battery, the pump or patch expires. A card and one sound. */
    WARNING,
    /** For information only. A card, no sound. */
    INFO
}

/** A step on the pump that the user can start from the alert, for example "clear the alarm on the patch". */
class PumpAlertAction(
    val label: TextRef,
    /** Runs through the command queue. Returns true when the pump confirmed it. */
    val run: suspend () -> Boolean
)

class PumpAlert(
    /** The driver that raised it, for example "carelevo". */
    val source: String,
    /** Stable id inside the source. Raising the same key again updates the alert; it does not ring again. */
    val key: String,
    val severity: PumpAlertSeverity,
    /** Resources, not resolved text, so the core resolves them when it shows them. A pump value goes in as an argument. */
    val title: TextRef,
    val text: TextRef? = null,
    /** When the pump raised it. A driver that raises it again after a restart passes the same time. */
    val raisedAt: Long,
    val action: PumpAlertAction? = null
)

interface PumpAlerts {
    val active: StateFlow<List<PumpAlert>>

    /** The pump reports this alert. */
    fun raise(alert: PumpAlert)

    /** The pump says it is gone, or the user's action cleared it. Stops the sound, removes the card. */
    fun resolve(source: String, key: String)

    /** The pump is removed, discarded or stopped. */
    fun resolveAll(source: String)

    /** Called by the alarm screen and the card, never by a driver. The alert stays; the sound stops for 30 minutes. */
    fun silence(source: String, key: String)
    fun snooze(source: String, key: String, minutes: Int)

    /** Called by the alarm screen: run the alert's action, as its button on the card does. It also silences. */
    fun act(source: String, key: String)
}

/** What a platform does beyond the notification list, which already owns the card and the sound. */
interface PumpAlertPlatform {
    fun showAlarmScreen(alert: PumpAlert)      // CRITICAL rings: the alarm screen, also from the background
    fun chime(alert: PumpAlert)                // WARNING: a system notification with one sound
    fun snoozed(alert: PumpAlert, until: Long) // a platform that cannot run a timer in the background schedules the ring
    fun withdrawn(source: String, key: String) // the alert ended or rings again: take back what chime/snoozed left
}
```

`PumpAlertsImpl` (`:implementation` commonMain) is not contributed from shared code: a platform binds it once it has a
`PumpAlertPlatform` (Android: `PumpAlertsAndroidBindings`). The end of a silence is checked against the clock
(`checkSilences`), by a timer while the app runs and by the platform's wake (Android: an exact alarm to
`PumpAlertWakeReceiver`; iOS: a scheduled local notification).

**Why not `UiInteraction`.** Pump alerts have an identity, a pump-side action that must go through the queue, a
lifetime tied to the pump (removal, discard, restart) and a test seam for the scenario suite. `UiInteraction` has none
of that, and it also carries `mainActivity` and `errorHelperActivity`, which only mean something on Android. It stays
for the callers that are not pumps until those move too.

**What the core does** (one shared implementation in `:implementation` commonMain):

- **One sound owner.** Each CRITICAL alert is an URGENT card with a sound in the notification list, and that list is
  the only thing that starts and stops alarm sound. The alarm screen is a view of the sounding card, not a second
  player. Silence stops the sound of that one alert and leaves the card; it never removes other cards.
- **The alarm screen.** CRITICAL opens it: on Android the existing full-screen path (wakes the screen, works from the
  background) without its own sound; on iOS an alarm screen in the app.
- **WARNING** is a card plus one notification sound. It does not repeat and opens no alarm screen.
- **Background.** On Android the system notification as today. On iOS, while the app is not in front, a system
  notification with a sound and the time-sensitive level (no critical alerts: that needs Apple's entitlement). No
  driver checks the foreground for this.
- **One announcement** per new alert (`source`, `key`, `raisedAt`). Drivers stop writing their own for alerts.
- **The watch** gets ringing and stopped, as today.
- **The action.** The card and the alarm screen show the action button. The core shows that it runs and what came
  back. On false the alert stays and the user sees that the pump did not confirm.
- **Silence.** Silence is a snooze of 30 minutes; `snooze` takes another length. When the window ends and the alert
  is still active, it rings again. Any other way the card goes away while the alert is still active (OK on another
  alarm screen, a swipe) counts as a silence, and the card comes back without sound.
- **Restart.** The core does not store alerts: the pump and the driver are the truth. It stores only the silences
  (`source`, `key`, `raisedAt`, until when), so an alert raised again after a restart keeps the rest of its window.
  `raisedAt` is part of it: a new occurrence of the same condition has a new time and rings at once.

**What the driver does:**

- Raise each pump alert with a stable key, a severity, the text and the time the pump raised it.
- Raise again from `onStart` what the pump still holds (acceptance D1).
- Resolve when the pump says it is gone. `resolveAll` when the pump is removed or discarded (acceptance D9) and in
  `onStop`.
- Give the pump-side step as the action, and run it through the queue (acceptance D2).
- Keep messages that are not pump alerts (wrong driver, wrong password, the time was updated) as plain cards.

### Platforms

| | Android | iOS | Desktop |
|---|---|---|---|
| Sound | notification list, `OWNER_INTERNAL` | notification list, `IosAlarmSoundPlayer` | no pump drivers, not bound |
| Alarm screen | full-screen path, sound from the list | in-app alarm screen | - |
| App in the background | system notification, full-screen intent | system notification with sound, time-sensitive | - |

## 3. A driver in shared code (KMP)

How to move a module is in the `kmp-module-flip` skill. The pump-specific rules, from moving Carelevo:

- The plugin, its screens and its view models are in commonMain. androidMain holds only Android implementations of
  ports.
- A port is honoured on every target that gets one. A target that cannot honour it has no such pump. So the driver
  is registered by a binding container in commonMain that is **not** contributed (`CarelevoRegistration`), and each
  platform includes it once its ports exist (`CarelevoAndroidRegistration`). Contributing from commonMain pulls the
  driver into every graph, ready or not.
- Bluetooth state comes from `BleCentral.adapterState`, not from a broadcast receiver. Asking the user to turn
  Bluetooth on is the shared `rememberBluetoothEnableRequest` of `:core:ui`, which returns null where the platform
  cannot ask (section 1).
- Images are Compose Multiplatform resources. The iOS app build must copy them into the app bundle, or the screen
  that uses one fails when it opens.
- No foreground check and no notification port for alerts: that is `PumpAlerts`.

## 4. What each driver changes for pump alerts

| Driver | Change |
|---|---|
| Carelevo | `handleAlarms` and `CarelevoAlarmNotifier` raise and resolve. Clear and discard become the action. Delete `CarelevoSystemNotifier`. `CarelevoAppForeground` stays only for the alarm refresh when the app comes to the front. |
| EOPatch | `AlarmManager`/`AlarmRegistry` raise and resolve. The actions of `AlarmProcess` run through the queue. `restartAll` becomes the raise on start. |
| Equil | The alarm cards raise. The rule that old history alarms do not ring stays in the driver (raise them as INFO). No action: the pump has no clear command. |
| Medtrum | The alarm cards in `MedtrumService` raise; a pump state change resolves. `clearAlarms` becomes the action. |
| Omnipod Dash, Eros | Pod fault, active pod alerts and uncertain delivery raise. Silence or acknowledge alerts becomes the action. `showErrorDialog` raises. The per-type sound settings choose the severity. Eros automatic acknowledge stays. |
| Insight | The alert service, alert screen, channel and vibration go. Alerts raise with Confirm as the action; snooze sends the snooze to the pump too. |
| Combo | `notifyAboutComboAlert` raises: errors CRITICAL, warnings WARNING. The automatic CHECK press for warnings stays. |
| DanaRS, DanaR | `DanaRSAlarmReporter`, `MsgError` and the large time difference raise. The bolus block raises. |
| Diaconn | The report packets raise, with the severity taken from the report's grade. |
| Medtronic | The one `runAlarm` raises. The configuration cards stay plain cards. |
| Virtual | Nothing. |

## 5. Order of work

1. Core: `PumpAlerts`, its shared implementation, the notification list changes (a title on the card, silence and
   snooze for one card, pump alerts never removed by `muteAllAlarms`), the Android alarm screen as a view of the card,
   the iOS alarm screen and background sound. Tests.
2. Carelevo, because it is shared code already and iOS needs it first. Real-patch test.
3. The other drivers, one change each, each with a real-pump test (see the `pump-emulator-scenarios` skill). Not a
   task of its own (decided 2026-11-19): a driver moves to pump alerts, to Bluetooth version 2 and to shared code
   when it is brought to the acceptance file's current requirements (A1-A6, T2, K1-K3).
4. The scenario check for an alarm (`PumpScenario.checkUrgentAlarmSince`) reads `PumpAlerts.active` instead of any
   URGENT card.
5. Add the requirements below to `PUMP_DRIVER_ACCEPTANCE.md`.
6. **Done (2026-11-19).** Not pumps: the other `runAlarm` callers moved to the same sound owner, and the two
   `KClass` members of `UiInteraction` became `AndroidActivities`. See section 8.

## 6. Requirements in the acceptance file

Added to `PUMP_DRIVER_ACCEPTANCE.md` on 2026-11-19 (sections A and K, and R14 for the real pump); kept here as the short
form.

- **A1** Every alert the pump reports, and every delivery problem the user must act on, goes through `PumpAlerts`.
- **A2** The driver plays no sound and has no notification channel, alarm screen or foreground check of its own.
- **A3** The severity follows what the pump does: delivery stopped or not safe is CRITICAL.
- **A4** `onStart` raises again what the pump still holds, with the same key and `raisedAt`.
- **A5** The pump-side step (clear, confirm, silence) is the alert's action and runs through the queue.
- **A6** The driver resolves an alert when the pump drops it, and all of them when the pump is removed.
- **K1** The driver logic is in commonMain; platform work is behind ports.
- **K2** A platform without the driver's ports has no such pump.
- **K3** The driver's shared tests run on iOS (`.github/workflows/ios-ci.yml`).

## 7. Decisions (2026-11-19)

1. **One sound owner: the notification list.** The alarm screen is a view of the sounding card. Not chosen: keeping
   the full-screen alarm's own sound and giving iOS a way to stop it (`_docs/ios_blockers.md`, option 2). That fixes
   the iOS stop, but it keeps two owners on one player, and the alarm screen still cannot silence only its own alert.
2. **WARNING** is a card plus one notification sound, with no repeating alarm and no alarm screen.
3. **Silence is a snooze of 30 minutes, and it survives a restart.** When the window ends and the alert is still
   active, it rings again. Not chosen: silence until the alert ends (an occlusion could stay unheard for hours), and
   a silence that a restart forgets (a restart would ring an alert the user had just silenced).
4. **The name is `PumpAlerts`.**

## 8. Step 6: the alarms that are not pump alerts

Step 6 of section 5. Built on 2026-11-19; the decisions at the end are Milos's. What is written here is what the
code does, not a plan.

### Why it exists

`UiInteraction.runAlarm` is the other alarm path. `IosUiInteraction.runAlarm` only writes a line in the log, so every
caller of it is **silent on iOS** - there is no sound, no screen and no notification. Two of those callers are shared
code, so this is not a gap in an Android feature, it is a gap in code that both platforms run.

### Inventory, 2026-11-19

Callers that are in scope (a pump driver's own `runAlarm` is not - those go away with step 3, one driver at a time):

| Caller | Source set | What it is |
|---|---|---|
| `CommandQueueImplementation` | **commonMain** | a bolus or insulin-change command failed (`BOLUS_ERROR`) |
| `OverviewScreen` | **commonMain** | the same error, shown from the manage sheet |
| `ComposeMainActivity` | androidMain | the same error again, the Android copy of the `OverviewScreen` call |
| `EatReminderReceiver` | androidMain | the "time to eat" timer (`runEatReminderAlarm`) |
| `TimerReminderReceiver` (automation) | androidMain | an automation timer reminder |
| `UiInteractionImpl.muteAlarm` | androidMain | the re-ring 5 minutes after a mute |

The two commonMain callers are the ones that are silent on iOS today. The androidMain ones have no iOS counterpart
yet, so they are not silent so much as absent - but the eat reminder and the automation reminder both already have an
iOS scheduler (`IosReminderScheduler`), so they will reach this path as soon as anything rings them.

### What `runAlarm` does on Android today

Worth writing down, because all of it has to keep working. `UiInteractionImpl.runAlarm`:

1. cancels a pending mute re-ring and remembers this alarm as the one shown;
2. tells the watch (`AlarmRinging` with **the caller's title** and **`canMute = true`**, or `AlarmStopped` for the eat
   alarm, which the watch rings itself);
3. writes an announcement (`config.APS` only, and not for a re-ring);
4. off the main thread, or in the background: `AlarmNotificationManager.postFullScreenAlarm` - a notification on a
   sound-bearing channel (silent channel while Do Not Disturb is on and the override is off, see `DoNotDisturbRule`),
   `CATEGORY_ALARM`, `setOngoing(true)`, a **Mute action** and a **swipe-to-mute** delete intent (`AlarmMuteReceiver`,
   #5338), `+5 min` for the eat alarm, the looping `AlarmSoundPlayer` under `OWNER_FULLSCREEN` deferred past the
   channel's one-shot sound, and two `setAlarmClock` alarms that light the screen and launch the alarm screen;
5. in the foreground on the main thread: starts the alarm screen directly, which owns the ramp from zero.

And `stopAlarm` is `muteAllAlarms()` - every audible alarm, not only this one - plus `AlarmStopped` to the watch;
`dismissAlarm` adds `EventAlarmDismissed`; `muteAlarm(minutes)` stops and re-rings after N minutes from an in-process
coroutine; `stopEatReminderAlarm` cancels the full-screen alarm alone.

### The design

The same card model as pump alerts, because it already answers every one of those points on both platforms. One alert
path, not two.

1. **Widen the core.** `PumpAlert` gains a lifetime, which is the one real difference between the two kinds:
   - `CONDITION` - today's pump behaviour. The raiser resolves it; a card removed while it is still active counts as a
     30-minute silence and comes back.
   - `EVENT` - the user's acknowledgement ends it. Silence-and-close, a swipe or Mute **resolves** it, and it does not
     come back. A bolus error is an event: nobody will ever "resolve" it from the pump side.

   Everything else - severity, the card, the sound, the alarm screen, snooze, the announcement - is unchanged.
2. **`runAlarm` becomes a shim.** `runAlarm(status, title, sound)` raises an `EVENT` alert with `source = "aaps"`.
   `stopAlarm` resolves, `dismissAlarm` resolves and closes the screen, `muteAlarm(minutes)` is `snooze(..., minutes)`,
   `stopEatReminderAlarm` resolves the eat alert alone.
3. **The eat reminder's `+5 min` becomes an alert action.** `PumpAlertAction(label = "+5 min") { eatReminder.extend() }`
   - the model already carries exactly this, so the special case in `ErrorScreen` and in the notification goes away.
4. **Restore Mute and swipe on the sounding card.** `AlarmNotificationManager.postSilentAlarmNotification`, which is
   what the registry posts for an URGENT card with a sound, has no Mute action and no delete intent;
   `postFullScreenAlarm` has both. They are added to the silent one, so #5338 is answered for **every** sounding card -
   pump alerts included, which do not have it today either.
5. **The watch keeps its title and its Mute.** `CommonNotificationManager.refreshAlarmSound` sends a fixed
   "Urgent alarm" title and `canMute = false`. The card needs to carry the title and whether it can be muted, so the
   watch says what it says today.
6. **Move the Android-only members out of `UiInteraction`.** `mainActivity` and `errorHelperActivity` are `KClass`.
   Every real reader is androidMain (`UiInteractionImpl`, `AlarmNotificationManager`, `NotificationHolderImpl`,
   `AndroidPumpAlertPlatform`); the only other implementation is the desktop stub, which exists solely to satisfy the
   interface and points both at a class that is not an activity. That is the proof they do not belong in shared code.

iOS needed no new mechanism at all: `IosPumpAlertPlatform` already shows the alarm screen, chimes, schedules the
re-ring as a local notification and withdraws it, and `IosSystemNotificationPlatform` already gives a background card
its sound. `IosUiInteraction` is deleted - there is one shared `UiInteractionImpl` in `:implementation` commonMain,
bound by each platform beside `PumpAlerts`, and the desktop keeps its own honest stub because it has no alert
platform.

### What was deleted

A whole parallel alarm path, which is the point of the step:

- `AlarmNotificationManager.postFullScreenAlarm` and everything only it used: the record of an unanswered alarm, the
  `OWNER_FULLSCREEN` sound owner, the deferral of the ramp past a channel one-shot sound and the duration probe
  behind it, `NOTIFICATION_ID_FULL_SCREEN`.
- `AlarmMuteReceiver`: Mute and a swipe are an ordinary dismissal of the card now, which the notification platform
  already reported, and dismissing one card acts on one alert.
- `UiInteractionImpl` in `:app`, 150 lines of activity starts, watch events, an announcement and an in-process
  re-ring timer. What is left of it there is `AndroidActivitiesImpl`, two lines naming two activities.
- `ErrorActivity`'s own `MediaPlayer`, its five intent extras and its non-pump branch: it shows `PumpAlertScreen` and
  nothing else.
- `IosUiInteraction`, whose `runAlarm` only wrote a line in the log.

`unansweredAlarmIntent` moved rather than went: it is `AndroidPumpAlertPlatform` now, set when the alarm screen
could not be shown and cleared when the alert is answered, and `ComposeMainActivity` reads it on the way in (#5338).

### What must not change on Android

The volume ramp; `DoNotDisturbRule` on the channel choice; the `setAlarmClock` screen wake when AAPS is in the
background; reopening an unanswered alarm on the way back in (`unansweredAlarmIntent`, #5338); the Mute action and the
swipe; the watch events (`AlarmRinging` with `canMute`, `AlarmStopped`, `EventAlarmDismissed`); the eat reminder's
`+5 min`; and `MUTE_MINUTES` re-ringing. These tests stay green: `AlarmNotificationManager*Test`,
`AndroidSystemNotificationPlatformTest`, `CommonNotificationManagerTest`, `UiInteractionImplTest`,
`ErrorActivityInjectionTest` and `CarelevoUiTest.aPatchAlarmOpensTheAlarmScreenThatSilencesAndActs`.

### Decisions (2026-11-19)

1. **A key per caller.** A bolus error and a pump-unreachable alarm are two cards, each silenced on its own. The
   registry still sounds only the top one, so the user hears one alarm. Not chosen: one shared key, which is what
   Android does today - a new alarm replaces the one shown, so two different problems can never both be on screen and
   silencing one silences whichever arrived last.
2. **A mute is stored and survives a restart.** It is a snooze, exactly like a pump alert's silence. Not chosen: the
   in-process coroutine used today, which a restart forgets - and which cannot work on iOS at all, where a suspended
   app cannot re-ring itself.
3. **The OK button silences only its own alarm.** A ringing pump alert keeps ringing, because the pump condition is
   still there. Not chosen: today's `muteAllAlarms()`, where OK on a bolus error also stops an occlusion alarm, so one
   alarm can hide another.
4. **An EVENT alert ends when the user answers it.** A swipe, Mute or Silence-and-close resolves it and it does not
   come back. A CONDITION alert is unchanged: removing its card is a 30-minute silence, because the pump still holds
   it. This is what the lifetime is for.
