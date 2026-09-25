# Insight driver - findings from issue #5132 and from the connection work around it

Everything here was measured on a real Accu-Chek Insight between 2026-09-16 and 2026-09-22. The rig
runs water, not insulin, so the cancelled boluses and interrupted profile writes below were safe to
provoke.

Instrumentation: temporary logging in `MessageQueue` recording every enqueue and send with the
resulting queue order, plus the permanent change that keeps the `IOException` behind a connection
failure. The temporary part is not in the branch.

Several hypotheses in this work were contradicted by later measurement. Where a result rests on one
observation, or on none, it says so.

---

## 1. Answer to issue #5132

### 1.1 Message queue order - correct fix, marginal gain

The sort was wrong: `MessagePriority` is declared `NORMAL, HIGHER, HIGHEST`, sorting ascending put
the most urgent message last, and `nextRequest()` takes index 0. Fixed on `dev` in `aa9497f65c`.

Measured over 33 hours and **10 835 messages sent**:

| queue depth when a message was sent | count | share |
|---|---|---|
| 0 waiting | 10 767 | 99.37 % |
| 1 waiting | 51 | 0.47 % |
| 2 waiting | 17 | 0.16 % |

Maximum depth ever observed: 2. Of those 68 non-empty cases, 28 are the `open / write / close` batch
looking at itself, so real contention is **40 cases out of 10 835 (0.37 %)**, always the
one-per-second alert poll arriving while another message is in flight.

This is structural: every driver call is `requestMessage(...).await()`, and `requestNextMessage()`
only takes a new message once the active one has been answered, so the queue holds at most the alert
poll plus one command message.

**The priority sort never had anything to sort.** Over the same 33 hours, 27 raised-priority
messages were sent - 7 `CancelBolusMessage` (HIGHEST) and 20 `CancelTBRMessage` (HIGHER) - and every
one was enqueued into an empty queue. A bolus cancel went from enqueue to send in 15 ms.

#### The "HIGHEST inside an open session" case cannot arise as described

`SetOperatingModeMessage` is only reachable through `CommandStopPump` / `CommandStartPump`, and
`setNewBasalProfile` through `CommandSetProfile`. Both go through `CommandQueue`, and
`CommandExecutor.drainOnce()` runs one command at a time, so a pump stop cannot overlap a
configuration write. Same for `CancelTBRMessage`.

Only four paths talk to the pump outside that sequential queue:

| path | message | priority |
|---|---|---|
| `InsightPlugin.stopBolusDelivering()` | `CancelBolusMessage` | HIGHEST |
| `InsightAlertService` poll | `GetActiveAlertMessage` | NORMAL |
| `InsightAlertService.mute()` | `SnoozeAlertMessage` | NORMAL |
| `InsightAlertService.confirm()` | `ConfirmAlertMessage` | NORMAL |

So the only HIGHEST message that can arrive concurrently is the bolus cancel, which
`cancelAllBoluses` deliberately launches off the queue. The case can only come from a late bolus
cancel landing during a later configuration write - which is point 3 of the issue.

#### Configuration and history sessions differ

In 8 configuration write sessions no foreign message ever got in: the three messages are enqueued
together, all `NORMAL`, and the sort is stable. History sessions are the opposite, and the analogy
in the issue holds there:

```
23:13:20.648  StartReadingHistory
23:13:20.834  GetActiveAlert        <- foreign STATUS message inside the session
23:13:20.933  ReadHistoryEvents
23:13:21.232  StopReadingHistory
```

#### Not tested

Stopping the pump: zero `SetOperatingModeMessage` in 33 hours. Given the paragraph above it cannot
produce the case the issue worries about, but the box is honestly unticked.

### 1.2 Basal profile write - real, and fixed

#### Two separately committed sessions, measured

`ParameterBlockUtil.writeConfigurationBlock` does not share a session: every
`WriteConfigurationBlockMessage` is wrapped in its own open/close pair by
`InsightConnectionService.requestMessage`. On the wire:

