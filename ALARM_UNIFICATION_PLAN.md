# Alarm / Notification Unification Plan

## Goal

Unify the fragmented alarm system into a single, level-driven pipeline so that
"how loud / ramping / full-screen / snoozeable an alarm is" is decided by **one
axis (`NotificationLevel`)** instead of three tangled, independent inputs.

## Problem being fixed

Refactor `47bb7d3513` ("Improve ErrorActivity compatibility") moved the ramping
audio into `ErrorActivity` and deleted the standalone `AlarmSoundService`. As a
result, alarms raised through `NotificationManager.post(..., soundRes=...)` (NS
alarms, local alerts, and most pump plugins) now play a **fixed-volume, one-shot
channel sound** with **no volume ramp** and **no working snooze**.

Reported symptoms (all on NS-originated alarms, but the whole `post(...soundRes...)`
family is affected):

- Alarm is always very loud (channel one-shot at full volume, no ramp-from-zero).
- "Increasing volume" doesn't work (ramp logic only exists inside `ErrorActivity`,
  which this path never opens).
- Snooze doesn't work (the loud system notification carries no snooze action; the
  snooze buttons live only on the in-app card, and snoozing them doesn't stop the
  sound).

The full-screen path (`UiInteraction.runAlarm` → `ErrorActivity`) still works — that
is why the in-app test buttons behaved correctly while real NS alarms did not.

## Root cause

Three concerns got conflated across two parallel APIs:

1. **Two entry points** both create alarms: `UiInteraction.runAlarm()` and
   `NotificationManager.post()`.
2. **Audio is implemented twice and welded to presentation**: ramp lives inside
   `ErrorActivity`; the internal path got a fixed-volume channel one-shot.
3. **Four independent inputs** — severity (`level`), audibility (`soundRes`),
   full-screen-ness, and actions — are each partially honored by a different path.

Key enabling fact for the fix: AAPS always runs a persistent foreground service
(`DummyService`), so a plain `MediaPlayer` can play and ramp looping audio from the
background **without starting any service** — sidestepping the old
`startForegroundService()` → `startForeground()` failure that motivated the refactor.

## Target design — one pipeline, three separated concerns

- **Single source of truth:** the alert registry (`_notifications` StateFlow in
  `NotificationManagerImpl`). Every alarm is one `AapsNotification`.
- **Single entry point:** `NotificationManager.post(...)`. `runAlarm` is removed as a
  public API; everything posts.
- **Level drives everything.** `URGENT` ⇒ sound + ramp + full-screen. Everything below
  ⇒ silent internal notification (current behavior).
- **Three sinks**, each owning exactly one concern:
    - `AlarmSoundPlayer` (new `@Singleton`, activity-less, no service) — audio + ramp + DND.
      The single highest-priority active `URGENT` alert owns the speaker.
    - System notification — shade / lock-screen visibility + action buttons. **Silent**
      channel when we own the audio; FSI attached for full-screen.
    - `ErrorActivity` — full-screen rendering only; observes the top alert, routes
      OK/Mute/Snooze back to the manager. No `MediaPlayer`.

## Level model

New ladder (priority ascending = higher priority first, matching `sortBy { level.priority }`):

```
URGENT(0)        // NEW — alarm: sound + ramp + full-screen (the old runAlarm path)
IMPORTANT(1)     // = the OLD "URGENT" (renamed via Android Studio): important, silent, top of list
NORMAL(2)
LOW(3)
INFO(4)
ANNOUNCEMENT(5)
```

Definition of `URGENT` (the only tier that rings + takes over the screen):

> **An acute, active insulin-delivery failure, a critical BG condition, or a
> user-configured alarm.** Recoverable / config / warn states do NOT ring.

`soundRes` is retained as a **tone selector** (boluserror / urgentalarm / alarm / error)
but is only honored for `URGENT`.

### Reassignment decisions

Promote to `URGENT` (alarm):

