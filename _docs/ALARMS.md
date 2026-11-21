# Alarms and notifications

What AAPS uses to tell the user something, where each one is shown and who uses it. Written 2026-11-21 from the code
on branch `eversense`. The counts and lists of users change quickly: re-derive them with the commands at the end
before you plan work from them.

Sections 1 to 5 describe the code as it is. Section 6 is the target design, decided on 2026-11-21 and not built.
How a pump alert works is in `PUMP_DESIGN.md` (sections 2 and 8). How a CGM raises its alarms is in `CGM_DESIGN.md`
(section 5).

## 1. The kinds, and how each is used

| # | Kind | API | What the user gets | Sound | Platforms | Used by |
|---|---|---|---|---|---|---|
| 1 | **In-app notification card** | `NotificationManager.post(NotificationId, text, level, sound?)` | A card in the overview list. Levels: URGENT, IMPORTANT, NORMAL, LOW, INFO, ANNOUNCEMENT | Only URGENT **with** a sound: a rising volume and the alarm screen | Android, iOS, desktop | Most modules; on 2026-11-21 about 70 files in 28 modules, most of them in core, DanaR, DanaRS, sync and constraints |
| 2 | **Pump alerts** | `PumpAlerts.raise(PumpAlert)`. Severity CRITICAL, WARNING or INFO; lifetime CONDITION or EVENT | A card. CRITICAL: the alarm screen. WARNING: one chime as an Android notification. Silence is a 30-minute snooze that survives a restart; the watch is told | CRITICAL rings until answered, WARNING once | Android, iOS | Carelevo, and the `runAlarm` shim (row 3) |
| 3 | **App alarms** | `UiInteraction.runAlarm(...)`. Now a shim: it raises an EVENT pump alert with the source `"aaps"` | As row 2: the alarm screen until it is answered | `BOLUS_ERROR`, `EAT_REMINDER`, `ALARM` | Android, iOS | A bolus error (command queue, overview, main activity), the eat reminder, the automation reminder, and the drivers' own alarms: DanaR v2, DanaRS, Diaconn, EOPatch, Medtronic, Omnipod Dash and Eros |
| 4 | **Local alerts** | `LocalAlertUtils` | An URGENT card: **pump unreachable** (also a therapy event and an SMS), **missed BG readings** (off by default) | `ALARM` | Android, iOS (`IosMissedReadingAlarm`) | Core |
| 5 | **Nightscout alarms** | `NsFrameHandler`, `NsIncomingDataProcessor` | Alarms and announcements from Nightscout as URGENT cards (the setting `ns_alarms`) | `ALARM`, `URGENT_ALARM` | Android, iOS | NS client |
| 6 | **Automation** | `ActionNotification`, `ActionAlarm`, `ActionSendSMS` | A card (`AUTOMATION_MESSAGE`); an alarm (a reminder, then row 3); an SMS | The alarm only | Android; iOS through `IosReminderScheduler` | The user's rules |
| 7 | **Loop notifier** | `LoopNotifier` (`AndroidLoopNotifier`, `IosLoopNotifier`) | A system notification: an open-loop suggestion, carbs required | The channel's sound; silent while Do Not Disturb is on and its override is off | Android, iOS | Loop |
| 8 | **Ongoing notification** | `NotificationHolder`, `PersistentNotificationPlugin` | The permanent notification of the foreground service, with BG, IOB and the loop status | None | Android | Services, the persistent notification plugin |
| 9 | **Eat reminder countdown** | `AndroidEatReminderAlarm`, `IosEatReminderAlarm` | A quiet countdown notification, then the alarm (row 3) with "+5 min" | At the end | Android, iOS, watch | The wizard's "time to eat" |
| 10 | **Watch** | `PhoneAlarmWatch`, `DataHandlerWear`, `EatReminderWatch` | The phone's alarm on the watch with Silence and Mute, the bolus progress, open-loop suggestions, the eat timer | The watch's channels | Wear OS | Rows 2, 3, 7 and 9 |
| 11 | **A driver's own notification** | The Insight `ALERT_CHANNEL_ID` | Insight alerts on a channel of their own | Its own | Android | Insight (an exception to `PUMP_DRIVER_ACCEPTANCE.md` A2) |
| 12 | **Short messages** | `ToastUtils`, snackbars | A message that goes away by itself, with no history | None | Android; iOS for snackbars | Screens, the result of an action |