```
22:57:07.561  Open    |
22:57:07.907  Write   | session 1 - ActiveBRProfileBlock
22:57:08.001  Close   | committed
                        <- gap
22:57:08.202  Open    |
22:57:08.324  Write   | session 2 - BRProfile1Block
22:57:08.523  Close   | committed
```

Gap between the close of session 1 and the open of session 2, over five profile writes:
**201, 289, 311, 332 and 314 ms**.

#### On TebbeUbben's comment

> *"as long as the active basal profile and the actual profile blocks are configured within the same
> session, a connection drop should not cause an issue. I remember to have implemented it that way"*

The recollection does not match the code and never did. The original driver wrote only the profile
block, into whatever profile was already active. The `ActiveBRProfileBlock` write was added later in
`286be1e78f` ("Insight: Enforce using first profile"), already as a second `writeConfigurationBlock`
call.

> *"Configuration blocks are only committed when the configuration write session is finished."*

This part is right, and was verified on the pump. A temporary hook held the session open and the
Bluetooth was cut after the pump had acknowledged the `ActiveBRProfileBlock` write but before the
close:

```
00:21:39.737  SEND Open    -> answered
00:21:40.104  SEND Write   -> answered   <- pump acknowledged "switch to PROFILE_1"
00:21:40.237  held 10 s, session still open
      ^ Bluetooth cut here
00:21:50.239  close attempted on a dead link
```

The queue only advances on an answer, so the pump did acknowledge both. **The pump stayed on
PROFILE_2, and was still on PROFILE_2 after a full restart.** *One observation - a second run
reproduced the conditions but the reading was lost while handling the pump alarm.*

> *"There's also a command to cancel an ongoing write session and rollback all changes."*

Not implemented in AAPS: `app_layer/configuration/` holds only `Open`, `Write` and `Close`.

#### Side effect, reproduced 2 out of 2

Cutting the link inside a configuration session made the pump raise an **M27 data transfer error,
stop, and require a manual acknowledgement plus a restart from its menu**. Basal delivery stops and
the user has to act on the pump.

*Caveat: the test held the session open for 10 s first, and it was not isolated whether the alarm
comes from that pause or from the disconnection.*

#### Where the danger sat

```
            Open A - Write A - Close A | 314 ms | Open B - Write B - Close B
session      [======= open =======]      closed   [======= open =======]  closed

pump        profile 2                 | profile 1 + OLD rates          | profile 1
holds       old rates                 |                                | new rates
```

The moment `Close A` commits, the switch is real while `PROFILE_1` still holds the old rates. And
the 314 ms gap is the worst place to lose the link:

| connection lost | session open? | M27? | pump configuration | does the user notice? |
|---|---|---|---|---|
| during session A | yes | yes | untouched | yes, loud alarm |
| **in the gap** | **no** | **no** | **profile 1 + old rates** | **no, nothing** |
| during session B | yes | yes | profile 1 + old rates | yes, alarm |

The one window that leaves a wrong basal profile is the one window that is silent.

Recovery exists: `KeepAliveWorker` calls `isThisProfileSet()` and posts `EventProfileChangeRequested`.
Measured on the failed write, the profile was back about **4 minutes** later.

#### What was done

1. **Swap the two writes** - rates first, activation second. On its own this closes the therapy risk:
   starting from a pump on PROFILE_2, no cut position leaves the user on a wrong basal profile.
2. **Put both blocks in one session** - `writeConfigurationBlocks` sends one open, both writes, one
   close. Since an unclosed session is not committed, a connection lost anywhere leaves the pump
   exactly as it was. It is also shorter: 4 messages instead of 6.

**Neither prevents the M27.** What they remove is the *silent* failure. The single session depends on
the pump accepting several writes inside one session, which had never been done before; profile
writes have since run with it on a real pump without failure.

