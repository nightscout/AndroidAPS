# Carelevo BLE Transport Migration (Phase 1) — review notes (2026-07-08)

## Architecture
- Shared fleet abstraction: `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/pump/ble/BleTransport.kt`
  (`BleTransport`/`BleAdapter`/`BleScanner`/`BleGatt`/`BleTransportListener`). Used by Dana/Equil/Medtrum/Carelevo.
  `BleGatt.writeCharacteristic(data)` and `.enableNotifications()` have NO uuid param — the transport
  hardcodes a single write char + single notify char internally (one-service/one-write/one-notify patch
  pump shape). `BleTransportListener.onCharacteristicWritten()` carries no status (unlike
  `BluetoothGattCallback.onCharacteristicWrite`'s status int) — silently-dropped writes degrade to the
  caller's own `withTimeout`, they don't fast-fail. Single listener via `setListener()` (last write wins,
  no ownership enforcement).
- `pump/carelevo/.../ble/gatt/BleTransportGattConnection.kt` is a NEW adapter bridging `BleTransport` to
  carelevo's own pre-existing `GattConnection` contract, so `BleClientImpl` (coroutine correlation layer)
  runs unchanged over either the old `AndroidGattConnection` (direct `BluetoothGatt` wrapper) or the new
  shared-transport adapter. Mirrors `AndroidGattConnection`'s internals almost line for line (gattMutex +
  3x `@Volatile CompletableDeferred` for write/discovery/descriptor acks).
- `pump/equil/.../ble/EquilBleTransportImpl.kt` is the template `CarelevoBleTransportImpl.kt` was cloned
  from — near-identical structure (inner Adapter/Scanner/Gatt classes, `@Synchronized` gattCallback for
  conn-state/svc-discovery/descriptor-write, unsynchronized for char write/read/changed).

## Confirmed non-issues (checked against Equil precedent, don't re-flag)
- **Double `@Singleton`** (class-level `@Singleton class XxxBleTransportImpl @Inject constructor` PLUS
  `@Provides @Singleton fun provideXxxBleTransport(impl: XxxBleTransportImpl): XxxBleTransport = impl`)
  is the established fleet pattern, not a bug. See `app/src/withPumps/kotlin/app/aaps/di/EquilModules.kt`
  — this indirection exists so the app module can swap in an emulator transport
  (`EquilEmulatorBleTransport`) at runtime while the impl module's own `@Module class` (non-abstract, so
  no `@Binds`) still exposes the concrete singleton. Carelevo's `CarelevoBleModule.kt` binds it locally
  instead (no emulator swap yet — KDoc says "a future emulator impl can be swapped in here"). Functionally
  identical either way (Dagger caches per scoped binding, exactly one instance).
- Synchronous listener callback firing INSIDE `gatt.writeCharacteristic()` on the not-connected path
  (`listener?.onConnectionStateChanged(false)`) correctly aborts the just-registered `writeAck` deferred
  with no deadlock risk — `completeExceptionally` never tries to reacquire `gattMutex`, and it all runs on
  the same coroutine/call-stack that already holds the mutex from `writeCharacteristic`'s `withLock` block.
  Verified via `BleTransportGattConnectionTest` `writeCharacteristic on a not-connected transport fast-
  fails...` test — legit coverage, not trivially green.
- `CarelevoBleTransportImpl.onServicesDiscovered` tightening the success condition to
  `service != null && notifyChara != null && writeChara != null` (vs Equil's bare `service != null`) is a
  **safety improvement**, not a regression — Equil's version can report false "success" if the service is
  found but a characteristic UUID lookup inside it fails (firmware/UUID mismatch), only surfacing the real
  failure later on the first write/enableNotifications call.
- UUID role wiring verified correct: `BleEnvConfig`/`CarelevoConfig.kt` — service `e1b40001`, TX(notify)
  `e1b40003` → `notifyChara`, RX(write) `e1b40002` → `writeChara`, CCCD `2902`. Matches Equil's analogous
  NRF_UART_NOTIFY/NRF_UART_WRITE mapping.

## Real findings (see full review text in conversation 2026-07-08 for details — recap here)
- **High**: `BleTransportGattConnection` has no self-tracked "closed" flag. `close()` (lines ~122-130)
  nulls the transport's listener slot (`transport.setListener(null)`) but a *subsequent* call to
  `writeCharacteristic`/`discoverServices`/`enableNotifications` on the same (now-closed) adapter
  re-acquires `gattMutex` uncontended, registers a fresh deferred, and calls into the transport — whose
  not-connected fast-fail path (`listener?.onConnectionStateChanged(false)`) is now a silent no-op because
  the listener is null. The deferred is never completed → hangs until the caller's own `withTimeout`
  instead of failing fast. `AndroidGattConnection` avoids this by self-guarding with
  `val g = gatt ?: throw GattWriteException(...)` at the top of every suspend op (checks its own nulled
  `gatt` field, doesn't depend on a callback round-trip). Not yet reachable in production (adapter isn't
  wired into any real call site yet — grep confirms only the test file constructs it), but must be fixed
  before Phase 2 wiring.
- **Medium-High (latent/Phase-2)**: Listener-slot ownership has zero enforcement. `CarelevoBleTransportImpl`
  is `@Singleton`; `BleTransportGattConnection` is meant to be "one adapter instance owns the transport's
  single listener slot for one connection lifecycle" (its own KDoc) but nothing asserts this. If a future
  reconnect path ever constructs a new adapter without `close()`-ing the previous one first, the new
  adapter's `init { transport.setListener(this) }` silently steals the slot; the old adapter's in-flight
  deferred(s) hang until timeout with no error surfaced.
- **Medium**: `writeCharacteristic(uuid, payload, withResponse)`'s `uuid` and `withResponse` params, and
  `enableNotifications(uuid)`'s `uuid` param, are silently ignored by `BleTransportGattConnection` (the
  underlying `BleGatt` interface has no per-uuid/write-type parameters — single hardcoded write/notify
  char). No `require()` guard, no doc comment (contrast with the write-ack-status KDoc it does have).
  A caller passing the wrong UUID (e.g. copy-paste bug) would silently "succeed" against the transport's
  one real characteristic instead of erroring.
- **Medium (pre-existing, not a regression)**: `writeAck`/`discoveryAck`/`descriptorAck` are bare mutable
  fields, not per-op correlation tokens. A late/stale native ack for an aborted operation could in theory
  complete a *different*, newer operation's deferred after `gattMutex` released and a new op started. This
  risk is identical in `AndroidGattConnection` (same design) — not introduced by the new adapter, just
  worth a shared follow-up someday.
- **Test gaps**: no test for close()-then-reuse (would have caught the High finding above); no test for
  double-close() idempotency (interface explicitly promises it); no test characterizing two adapters over
  one transport clobbering the listener slot. Existing tests (register-before-write, synchronous not-
  connected fast-fail, disconnect aborting in-flight write, full BleClient-over-adapter correlation
  end-to-end via nested `scope.launch` + `runTest` virtual time) are legitimate, not trivially green.

## See also
- [MEMORY.md](MEMORY.md) main index