## 2. How an in-app card reaches the Android tray

`AndroidSystemNotificationPlatform` decides it for each card:

| Card | What Android shows |
|---|---|
| URGENT with a sound | A **silent** notification on an alarm channel. The sound comes from `AlarmSoundPlayer`, with a rising volume, so the user is not alerted twice for one alarm |
| Any other card | Shown in the tray when "raise urgent alarms as Android notification" (`BooleanKey.AlertUrgentAsAndroidNotification`, on by default) is on **and** the card has no buttons, **at any level**. A card with buttons is answered in the app |

**A card in the tray makes a sound.** The tray channel `AndroidAPS-Overview` is created with HIGH importance and no
`setSound(null)`, and the notification has priority MAX: the phone plays its default notification sound and shows a
heads-up, for a card of any level, INFO included (checked on a Pixel, 2026-11-21: `mImportance=4`,
`mSound=.../notification_sound`). The setting's name says "urgent" and the code comment says "no-sound, visual
only", but neither is true: the 2017 version sent only URGENT cards to the tray, and its level check is gone.

## 3. Android notification channels

| Channel | Created in | Importance | Used for |
|---|---|---|---|
| Notifications | `AndroidSystemNotificationPlatform`, `SettingsUnreadableNotification` | HIGH | Cards in the tray; "settings unreadable" at the start |
| Alarm: standard, bolus error, error, urgent, eat reminder | `AlarmNotificationManager` (one per `AlarmSound`, in one group) | HIGH | Sounding cards and alerts |
| Alarm silent, silent urgent | `AlarmNotificationManager` | HIGH | The same, while Do Not Disturb is on and its override is off (`DoNotDisturbRule`) |
| Ongoing | `NotificationHolderImpl` | HIGH | The foreground service and the status notification |
| Loop, loop silent | `AndroidLoopNotifier` | HIGH | The open-loop suggestion, carbs required |
| Eat reminder | `AndroidEatReminderAlarm` | LOW | The countdown |
| Insight alert | `InsightPlugin` | HIGH | Insight's own alerts |
| Watch: phone alarm (and silent), bolus progress (and silent), open loop, eat reminder (and silent), the service | The wear module | HIGH or LOW | On the watch |

## 4. iOS

| Class | What it does |
|---|---|
| `IosSystemNotificationPlatform` | Gives a card its sound while the app is in the background |
| `IosPumpAlertPlatform` | Shows the alarm screen, chimes, and schedules the ring after a silence as a local notification, because a suspended app runs no timer |
| `IosAlarmSoundPlayer` | Plays the alarm sounds |
| `IosMissedReadingAlarm` | The missed BG readings alarm |
| `IosEatReminderAlarm` | The eat reminder |
| `IosLoopNotifier` | The loop's notifications |

## 5. What is not uniform yet

- **Pump alerts are new.** Only Carelevo and the `runAlarm` shim use them. Seven drivers still ring through
  `runAlarm`, and Insight has its own channel. Each driver moves when it is next reworked (`PUMP_DESIGN.md`, section 5).
- **Some cards ring outside the alert path.** Pump unreachable, missed BG readings, the Nightscout alarms and
  Eversense are URGENT cards with a sound, not pump alerts. The sound owner is the same (the notification list), but
  the silence is not: "mute all" stops them all, while a pump alert's silence is for that alert only and it rings
  again after 30 minutes.
- **Every card in the tray makes a sound** (section 2): any card without buttons, at any level, dings with the
  phone's notification sound while the tray setting is on.
- **Eversense** posts plain cards today, its transmitter's glucose alarms included. AAPS has no low or high glucose
  alarm of its own yet, so those cards are the only glucose alarm an Eversense user gets (the vendor app is stopped).