`InsightPlugin` had no unit test at all, so a first one covers the write order, the single session,
the block conversion and the disconnected case.

### 1.3 `stopBolusDelivering()` without an active bolus

#### The recording is not a problem, verified on the pump

`deliverTreatment` writes the bolus once, at the start, with the **requested** amount. The
`while (!bolusCancelled)` loop only feeds the progress dialog - it never writes to the database. The
real amount arrives from the history: `readHistory()` -> `processBolusDeliveredEvent` calls
`syncBolusWithPumpId` again with the same `pumpId`, and `SyncPumpBolusTransaction` updates the row in
place. An 8 U bolus cancelled after 6 U:

```
23:12:54.822  Inserted  pumpId=700  amount=8.0   <- requested
23:13:21.228  Updated   pumpId=700  amount=6.0   <- actually delivered
```

The database **over-states** insulin until the correction lands, never under-states it.

#### Repeated bolus ids are not a concern

Logs in issue #645 show `event.bolusID` at 34776 on 2021-09-24 and 35109 on 2021-10-01: an
incrementing counter, about 55 per day, in a `readUInt16LE` field, so it wraps after more than three
years. The +/-3 day window in `getInsightBolusID` comes from the original driver. No report was
found where a repeated id was actually observed; the root cause in #645 was a one-day shift in the
pump event date.

#### The race is real, for another reason

`stopBolusDelivering()` does not remember which bolus it was asked to cancel: it sends
`CancelBolusMessage` with whatever `bolusID` holds at that moment. If a new bolus started in between,
the cancel hits the new one. The database still ends up correct, but the insulin was not delivered.
A plain "bolus in flight" flag does not cover that, because a bolus *is* in flight - just not the one
the user meant to cancel. A counter captured when `stopBolusDelivering()` is called, and re-checked
under `_bolusLock`, would. **Not implemented.**

---

## 2. A regression that matters more than the three points above

Found by following a user's report that red alerts had become far more frequent without any change
in use. That report was right, and three days of measurement had walked past it.

### What happened

`InsightPlugin` used to expose `lastDataTime` as a getter reading the connection service, so every
reader got the live value:

```kotlin
override val lastDataTime: Long
    get() = if (connectionService == null || alertService == null) dateUtil.now()
            else connectionService?.lastDataTime ?: 0
```

`ac0a8ff3ac` ("Pump interface -> flow", 18 March 2026) turned it into a StateFlow that the plugin
only pushes **while fetching the pump status**, which happens once every 15 minutes. Between two
status reads the value stands still even though the pump is answering commands.

Every other driver exposes a flow fed by its communication layer - `danaPump.lastConnectionFlow`,
`medtrumPump.lastConnectionFlow`, `_lastConnectionTimestamp` for Combo v2. Insight was the only one
pushing it by hand, because it had no such flow to bind to; the migration put the assignment where
the code already needed a value.

### Why it produces red alerts

The pump unreachable alarm is timed off that value:

```kotlin
val lastConnection = pump.lastDataTime.value
val isStatusOutdated = lastConnection + 15 min < now
// alarm when the age passes the threshold, 30 minutes by default
```

Before, any exchange with the pump refreshed it - temp basal, bolus, status read - so the alarm only
fired when the pump was genuinely unreachable for 30 minutes. After, **one delayed or dropped status
read is enough**, while temp basals keep going through normally. To a user the alarm says "pump
unreachable" in both cases.

### Measured, before and after

Before the fix, with the pump connecting every few minutes:

```
13:41:32  "Last connection: 13:31"
13:46:35  "Last connection: 13:31"     <- while the pump had connected at 13:46:15
```

A quarter of an hour behind. After the fix:

```
23:12:42  "Last connection: 23:12"
23:17:42  "Last connection: 23:17"     <- last real connection 23:17:10
```

Within 30 seconds of reality.

### Fix

