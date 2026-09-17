# CareLevo: the queue-owned BLE link

Four places in `pump/carelevo` point here. This is the one page that explains how the CareLevo BLE link
is managed, why it works that way, and what it costs.

## The model

The AAPS **CommandQueue owns the link**. `CommandExecutor` calls `Pump.connect()` before it runs
commands and `Pump.disconnect("Queue empty")` once the queue drains — the same for every pump in AAPS,
not something CareLevo does specially. There is no keepalive, no self-reconnect and no scanning.

Three parts implement it:

| Part | Role |
|---|---|
| `CommandExecutor` (core) | connect → run ops → disconnect when the queue is empty |
| `CarelevoConnectionCoordinator` | the border: maps `Pump.connect/disconnect/isConnected` onto the session |
| `CarelevoBleSession` | owns the actual GATT: `requestConnect` / `openLink` / `withSession` / `requestDisconnect` |

`CarelevoBleSession` has two modes over one code path (`withSession`):

- **Queue-owned held link** — `openLink` holds ONE session across a whole connect → run-ops → disconnect
  burst, so a multi-op sequence (cancel-temp then pump-stop, say) does not re-dial between ops. This is
  the normal therapy path once a patch is activated.
- **Transient per-op session** — `transientSession` opens, runs, closes. Used pre-activation, for
  pairing, and for any op with no held link.

Two details that look odd until you know why:

- `CarelevoConnectionCoordinator.isConnected()` returns `true` **pre-activation**, so the queue never
  dials a device that does not exist yet. Post-activation it returns the real held-link state.
- `isInitialized()` is **activation-based, not connection-based**. It has to stay true while the link is
  down, otherwise the loop's `applyTBRRequest` / `applySMBRequest` gate aborts with "pump not
  initialized" during the normal resting state — which is most of the time.
- `CarelevoBleSession.connected` tracks the **held link only**. A transient session must never flip it,
  or the queue would skip `connect()` and re-dial per op.

## How it got here

Worth knowing, because the current design is a deliberate reversal of the first one.

| Commit | What changed |
|---|---|
| (original) | Held the link open and re-dialled itself. `CarelevoConnectionCoordinator` had a `startReconnection` / `stopReconnection` reconnect loop, and `isInitialized()` asked whether the link was up right now. |
| `b95b6b1210` | Moved to the standard AAPS pump lifecycle. `isInitialized()` became activation-based to match Omnipod Dash and Medtrum. **This is where patch-initiated frames became losable.** |
| `d01e7bc7d5` | Reattached the unsolicited-frame bridge the BLE migration had dropped. |
| `0e1a25b47f` | Per-op sessions → the queue-owned held link described above. Strictly better than per-op: the link now spans a whole burst instead of a single command. |
| `bc98244747` | Added the `0x43` active-alarm snapshot, polled on reconnect — the recovery mechanism for what the lifecycle change made losable. Newer-firmware-only. |

## What it costs

**A frame the patch sends while no link is up cannot be received.** It is lost at the radio, before
`BleClientImpl.routeNotification` ever runs, so nothing on the dispatch side can recover it. With the
loop quiet the link is down for minutes at a time — up to `KeepAliveWorker.STATUS_UPDATE_FREQUENCY`
(15 min) when nothing else wakes the queue.

This is accepted, not overlooked. A resting link or a keepalive would make CareLevo the only pump in
AAPS that holds one, and it costs battery and BLE stability for every user.

**The frame is lost; the state behind it is not.** That distinction is what makes the model workable, and
CareMedi confirmed it in [#4993](https://github.com/nightscout/AndroidAPS/issues/4993) with a log from a
real patch:

- **An alarm is patch state, not an edge.** It stays raised on the patch until the app clears it with
  `CMD_ALARM_CLEAR_REQ` (`0x47`), so one raised while no link was up is still raised on the next connect
  and comes back through `0x43`. That is why `applyActiveAlarmSnapshots` does its own edge detection over
  a level poll — the same active alarm reappears in every snapshot until it is cleared.
- Between `0x43` and `0x31`/`0x91` there is **no patch state a push would have told us about that those
  two reads miss**.
- `0x98` `PULSE_FINISH_RPT` and `0x9A` `PULSE_PRESSURE_RPT` are the exception, and deliberately so: they
  are test instrumentation for comparing delivered pulses against delivered insulin. Neither is parsed,
  and neither should be.

What does the recovering, on every reconnect:

- `CarelevoPumpPlugin.startReconnectAlarmSnapshotObserving` → `readActiveAlarmSnapshots` (`0x43`), diffed
  against a persisted baseline so acknowledged alarms are not resurrected. Level-triggered: it reports
  current state, not edges.
- `readInfusionInfo` (`0x31` → `0x91`) on every status read.
- `CarelevoPumpPlugin.startAutoResumeWatchdog` for the end of a timed pump stop, which is driven by a
  local timer rather than by the patch's push.

**What the model does cost is latency, and it is uneven.** An alarm raised while the link is down is not
seen until the queue next connects. For low insulin that is fine - insulin is still being delivered. The
same tiers also carry occlusion, patch error, self-diagnosis failure and auto-off, where delivery has
**already stopped** when the alarm is raised. That is the real trade-off of not holding a link open, and
it is worth re-reading before anyone widens the gap between connects.

### Low insulin is not a notice

Easy to get wrong, because every name on the path says "notice" -
`NoticeThresholdCommand(TYPE_LOW_INSULIN)` sets the threshold, and `AlarmCause` has an
`ALARM_NOTICE_LOW_INSULIN`. The patch does not use it:

| Reservoir | Tier | Flag |
|---|---|---|
| crosses the configured threshold (20..50 U) | **advisory** `0xA5` | `OUT_OF_INSULIN` |
| below 10 U | **critical** `0xA4` | `OUT_OF_INSULIN` |
| notification `0xA6` | — | slot `[2]` is `unused` |

The three tiers are stages on one reservoir axis, not three different conditions. So
`ActiveAlarmSnapshotTier.NOTIFICATION` carrying only `OPERATING_LIFE_EXPIRED` is correct: there is no
low-insulin alarm at notice level to leave out, and the crossing is recovered from the advisory tier.

## Invariants worth not breaking

- **One GATT at a time.** Two GATT clients to one patch cause the status-133 collision. `sessionMutex`
  serializes every session; a session must not run concurrently with any other link to the patch.
- **A closed connection is never reused.** `BleTransportGattConnection.close` is one-shot — it latches
  `closed` and releases the transport's single listener slot — so `openLink` builds a fresh
  connection + client + scope on every connect.
- **`watchForDrop` does not re-dial.** On an unexpected disconnect it tears the held link down and stops.
  The queue is the sole reconnect driver; anything else would reintroduce the reconnect loop that
  `b95b6b1210` removed.
- **The unsolicited handler must not open a session.** `withSession` holds `sessionMutex` for its whole
  duration, so a nested session self-deadlocks.