- **Nightscout alarms** are the glucose alarms of an AAPSClient today. They come from the Nightscout server's plugins.

## 6. Target design (decided 2026-11-21, not built)

### Three ways to tell the user something

| Tier | For | API | Noise |
|---|---|---|---|
| **Alert** | The user must notice it and answer | `Alerts` (today `PumpAlerts`) | Yes. The raiser gives the severity and the lifetime; the core picks the sound, the alarm screen, the watch, the silence and the announcement |
| **Card** | Information that stays until it is dismissed or ends | `NotificationManager.post`, without a sound | No |
| **Snackbar** | Feedback on an action, in the screen that is open | Event hoisting (see CLAUDE.md) | No |

The ongoing Android notification (the foreground service and the status) and the watch's channels stay what they
are: they show things, they do not warn.

### Levels

Alerts keep their three severities: CRITICAL, WARNING, INFO. The list of cards has four levels instead of six:

| Level | Who makes it | Colour | Sound | Android tray |
|---|---|---|---|---|
| **URGENT** ("Critical") | Only the alert core: the card of a CRITICAL alert | red | The alarm, until answered | Alarm channel (HIGH) |
| **IMPORTANT** ("Warning") | The card of a WARNING alert, or a silent card from code | orange | One chime, only for an alert | The only level in the tray, silent |
| **NORMAL** | Code; the card of an INFO alert | salmon | None | No |
| **INFO** | Code: good news, done, information | green | None | No |

- **LOW** goes into NORMAL: its colour can hardly be told from NORMAL's, and it had 6 ids and 3 posts.
- **ANNOUNCEMENT** goes: its only user, the Nightscout announcement, becomes a WARNING alert.
- **IMPORTANT gets orange.** Today URGENT and IMPORTANT are the same red and differ only by the word; with orange the
  user sees at once whether something rings or only needs to be done soon. Orange is free once ANNOUNCEMENT goes.

### The alert core

- **One core, named `Alerts`.** `PumpAlerts` is renamed (`Alerts`, `Alert`, `AlertSeverity`, `AlertAction`,
  `AlertPlatform`): it carries pumps, CGMs, BG alarms, core errors, the eat reminder and automation, not only pumps.
  This reverses decision 4 of `PUMP_DESIGN.md`.
- **A silence length per alert, and the user's choice of it.** A high can wait an hour or two, an urgent low 15
  minutes. The alert says its default and which lengths the alarm screen offers. Today it is a fixed 30 minutes.
- **Scheduled alerts: ring at a time, unless cancelled before.** The platform books it in advance (a local
  notification on iOS, `AlarmManager` on Android), so it rings also when the app cannot run. It replaces four
  mechanisms of their own: `IosMissedReadingAlarm`, the eat reminder alarms, the automation reminder
  (`ReminderScheduler`, `TimerReminderReceiver`, `IosReminderScheduler`) and the ring after a silence on iOS.
- **The announcement is written under the alert's own source.** Today every alert is written through `PumpSync` with
  the active pump's type and serial, also the app's own alarms. Only a pump alert belongs under the pump.

### The user's choice per kind of alert

The user can make a kind of alert silent, or turn it off. Example: a Combo profile update takes minutes, and a try
that fails rings before the next try succeeds.

- **A catalogue of alert kinds.** Each kind has a stable id, a title, its severity and what the user may change. The
  kinds are enums that implement one small interface (`AlertKind`): `CoreAlert` in core for the core, `"bg"` and
  `"nightscout"`, and one enum per driver (`CarelevoAlert`, `EversenseAlert`). Core does not list the drivers' kinds:
  `NotificationId` shows what that costs (119 entries in one core enum, many of them for one driver). An alert
  carries its kind, not a free string.
- **Three choices per kind:** Ring (the default), Silent (a card, no sound, no alarm screen, the watch not told), Off
  (not shown).
- **Per phone.** Each phone chooses for itself; the choice is not synced. For the BG alarms, "each phone decides
  whether it rings" is this same choice.