`InsightConnectionService` already stamps the time whenever the pump answers, so it now exposes that
as a flow and the plugin follows it. While the service is unbound the plugin reports "now", which is
what the old getter did.

*This is verified on the mechanism, not yet on daily use: showing that red alerts disappear needs
several days without one on a reachable pump.*

---

## 3. Connection reliability

### What was measured

33 hours of ordinary closed-loop running, then two hours with the radio quiet:

| | with another device on the radio | radio quiet |
|---|---|---|
| socket attempts failing | **72 %** | **41 %** |
| attempts per successful connection | 2.25 | 1.71 |
| commands dropped at the 119 s budget | 41 (1.24/h) | **0** in 2h20 |

Every connection series eventually succeeded in both cases. The 41 % and 72 % are failure rates
**per attempt**, not per connection.

### First cause - another device owning the radio

`dumpsys bluetooth_manager` showed another paired device, switched off, being retried by the phone in
a permanent loop: one cycle every 7.45 s of which 6.44 s is active paging, around the clock.
Bluetooth classic pages one device at a time, so the pump's request had to fit in the 1 s gaps.

Stopping the companion app that drove it cut the contention to about 2.7 % of radio time, and the
dropped commands went to zero. **This is not an exotic situation** - a watch that is not worn, a car
kit out of range, earbuds in a drawer would all do it - so a driver has to survive it.

### Second cause - the pump does not answer the first page, and why is unknown

Lining up the system's ACL links against the driver's attempts, **no ACL link exists for the failed
attempts**. The phone pages the pump and gets nothing back, so the failure is below RFCOMM, below SDP
and below the socket. The phone gives up after **6.45 s**, reproducible to 30 ms, which is the
controller's page timeout and not something an app can change.

Several explanations were tried and each was contradicted:

| hypothesis | why it was dropped |
|---|---|
| a spent socket being reused | failure durations identical after fixing it |
| the first page wakes the pump, so the 2nd attempt always works | a series later needed 6 attempts |
| duty-cycled listening | probed every 16 s the pump answered 20 times out of 20 |
| the pump sleeps as idle time grows | a sweep found the first attempt doing *better* at 60 s idle than at 10 s |

Over 24 series with a quiet radio the distribution was 11 series of one attempt, 12 of two, and one
of six. **Why the pump ignores a page is not established.**

### What the driver can and cannot do

Nothing in AAPS can make a pump answer a page it does not answer. Quick retries, jitter or a longer
budget reduce the **cost** of these failures, not their number.

The one lever that removes them is not disconnecting - no reconnection, no page, no failure, and an
accidental five-minute test of it passed without a single incident. **It is ruled out on battery
grounds**: the current disconnect behaviour already took pump autonomy from about a month to about
fifteen days, and it cannot be spent again. The 5 s disconnect delay is not an arbitrary setting; it
is a compromise that was already paid for.

### Two defects found on the way, neither of them a remedy

A `BluetoothSocket` cannot be connected twice. `handleException` dropped it only when the failed
attempt had taken a second or less, and no failure is ever that quick - the fastest of 579 took
2.0 s - so every retry reconnected the same spent socket. `ConnectionEstablisher.close()` had the
mirror problem: `if (closeSocket && it.isConnected) it.close()`, and a socket whose `connect()`
failed is not connected, so the one case that needed releasing was never closed and its thread never
interrupted. Both come from the original Java.

Both are fixed, but **measurements afterwards show the same failure durations**, so neither explains
the failures. Correctness, not a remedy.

### Ideas considered and not implemented

- **Jitter on the retry delay.** Under the periodic competitor above, attempts 2 and 3 of a series
  succeeded 12.5 % and 8.7 % of the time while attempts 1 and 4 succeeded about 55 %, with failure
  durations reproducible to 20 ms - the shape of two fixed periods staying in step. Spreading the
  wait should decorrelate them. **Never measured**, so not proposed.
