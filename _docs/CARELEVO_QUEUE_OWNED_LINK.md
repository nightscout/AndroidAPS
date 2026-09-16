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

What recovers state instead, on every reconnect:

- `CarelevoPumpPlugin.startReconnectAlarmSnapshotObserving` → `readActiveAlarmSnapshots` (`0x43`), diffed
  against a persisted baseline so acknowledged alarms are not resurrected. Level-triggered: it reports
  current state, not edges.
- `readInfusionInfo` (`0x31` → `0x91`) on every status read.
- `CarelevoPumpPlugin.startAutoResumeWatchdog` for the end of a timed pump stop, which is driven by a
  local timer rather than by the patch's push.

The open question this leaves is tracked in
[#4993](https://github.com/nightscout/AndroidAPS/issues/4993): a threshold **crossing** (the low-insulin
notice) is an edge, and a level-triggered snapshot cannot replay an edge — so whether it is recoverable
at all depends on whether the patch reports it as active state.

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