- **The safety kinds are protected.** Urgent low, and insulin delivery stopped or not safe (occlusion, empty
  reservoir, patch or pod stopped), cannot be set to Silent or Off. The settings show them greyed, with the reason.
  A ringing one is still answered and silenced for a while, as every alert. Each kind declares whether it is
  protected.
- **Applied in one place**, the alert core. A driver or a rule does nothing for it.
- **Off is not lost.** An alert that is off is still written to the log and the history; it is only not shown.
- **Visible.** The alert settings and a mark in the overview say how many kinds are silent or off, so a choice is not
  forgotten.
- **No delay in the core.** An alert is shown when it is raised. A kind that is noise for one user is silenced by
  that user. The trade-off: a user who silences "profile update failed" also gets a real, lasting failure as a silent
  card only. (The BG rules keep their own "how long the condition must last"; that is part of a rule, not of the
  core.)

### Review of today's notification ids (2026-11-21)

All 119 `NotificationId`s, with who posts each one. In scope: the core, Carelevo and Eversense. The other pump drivers
and the other BG sources (about 45 ids) are reviewed when each one moves.

**Become alerts of the core** (`CoreAlert`, source `"aaps"`):

| Kind | From today | Severity | Lifetime | Protected |
|---|---|---|---|---|
| Profile update failed | `FAILED_UPDATE_PROFILE`, the write failed (URGENT, bolus-error sound) | CRITICAL | CONDITION: ends with the next successful write | No: the Combo case |
| Basal far from the profile | `FAILED_UPDATE_PROFILE` too, "basal reported far from profile" | CRITICAL | CONDITION | No |
| Insulin change failed | `runAlarm`: the new insulin is in the pump, the old program runs | CRITICAL | CONDITION: ends when the write succeeds | Yes: delivery not safe |
| Bolus failed | `BOLUS_DELIVERY_FAILED`, on the master and the client | CRITICAL | EVENT | Yes: insulin the user asked for was not given |
| Command failed | `runAlarm` from the overview and the main screen | CRITICAL | EVENT | No |
| Bolus cancelled | `BOLUS_CANCELLED` (IMPORTANT, silent) | WARNING | EVENT | No |
| Carbs not saved | `CARBS_STORE_FAILED` (URGENT, but silent: heard only through the tray) | WARNING | EVENT | No |
| Pump unreachable | `PUMP_UNREACHABLE` | CRITICAL | CONDITION: ends when the pump is reached; its threshold setting stays | No: optional today |
| Pump driver not responding | `PUMP_DRIVER_NOT_RESPONDING` | CRITICAL | CONDITION: ends when the command ends | No |
| Zero delivery not set | `ZERO_DELIVERY_NOT_SET` | CRITICAL | CONDITION | Yes: the pump may give basal while the user thinks it does not |
| Plugin start failed | `PLUGIN_START_FAILED` | CRITICAL | CONDITION: ends when the plugin starts | No |
| Plugin work failed | `PLUGIN_WORK_FAILED` | WARNING | EVENT | No |
| Hard limit | `TOAST_ALARM` from `HardLimits` (error sound) | WARNING | EVENT | No |
| Carbs required | `CARBS_REQUIRED` and the loop notifier | WARNING | CONDITION | No |
| Eat reminder | `runAlarm` | CRITICAL | EVENT, scheduled | No |
| Automation alarm | `ActionAlarm` through `runAlarm` | CRITICAL | EVENT, scheduled | No |

**Become BG alarms** (source `"bg"`): `BG_READINGS_MISSED` becomes the stale-data rule. Urgent low, the level rules,
persistent high, the rate rules and predictive low are new.

**Become a Nightscout alert:** `NS_ANNOUNCEMENT` (the level-0 message and the careportal announcement), WARNING,
EVENT. **Removed** after the BG alarms: `NS_ALARM`, `NS_URGENT_ALARM`.

**Become driver kinds:**

- Carelevo (`CarelevoAlert`): its pump alerts already are alerts; their keys become the enum. Its
  `PUMP_WARNING` card "the pump was not reached" at the removal (`PUMP_DRIVER_ACCEPTANCE.md` D9) becomes a WARNING
  EVENT alert: the user has to take the pump off, and today it is a silent card.