| Id                                                                                                                                                                                 | Rationale                                                |
|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------------|
| `PUMP_ERROR`, `COMBO_PUMP_ALARM`, `MEDTRONIC_PUMP_ALARM`, `OMNIPOD_POD_FAULT`, `OMNIPOD_POD_ALERTS`, `EOFLOW_PATCH_ALERT`, `EQUIL_ALARM`, `EQUIL_ALARM_INSULIN`, `DANA_PUMP_ALARM` | pump / delivery alarm                                    |
| `PUMP_UNREACHABLE`                                                                                                                                                                 | APS cannot deliver                                       |
| `BG_READINGS_MISSED`                                                                                                                                                               | CGM data loss → unsafe dosing                            |
| `NS_URGENT_ALARM`                                                                                                                                                                  | critical BG                                              |
| `NS_ALARM`                                                                                                                                                                         | user override — keep audible for now                     |
| `TOAST_ALARM`                                                                                                                                                                      | hard-limit dosing guard                                  |
| `AUTOMATION_MESSAGE`                                                                                                                                                               | user-configured alarm (carve-out)                        |
| `FAILED_UPDATE_PROFILE` (merged, see below)                                                                                                                                        | basal profile not written → wrong basal (delivery issue) |

Merge: `PROFILE_SET_FAILED` → `FAILED_UPDATE_PROFILE` (same condition at two code
layers; DanaR raises both). Repoint the 2 DanaR msg-handler call sites, unify the
string. Merged id is `URGENT` → **all pumps now alarm on profile-set failure**
(they were silent before — accepted).

Stay / demote to `IMPORTANT` (silent) — already handled by the Studio rename, no
further edits except as noted:

- Borderline resolved to IMPORTANT: `DST_LOOP_DISABLED`, `OMNIPOD_UNKNOWN_TBR`,
  `AIDEX_SENSOR_EXPIRED`, `AIDEX_SENSOR_ERROR`.
- Everything else that was `URGENT` pre-rename stays `IMPORTANT`.

### Behavior-change summary (vs. today)

- **Newly ring + full-screen:** the promoted pump alarms, `PUMP_UNREACHABLE`,
  `BG_READINGS_MISSED`, `TOAST_ALARM`, `AUTOMATION_MESSAGE`, and `FAILED_UPDATE_PROFILE`
  (all pumps). Several of these rang before but did not take over the screen; now they
  do (when device is idle/locked — otherwise heads-up).
- **Goes silent:** `OMNIPOD_UNKNOWN_TBR` (state-sync warning, no critical user action).
- **Unchanged:** every IMPORTANT/NORMAL/LOW/INFO/ANNOUNCEMENT notification.

### `legacyId` removal

`NotificationId.legacyId` is a fossil of the pre-enum int-constant era. Its only live
consumers are internal to `NotificationManagerImpl` (system-notification id +
in-process `RECEIVER_NOT_EXPORTED` dismiss round-trip). Nothing persists or transmits
it. Remove it; derive the int from `ordinal` (`fromLegacyId` → `entries[ordinal]`).

## Status

- **Phase 0 — core + clean/sole-path call sites: DONE, compiles** (core:interfaces, implementation,
  plugins:sync, pump danar/danars/diaconn/insight/medtrum/combov2, app/ui all green).
    - `NotificationLevel.URGENT(0)` added, ladder renumbered.
    - `NotificationId`: `legacyId` removed → `fromOrdinal`; `PROFILE_SET_FAILED` merged into
      `FAILED_UPDATE_PROFILE` (2 DanaR sites repointed); 15 ids defaulted to `URGENT`.
    - **Correction:** `AUTOMATION_MESSAGE` is NOT an alarm (the automation "Alarm" action uses
      `TimerUtil.scheduleReminder`, not this path) → kept `IMPORTANT`.
    - NS alarms (`NS_ALARM`, `NS_URGENT_ALARM`) and `FAILED_UPDATE_PROFILE` (all pumps) had their
      explicit level overrides removed so the `URGENT` default governs.
    - `NotificationManagerImpl` legacyId→ordinal + alarm checks keyed on `URGENT`;
      `ComposeMainActivity` / `NotificationBottomSheet` handle the new tier.