- **Quick first retries.** Arithmetic says about 4 s saved on the half of connections that need a
  second attempt. The premise - that the second attempt almost always works - held 23 times out of 24
  but has no known mechanism, and with the alarm regression fixed the saving is cosmetic. **No
  controlled measurement**, so not proposed.
- **A per-driver connection budget.** `PUMP_MAX_CONNECTION_TIME_IN_SECONDS = 119` is a global
  constant; `Pump.waitForDisconnectionInSeconds()` is already per-driver, so the precedent exists.
  Shared code, so it needs the maintainers' opinion.

### What actually made this findable

Keeping the `IOException` and logging it with the attempt duration. Before that every failure was one
indistinguishable line and the only evidence was timing.

---

## 4. Bluetooth cannot be switched on by AAPS from Android 13

Findings only - nothing was changed. This affects every Bluetooth pump driver, so it wants the
maintainers' opinion first.

### What happens

`BluetoothAdapter.enable()` was taken away from ordinary apps in Android 13 (API 33). The helper in
`core/utils/.../BluetoothAdapterExtension.kt` knows this and returns `false` from API 33 up, and its
comment explains there is nothing to migrate to. **The problem is that no caller reads the answer.**

### Consequence 1 - two seconds burned per retry, forever

`ConnectionEstablisher.run()`:

```kotlin
if (!bluetoothAdapter.isEnabled) {
    bluetoothAdapter.safeEnable()   // returns false on Android 13+, result discarded
    sleep(2000)
}
// ... then createInsecureRfcommSocketToServiceRecord, which throws because the adapter is off
```

With Bluetooth off this repeats for as long as the loop wants the pump:

```
09:00:33.692 Insight state changed: CONNECTING
09:00:35.721 Exception occurred: ConnectionFailedException after 0 ms (cause: IOException: null)
09:00:36.017 Insight state changed: RECOVERING
09:00:41.914 Insight state changed: CONNECTING
09:00:43.936 Exception occurred: ConnectionFailedException after 0 ms (cause: IOException: null)
```

Exactly 2.0 s from `CONNECTING` to the failure - the `sleep(2000)` - and a duration of 0 ms, which is
the socket-creation branch of `onConnectionFail(e, 0)`. It ran about 105 seconds until the radio was
switched on by hand, then the pump connected first try.

### Consequence 2 - the Bluetooth watchdog is dead on Android 13+

```kotlin
override val canRestartBluetooth: Boolean get() = true

override suspend fun restartBluetooth() {
    adapter.safeDisable(0)   // no-op on Android 13+
    delay(1000)
    adapter.safeEnable(0)    // no-op on Android 13+
    delay(1000)
}
```

`CommandExecutor` says what was meant:

> `canRestartBluetooth` is part of the condition, not a check inside the branch: where the platform
> cannot toggle the radio this must behave exactly as it does when the user has the watchdog switched
> off, rather than "barking" and doing nothing.

That is exactly what happens now. The implementation contradicts its own design note, and there is a
preference that cannot work on any phone running Android 13 or later.

### Consequence 3 - the user is never told

AAPS cannot switch the radio on, so the only useful thing it can do is say so. Today it retries
silently and reports a connection failure, which looks identical to a pump out of range or a flat
pump battery.

### Possible fixes

1. `canRestartBluetooth` should reflect the platform: `Build.VERSION.SDK_INT < TIRAMISU`. One line,
   and it restores the behaviour the design note asks for.
2. `ConnectionEstablisher` should not sleep when `safeEnable()` returned false.
3. Surface "Bluetooth is off" to the user, since no driver can fix it by itself.

### Other callers with the same pattern

- `BlePreCheckImpl` - `safeEnable(3000)` twice, result unused.
- `pump/danars/.../BleTransportImpl.kt` - `safeEnable()`, result unused.
- `pump/insight/.../InsightPairState.kt` - `safeDisable`.