- Eversense (`EversenseAlert`): `EVERSENSE_ALARM` for the device and error alarms (and its glucose alarms, until the
  BG alarms exist); `EVERSENSE_SENSOR_EXPIRY`, `EVERSENSE_BATTERY_LOW`, `EVERSENSE_CALIBRATION_DUE` and
  `EVERSENSE_PLACEMENT` (transmitter off the sensor) as WARNING CONDITION alerts.

**Stay cards**, at the target level:

| Level | Ids |
|---|---|
| IMPORTANT | `INVALID_PROFILE_NOT_ACCEPTED` (from NORMAL: the user has to fix the profile), `INVALID_PHONE_NUMBER`, `DST_LOOP_DISABLED`, `DISK_FULL`, `INVALID_VERSION`, `VERSION_EXPIRE`, `INSULIN_MIGRATION_DEFAULT_USED`, `IMPORTED_INSULIN_ADDED`, `MASTER_PASSWORD_NOT_SET`, `AAPS_DIR_ACCESS_LOST`, `GOOGLE_DRIVE_ERROR`, `SECRETS_NOT_PROTECTED`, `BLUETOOTH_PERMISSION_MISSING`, `SCENE_PROFILE_NOT_RESTORED`, `SCENE_CHAIN_ERROR`, `AUTOMATION_MESSAGE`; and the dev-branch "closed loop disabled" of `SafetyPlugin`, which gets its own id instead of `TOAST_ALARM` |
| NORMAL | `WRONG_DRIVER`, `MINIMAL_BASAL_VALUE_REPLACED`, `MAXIMUM_BASAL_VALUE_REPLACED`, `BASAL_PROFILE_NOT_ALIGNED_TO_HOURS`, `BASAL_VALUE_BELOW_MINIMUM`, `WRONG_PUMP_DATA`, `SMB_FALLBACK`, `DYN_ISF_FALLBACK`, `NSCLIENT_VERSION_DOES_NOT_MATCH`, `NSCLIENT_PAIRING_ORPHAN`, `OPEN_HUMANS_SIGNED_OUT`, `INVALID_MESSAGE_BODY`, `GARMIN_NOT_ON_CLIENT`, `IDENTIFICATION_NOT_SET`, `AAPS_DIR_NOT_SELECTED`, `NEW_VERSION_DETECTED`, `SNACKBAR_FALLBACK`, `DST_IN_24H` (from LOW) |
| INFO | `PROFILE_SET_OK`, `SENSOR_CHANGE_DETECTED` (from NORMAL), `SETTINGS_EXPORT_RESULT`, `SCENE_CHAINED`, `EVERSENSE_FIRMWARE` |

`PUMP_ALERT` stays: it is the card of every alert (renamed with the core).