- **Phase 1 — `AlarmSoundPlayer` extraction: DONE, compiles.**
    - New `AlarmSoundPlayer` interface (core:interfaces) + `AlarmSoundPlayerImpl` (implementation,
      `@Singleton`, activity-less, no service), bound in `ImplementationModule`.
    - Ramp / MediaPlayer / duration-probe / deferral logic moved out of `ErrorActivity`; the
      activity
      now injects the player and only drives play/stop. `startMediaPlayer` always runs on the main
      looper (play may be called from IO).
- **Phase 2 — route internal URGENT through the player: DONE, compiles.**
    - `NotificationManagerImpl.postInternal`: `URGENT` + soundRes → `alarmSoundPlayer.play()` + a
      **silent** heads-up notification (`AlarmNotificationManager.postSilentAlarmNotification`,
      `CHANNEL_FULL_SCREEN_SILENT`). Sound gated on `URGENT`; lower levels ignore soundRes.
    - `cancelAlarmFor()` stops the player + cancels the silent notification on
      dismiss/replace/expire;
      the in-app snooze button already dismisses, so snooze now silences the alarm.
    - `soundingKey` tracks which alarm owns the player.
    - `AlarmNotificationManager.postSoundAlarmNotification` (the old loud one-shot path) is now
      unused
      → delete in Phase 4.
    - Compiles green: implementation, ui, app (full Dagger graph validated).
- **Phase 4 — dead-code removal: DONE, compiles.**
  `AlarmNotificationManager.postSoundAlarmNotification`
  (the loud channel one-shot) deleted.
- **Test panel: DONE, compiles.** Dev-only (`config.isDev()`) section in the maintenance bottom
  sheet (`MaintenanceViewModel` + `MaintenanceBottomSheet`) with buttons: internal alarm, internal
  URGENT alarm, full-screen alarm, important silent notification, stop/clear — exercising every
  path.
- **Phase 3 — NOT done (held for validation + decisions).** Folding `runAlarm` into `post` is a
  ~17-call-site migration across pump drivers, plus an `ErrorActivity` render-only rewrite of a
  currently-working path — low user-visible benefit, real risk. The genuinely valuable part is the
  **per-driver pump-alarm taxonomy** (which pump alerts are `URGENT`), which needs per-driver
  decisions. Recommend: validate Phases 1–2 + test panel on-device first, then do the taxonomy.
- **Phase 0 — DEFERRED tail (needs per-driver judgment, overlaps Phase 3):**
    - Overloaded pump-alarm ids whose call sites pin the level (e.g. `COMBO_PUMP_ALARM` posted
      NORMAL for warnings / IMPORTANT for errors): their `URGENT` default is never reached, so the
      real alarm won't ring until each driver's error branch is set to `URGENT`. Same pattern in
      Medtrum / Omnipod-dash / eopatch explicit overrides.
    - Dual-path risk: several pump-alarm ids also fire via `runAlarm` (full-screen). Promoting their
      `post()` to `URGENT` could double-alarm — resolve together with Phase 3 (runAlarm
      unification).

## Code-review fixes (applied, compiles)

- **Concurrent-alarm silencing:** replaced the single `soundingKey` write with a
  `refreshAlarmSound()` orchestrator in `NotificationManagerImpl` — the highest-priority active
  URGENT+sound notification owns the player; dismissing the audible one promotes the next.
- **Player threading:** `AlarmSoundPlayerImpl` now confines ALL player state to the main looper
  (`play`/`stop` post Runnables); fixes cross-thread `player` races, double-`release`, and the
  blocking `MediaMetadataRetriever` probe on the caller thread (probe skipped entirely when
  `postedAt == 0`, i.e. the internal path).
- **Cross-path stomp:** `AlarmSoundPlayer.play/stop` take an owner tag (`OWNER_FULLSCREEN` /
  `OWNER_INTERNAL`); `stop` is owner-scoped so `ErrorActivity.onDestroy` can't silence an internal
  alarm. (Full unification of the two drivers remains Phase 3.)
- **Test buttons:** now use dedicated `TEST_ALARM` / `TEST_NOTIFICATION` ids instead of real NS ids
  — can't collide with a genuine Nightscout alarm.
- **Silent-alarm regression (per-driver):** restored `URGENT` at the 5 sound-bearing sites that were
  `URGENT` before the rename — Medtrum `PUMP_SYNC_ERROR` / `PUMP_ERROR` / `PUMP_SUSPENDED` (×2) and
  eopatch `EOFLOW_PATCH_ALERT` (PatchManager). **Still to decide:** `NS_ANNOUNCEMENT`-with-sound and
  Omnipod-Dash unconfirmed-command (borderline — not delivery/BG emergencies).
- **Cleanups:** removed dead `rh` injection from `ErrorActivity`; fixed stale `legacyId` KDoc.
- **Accepted risks (noted, not changed):** `ordinal`-as-id fragility on enum reorder;
  `MediaPlayer` background-audio depends on `DummyService` (persistent-notification plugin).

## Implementation phases

Each phase compiles and leaves alarms working.

### Phase 0 — Level / enum (mechanical, fully decided)

1. `NotificationLevel`: add `URGENT(0)` on top, renumber `IMPORTANT(1) … ANNOUNCEMENT(5)`.
2. `NotificationId`:
    - Remove `legacyId`; `fromLegacyId(int)` → `entries[ordinal]`.
    - Update the three `NotificationManagerImpl` uses (`notify` id, `deleteIntent`,
      `instanceKey`) to `ordinal`.
    - Merge `PROFILE_SET_FAILED` → `FAILED_UPDATE_PROFILE` (repoint 2 DanaR sites,
      unify string), set `URGENT`.
    - Promote the ids listed above to `URGENT`.

### Phase 1 — Extract `AlarmSoundPlayer` (no behavior change)

3. Move ramp / `MediaPlayer` / `probeSoundDurationMs` out of `ErrorActivity` into a
   `@Singleton AlarmSoundPlayer` (`play(soundRes, ramp)` / `stop()`), activity-less
   (relies on `DummyService` foreground). `ErrorActivity` delegates. Verify pump
   full-screen alarms still ramp.

### Phase 2 — Route internal URGENT through the player (the bug fix)

4. `NotificationManagerImpl.postInternal`: if `level == URGENT` →
   `AlarmSoundPlayer.play(...)` + post a **silent** FSI notification carrying `actions`;
   else current path. Gate sound on `URGENT` (non-URGENT `soundRes` ignored).
5. Stop the player on dismiss / replace / expire; wire NS snooze + ack to stop + dismiss.
    - After Phase 2 the reported symptoms (ramp + snooze) are fixed.

### Phase 3 — Unify entry point, remove `runAlarm`

6. Replace `UiInteraction.runAlarm` with `NotificationManager.post(level = URGENT)`.
    - **Open sub-decision:** transient `runAlarm` callers (e.g. bolus delivery error)
      have no `NotificationId`. Either give them ids, or add an id-less "transient
      URGENT" `post` overload. Also dedupe real overlaps (CommandQueue `runAlarm`s
      "failed update basal profile" while the pump also posts `FAILED_UPDATE_PROFILE`).
7. `ErrorActivity` → render-only: observes the top `URGENT` alert, renders, routes
   OK/Mute/Snooze back to the manager. `MediaPlayer` already removed in Phase 1.

### Phase 4 — Collapse duplication

8. Merge `postFullScreenAlarm` + `postSoundAlarmNotification` into one builder; delete
   dead code.

## Suggested delivery

- **PR 1:** Phase 0–2 (all-decided; fixes the reported bug).
- **PR 2:** Phase 3–4 (unification; Phase 3 still has the transient-id sub-decision).

## Open items before coding

- Confirm `NotificationLevel` renumber (vs. `URGENT(-1)` minimal add).
- Confirm scope of first PR (0–2 vs. 0–4).
- Resolve Phase 3 transient-alarm id strategy.