**No user left** (7): `CARELEVO_PATCH_ALERT`, `SHORT_DIA`, `OLD_NS`, `NSCLIENT_NO_WRITE_PERMISSION`, `NS_MALFUNCTION`,
`SCENE_ENDED` (a test only), `SCENE_CHAIN_SKIPPED`. Each lost its last user in a change made on purpose (Nightscout
client v1 removed, the scenes' banner, the insulin concentration work, Carelevo on pump alerts), so they hide no bug.
**They are deleted**, with `NotificationId.fromOrdinal`, which has no caller either. An id's position in the enum is
only the Android number of its card while the app runs: nothing stores it, and the card registry lives in memory. A
deleted entry renumbers the ids after it, and the only effect is once, after the update, on a card left in the tray
from before: a new card with the same number replaces it, or swiping the old one away removes the new card. Nothing
rings because of it (decided 2026-11-21: acceptable).

**Found on the way:**

- `FAILED_UPDATE_PROFILE` carries two problems (the write failed; the basal reported far from the profile) and
  `TOAST_ALARM` two unrelated ones (a hard limit; the dev-branch closed loop). As kinds they are split, so a user can
  silence one without the other.
- `CARBS_STORE_FAILED` is URGENT but has no sound: today it is heard only through the tray's ding (section 2).
- Carelevo's "the pump was not reached" at the removal is a silent card, though the user must act.
- The comments in `NotificationId` that say a new id must be "appended at the END", because "`fromOrdinal` maps a stored
  ordinal back", are out of date: nothing calls `fromOrdinal`. They go with the unused ids, and a new id can go where
  it belongs.

### Who raises what

| Source | Raises | Lifetime | Severity |
|---|---|---|---|
| A pump driver (`"carelevo"`, ...) | Its pump's alerts (`PUMP_DRIVER_ACCEPTANCE.md`, A1-A6) | CONDITION | From the pump |
| A CGM driver (`"eversense"`, ...) | **Device and error states only**: sensor failed or expired, battery, calibration, transmitter off the sensor. **Never** a glucose or a missed-readings alarm | CONDITION, or EVENT when the transmitter never says an alarm ended | From the transmitter |
| `"bg"`: AAPS's own BG alarms | Urgent low, low, high, urgent high, stale data, falling or rising fast, predictive low | CONDITION, with a margin so it does not flap | Urgent low always CRITICAL; the others as the user sets them; never INFO |
| `"aaps"`: the core | Pump unreachable, the pump driver not responding, zero delivery not set | CONDITION | CRITICAL or WARNING |
| `"aaps"`: the core | Bolus delivery failed (master and client), carbs not saved after a bolus (`CARBS_STORE_FAILED`, a silent URGENT card today), profile update failed, basal far from the profile, a hard limit hit, a plugin that failed to start or work | EVENT | CRITICAL or WARNING |
| `"aaps"`: the core | The eat reminder, an automation alarm | EVENT, scheduled | CRITICAL |
| `"aaps"`: the loop | Carbs required | CONDITION | WARNING |
| `"nightscout"` | An announcement (careportal, or a level-0 message) | EVENT | WARNING: one chime and a card |

### BG alarms

- **Evaluated in shared code on every stored reading, on the master and on every client.** A client evaluates the
  readings it receives, so a follower does not need the Nightscout server's alarms. The reading checks of
  `CGM_DESIGN.md`, section 3, run first: a reading from the future must neither raise nor end an alarm.
- **For every source.** A CGM driver, xDrip and a client get the same alarms from the same rules.
- **The rules are the master's, the ringing is each phone's.** Thresholds, durations and which alarms are on are set
  on the master and synced to the clients, as the sensor lifetime is. Each phone decides only whether it rings and
  how loud: a parent's client can ring while the master is quiet.
- **On an update**, AAPS can offer the Nightscout thresholds (`bgLow`, `bgHigh` and the others, from the server's
  settings) as the starting values.

#### The rules (the master's, synced to the clients)

- **Level rules are a list.** Any number of low and high rules. Each has a threshold, a time-of-day window (all day,
  or from and to, also across midnight: a night profile), how long the condition must last, its default silence
  length, and its severity (WARNING or CRITICAL).
- **Urgent low is always there.** It is protected: it cannot be set to Silent or Off, and it is not paused. When it
  rings, the user silences it as any alert, for a short time. It is CRITICAL and all day; the user sets only its
  threshold.
- **One active level alert per direction.** The most severe matching rule wins: the lowest low, the highest high. A
  less severe rule stays quiet while the stronger one is silenced; a stronger one replaces it at once. Low and urgent
  low never ring together.
- **The other kinds:**
  - **Stale data**, a scheduled alert booked again at every reading, with a grace time after the app starts. It
    replaces the missed BG readings alarm of `LocalAlertUtils` and `IosMissedReadingAlarm`, and also the Nightscout
    stale-data settings, which today only colour the data age on a client: one "data is old" rule for the display
    and the alarm.
  - **Persistent high**: above a threshold for N minutes while not falling, and only when the readings in that time
    are regular.
  - **Falling or rising fast**: a rate per minute, from the average of several readings and the last step, so it works
    for 1-minute and 5-minute sensors alike.
  - **Predictive low**: from the loop's own prediction, with IOB and COB; a client uses the master's prediction from
    the device status. Booked as a scheduled alert at the predicted time, and booked again at every reading.
- **Smart silence.** A silenced level alert does not ring again while BG already moves back towards the range, judged
  from AAPS's trend per minute. It is part of the BG rules, not of the alert core (decision 9).
- **Pause.** All BG alarms, the lows or the highs, for a time or until switched on again; and a silence set before an
  alert rings (before sport or a meal). A pause never includes urgent low. Pauses show in the "N alerts silent" mark.
- **Noisy readings** may be marked on the alert, but never hold back urgent low.

#### The ringing (each phone's own)

- Ring, Silent or Off per kind (decision 7), with the protected kinds greyed.
- **The sound per kind:** a bundled AAPS sound, with more of them than today (a different low and high), or on Android
  any ringtone of the phone. iOS can only play sounds bundled with the app, so it gets the bundled ones.
- The rising volume, vibration first, and the Do Not Disturb rule, as today; and no alarm sound during a phone call.
- **A Test button** per rule, which plays it as it will sound.

#### Between the phones

- **A shared silence, chosen per phone.** A silence of a BG alarm is sent to the other phones, master and clients,
  over the master-client link. Each phone decides whether it takes silences from the others: a parent can silence
  the child's phone, or keep their own ringing. It replaces the Nightscout acknowledgement, which goes away with the
  Nightscout alarms. A silence from a phone whose clock is far off is refused.
- **Escalation (a later step).** A client can be set to ring only when the master has not answered within N minutes;
  optionally an SMS to a contact. It needs the shared silence.

#### Learned from xDrip+ (read from its code, 2026-11-21)

Taken: the list of level rules with time windows, one active alert per direction, a default silence per rule, the
pause and the silence before it rings, smart silence, persistent high, the rate rule with several readings, the grace
after a start, the Test button, the shared snooze between phones, and the escalation (xDrip's "Emergency Assist").

Done better here: the predictive low from the loop's prediction (xDrip fits a curve to the last 12 minutes); every
alert kept in the history (xDrip writes log lines only); one alert engine, with the watch told by the phone (xDrip has
a second copy on the watch); smart silence and the rate rule per minute (xDrip assumes 5-minute readings).

Not copied:

- Two repeat numbers. xDrip has a re-raise interval and a default snooze, and its own code asks what the difference
  is. Here an alert rings until it is answered, and comes back when its silence ends: one number.
- Settings over four or five screens: here one alerts screen.
- Changing the phone's volume, playing on the music stream and no notification channels: xDrip can do these only
  because it targets Android SDK 24.
- Holding back alerts while the readings are noisy, for urgent low.

### CGM drivers and glucose alarms

A CGM driver raises no glucose and no missed-readings alarm; the core raises those for every source. The driver
raises its device and error states. Where they overlap (the transmitter says there is no glucose: a sensor fault,
glucose suspended), the driver's alert says why, and the ringing for the missing readings comes from the core's
stale-data rule.

**No gap:** a driver stops its transmitter's glucose alarms only in the same change that brings the core BG alarms,
or after it. Until then they are the only glucose alarm its user has.

### Nightscout

- **The alarms of level 1 and 2 are removed**, with their snooze that acknowledged them in Nightscout. **Only after**
  the BG alarms run on clients: removing them first would leave a follower with no alarm at all.
- **Announcements** arrive as a WARNING alert: one chime and a card, ended by the user's answer. Today a careportal
  announcement rings with the alarm sound.
- What AAPS sends to Nightscout (announcements from errors and from carbs required) does not change.

### Cards and short messages

- **An URGENT card is always the card of a CRITICAL alert**, and only the alert core makes one. It works so already:
  each alert is one card in the list (`NotificationId.PUMP_ALERT`), CRITICAL as an URGENT card with the alarm sound,
  WARNING as IMPORTANT, INFO as NORMAL. No other code posts an URGENT card: what is urgent must be noticed, which is
  what an alert is for.
- A card someone posts makes no sound, in the app and in the tray. The public `post` loses its `sound` parameter and
  the URGENT level once the last pump driver has moved to alerts; until then they stay, marked deprecated. The alert
  core keeps its own way to make its URGENT card with the sound.
- On 2026-11-21, 31 notification ids default to URGENT. The core's become alerts (the review below), and the other
  pump drivers' go as each driver moves.
- **The tray becomes silent in the same step, not before.** Today some users hear a driver's URGENT card only through
  the tray's ding (section 2); it may stop only when everything that must make noise is an alert. The silent tray
  needs a new channel id, because Android does not let an app change a channel's sound once it exists, and the old
  channel is deleted. Only IMPORTANT cards go to the tray: an URGENT card is an alert, which has its own channels.
  The new channel has DEFAULT importance, so it does not pop up either; a pop-up is for alerts. The alarm channels
  stay HIGH.
- The setting "raise urgent alarms as Android notification" then means "show important cards in the tray", and is
  named so.
- `ToastUtils` has one user left (DanaRS) and goes with it.

### Order of work

The order matters: step 3 must not come before step 2.

0. At any time, on its own: delete the 7 unused `NotificationId`s, `fromOrdinal` and the out-of-date "append at the
   END" comments.

1. The core: the rename to `Alerts`, the alert kinds (`AlertKind`, `CoreAlert`) with the user's Ring, Silent or Off
   per phone and its settings screen, the silence length and its choice, the announcement under its own source, the
   scheduled alerts.
2. The BG alarms, stale data included, on the master and the clients, with the shared silence. The escalation comes
   later, after the basics work.
3. The Nightscout alarms removed; announcements as a WARNING alert.
4. The core's sounding cards to alerts (the table above).
5. Eversense: its device alarms to alerts, and its glucose alarms dropped (step 2 is done by then).
6. Carbs required as an alert.
7. The cleanups, after the other drivers: the `sound` parameter and the URGENT level of the public `post`, the
   silent tray channel with only IMPORTANT cards, the tray setting's name, `ToastUtils`.

### Decisions (2026-11-21)

1. **One alert core, named `Alerts`**, for everything that makes noise.
2. **AAPS has its own BG alarms.** The rules are the master's, synced to the clients; each phone decides whether it
   rings.
3. **A CGM driver raises no glucose or missed-readings alarm**: the core does, for every source. The driver raises its
   device and error states.
4. **The Nightscout alarms are removed** once the own BG alarms work on clients.
5. **A Nightscout announcement is a WARNING alert:** one chime and a card.
6. **Four card levels:** URGENT (only from the alert core), IMPORTANT (orange), NORMAL, INFO. LOW and ANNOUNCEMENT go.
7. **The user chooses Ring, Silent or Off per kind of alert, per phone**, from a catalogue of alert kinds (an enum per
   module behind `AlertKind`).
8. **The safety kinds are protected:** urgent low and insulin delivery stopped or not safe can be neither silenced
   nor turned off.
9. **No "raise after" delay in the alert core.** Noise for one user is that user's choice to silence.
10. **BG level rules are a list with time windows**; urgent low is always there and protected; one active level alert
    per direction.
11. **A silence is shared between the phones**, and each phone chooses whether it takes silences from the others.
12. **Escalation** (a client rings when the master did not answer, an SMS) is designed now and built later.
13. **The sound per kind, per phone:** a bundled AAPS sound, or on Android any ringtone of the phone.

Open, with a recommendation:

- Do Not Disturb: the urgent low always breaks through; the other alarms follow the existing override setting.
- Which silence lengths the alarm screen offers. xDrip offers 10 minutes to 12 hours, with 35 minutes for a low and
  2 hours for a high as the defaults.

## 7. How to find them again

```text
git grep -l -E "notificationManager\.post\(" -- "*.kt" ":!*/src/*[Tt]est*/**"   # cards (row 1)
git grep -l "pumpAlerts.raise" -- "*.kt" ":!*/src/*[Tt]est*/**"                   # pump alerts (row 2)
git grep -l "\.runAlarm(" -- "*.kt" ":!*/src/*[Tt]est*/**"                        # app alarms (row 3)
git grep -l "createNotificationChannel(" -- "*.kt" ":!*/src/*[Tt]est*/**"         # Android channels (section 3)
```
