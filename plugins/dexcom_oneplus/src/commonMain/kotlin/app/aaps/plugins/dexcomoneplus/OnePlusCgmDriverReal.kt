package app.aaps.plugins.dexcomoneplus

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattClient
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorIdentity
import app.aaps.plugins.dexcomoneplus.identity.OnePlusSensorStore
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.oem.DeviceProfileRegistry
import app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScanner
import app.aaps.plugins.dexcomoneplus.scan.OnePlusBleScannerStub
import app.aaps.plugins.dexcomoneplus.scan.OnePlusScanListener
import app.aaps.plugins.dexcomoneplus.scan.OnePlusScanResult
import app.aaps.plugins.dexcomoneplus.session.OnePlusBleSession
import app.aaps.plugins.dexcomoneplus.session.OnePlusMacArbiter
import app.aaps.plugins.dexcomoneplus.session.OnePlusBleSessionSkeleton
import app.aaps.plugins.dexcomoneplus.session.OnePlusConnectPrep
import app.aaps.plugins.dexcomoneplus.session.OnePlusSessionAuthKeks
import app.aaps.plugins.dexcomoneplus.session.OnePlusSessionStart
import kotlin.concurrent.Volatile
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch

internal object OnePlusCgmDriverResumePolicy {
    fun canResume(storedSession: OnePlusStoredSession?): Boolean =
        storedSession?.lastMac?.isNotBlank() == true &&
            OnePlusSessionStart.isValidPairingCode(storedSession.identity.pin) &&
            storedSession.sharedKey?.size == SHARED_KEY_SIZE

    private const val SHARED_KEY_SIZE = 16
}

/**
 * Real driver: LE scan + GATT + KEKS + Control EGV loop.
 *
 * ⚠️ ASYNC IMPACT: scan on binder; connect/auth/EGV on [bleExecutor] (blocks that thread).
 * [disconnect] / [shutdown] call [OnePlusBleSession.stop] directly so GATT disconnect can
 * unblock [OnePlusGattClient.awaitControlNotify]. Watchers may be off main.
 *
 * ## One driver, two platforms
 *
 * This is shared code. It was Android only for as long as it used an Android clock and the JVM's
 * threads and collections, none of which it needed for what it decides: everything the phone
 * really owns is behind [OnePlusPlatform] and its four members. Keep it that way - a rule that
 * lands here rather than behind the port is a rule only one of the two phones follows.
 *
 * @param storeNamespace per-slot key-value namespace for the sensor store (null = the original
 *   single-sensor file; "staging" = the pre-soak second sensor). Lets two driver instances run
 *   concurrently without sharing identity / MAC / KEKS key / ingest markers. **A second instance
 *   needs a platform that allows two Bluetooth scans at once**, as Android does.
 */
@OptIn(ExperimentalAtomicApi::class)
class OnePlusCgmDriverReal(storeNamespace: String? = null) : OnePlusCgmDriver {

    /**
     * Which preferences file this instance's sessions read and write.
     *
     * A `var` and not a constructor `val`: a promotion hands a **live link** from the staging slot
     * over to production, and the link must not be dropped — but from that moment every new session
     * this instance opens has to read and write the production file. See [rebindStore].
     */
    @Volatile
    private var storeNamespace: String? = storeNamespace

    /**
     * `prod` / `staging` — logged on every marker so a dual-sensor trace is attributable.
     *
     * Fixed for the whole life of the instance, even after a promotion, so a bug report stays
     * readable across the swap: a promoted instance is production but still calls itself "staging"
     * in the log.
     */
    private val slot: String = OnePlusLogMarkers.slotOf(storeNamespace)

    /**
     * [OnePlusMacArbiter] owner token for this instance: unique for its whole life, unlike [slot]
     * which the next staging instance reuses. Keying the arbiter on [slot] would let that next
     * pre-soak's claim silently release the promoted instance's claim on the sensor still feeding
     * the loop — see [OnePlusMacArbiter]'s warning.
     */
    private val arbiterOwner: String = "$slot#${nextInstance.incrementAndFetch()}"

    /**
     * Guards [watchers] only, never anything else.
     *
     * This used to be a `CopyOnWriteArrayList`, which has no multiplatform form. The list is read
     * far more often than it is written and it is read while a session thread is running, so
     * [watchersSnapshot] copies it under this lock and the callers walk the copy - which is exactly
     * what iterating a copy-on-write list did. [lifecycleLock] is never taken from inside this one,
     * so the two cannot deadlock against each other.
     */
    private val watchersLock = AapsLock()
    private val watchers = mutableListOf<OnePlusGlucoseWatcher>()
    private val lifecycleLock = AapsLock()

    /** The host this driver is attached to. Null until [attach]. */
    @Volatile
    private var platform: OnePlusPlatform? = null
    private var operationGeneration = 0L
    private var resumeQueued = false

    @Volatile
    private var bleExecutor: OnePlusBleExecutor = onePlusBleExecutor()

    @Volatile
    private var scanner: OnePlusBleScanner = OnePlusBleScannerStub()

    @Volatile
    private var session: OnePlusBleSession? = null

    @Volatile
    private var profile: OemDeviceProfile = DeviceProfileRegistry.GenericFallback

    @Volatile
    private var sensorStore: OnePlusSensorStore? = null

    /** Last ADV name seen for [connect] target — persisted on auth success. */
    @Volatile
    private var pendingDeviceName: String? = null

    /**
     * When the ADV sighting handed off from the UI for the [connect] target was seen, on the
     * monotonic clock of [onePlusElapsedMs] (0 = none). Lets [prepareConnect] skip the blind
     * re-scan when the sensor was just seen.
     */
    @Volatile
    private var pendingAdvSightingElapsedMs: Long = 0L

    override fun attach(platform: OnePlusPlatform) {
        this.platform = platform
        val store = storeFor(platform, storeNamespace)
        sensorStore = store
        scanner = platform.scanner(hint = store.load(), slot = slot)
        profile = DeviceProfileRegistry.resolve()
    }

    private fun storeFor(platform: OnePlusPlatform, namespace: String?): OnePlusSensorStore =
        OnePlusSensorStore(platform.sensorPrefs(OnePlusSensorStore.prefsFileName(namespace)))

    fun sensorStore(): OnePlusSensorStore? = sensorStore

    /**
     * Points this instance at another slot's preferences file, without dropping a running link.
     *
     * Used once, by a promotion. The running Control/EGV loop must follow the new file at once: it
     * reconnects itself every few minutes and never ends before an app restart, and the promotion
     * clears the staging file right after this call. So the session reads `sensorStore` on every
     * call instead of keeping it in a local (see `createSession`).
     */
    fun rebindStore(namespace: String?) {
        storeNamespace = namespace
        platform?.let { sensorStore = storeFor(it, namespace) }
    }

    fun saveIdentity(identity: OnePlusSensorIdentity) {
        sensorStore?.saveIdentity(identity)
        scanner.updateSessionHint(sensorStore?.load())
    }

    /**
     * KEKS key of the slot this instance owns **right now**, for the running session's short auth.
     *
     * Read through the live field, never through a captured store: a promotion rebinds this instance
     * under its own running link (see [rebindStore]).
     */
    fun savedSharedKey(): ByteArray? = sensorStore?.load()?.sharedKey

    /**
     * Let go of a session that has ended on its own, so the reconnect watchdog can start a new one.
     *
     * `startWithPairingCode` blocks for the whole life of the link and returns when the link is
     * over. That includes the terminal `OnePlusBleSession.fail` path (for example after
     * `ONEPLUS_RECONNECT_EXHAUSTED` or a bad PIN), which sets its own `running` flag to false and
     * **returns normally instead of throwing**. Before this, nothing then cleared [session] and
     * [resumeQueued], so the driver stayed "busy" for ever with a link that was down:
     * `isSessionUp()` said false, while every wake-up of the plugin's reconnect watchdog got the
     * answer "auto-resume already active". The only ways out were an app restart or a manual
     * Connect, and no glucose came in until then.
     *
     * Guarded on both the operation generation and the session identity: a newer connect may have
     * replaced this one while its thread was ending, and the old session must not release the newer
     * one.
     */
    private fun releaseSessionIfCurrent(generation: Long, ended: OnePlusBleSession) {
        lifecycleLock.withLock {
            if (generation != operationGeneration || session !== ended) return
            session = null
            resumeQueued = false
        }
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: [$slot] session released — the watchdog may start a new one",
        )
    }

    /** Persist the MAC / KEKS key of a successful auth into the slot this instance owns right now. */
    fun onAuthSucceeded(address: String, key: ByteArray) {
        val store = sensorStore
        store?.saveLastMac(address)
        store?.saveSharedKey(key)
        pendingDeviceName?.let { store?.saveLastDeviceName(it) }
        scanner.updateSessionHint(store?.load())
    }

    /** Forget the stored KEKS key of the slot this instance owns right now (auth refused it). */
    fun onAuthInvalidated() {
        val store = sensorStore
        store?.clearSharedKey()
        scanner.updateSessionHint(store?.load())
    }

    override fun addWatcher(watcher: OnePlusGlucoseWatcher) {
        watchersLock.withLock {
            if (!watchers.contains(watcher)) watchers.add(watcher)
        }
    }

    override fun removeWatcher(watcher: OnePlusGlucoseWatcher) {
        watchersLock.withLock { watchers.remove(watcher) }
    }

    /**
     * The watchers as they are right now, for a caller that is about to walk them.
     *
     * A copy, so a watcher that adds or removes one while it is being told something cannot change
     * the list under the walk. That is what iterating a copy-on-write list gave before.
     */
    private fun watchersSnapshot(): List<OnePlusGlucoseWatcher> =
        watchersLock.withLock { watchers.toList() }

    override fun startScan(listener: OnePlusScanListener) {
        OnePlusLog.i("${OnePlusLogMarkers.SCAN}: [$slot] start")
        scanner.startScan(listener)
    }

    override fun stopScan() {
        OnePlusLog.i("${OnePlusLogMarkers.SCAN}: [$slot] stop")
        scanner.stopScan()
    }

    override fun connect(deviceAddress: String, pairingCode: String) {
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: [$slot] connect requested (Real GATT+KEKS+EGV)",
        )
        // The transmitter has one owner — see OnePlusMacArbiter. Refused means the other slot is on
        // this sensor, and opening a second session would corrupt both KEKS handshakes. Nothing is
        // written to the store on a refusal: the slot must stay exactly as it was.
        if (!OnePlusMacArbiter.claim(deviceAddress, arbiterOwner)) {
            watchersSnapshot().forEach {
                it.onError("ONEPLUS_SESSION: sensor already in use by the other slot", false)
            }
            return
        }
        scanner.stopScan()
        // Stop any in-flight reconnect loop (may still be targeting a previous MAC).
        // The three values come back as one result: `withLock` declares no callsInPlace contract,
        // so a `val` cannot be assigned inside the block.
        val (previousSession, generation, executor) = lifecycleLock.withLock {
            operationGeneration++
            val newGeneration = operationGeneration
            val previous = session
            session = null
            resumeQueued = false
            Triple(previous, newGeneration, bleExecutor)
        }
        previousSession?.stop("superseded")
        sensorStore?.saveIdentity(
            OnePlusSensorIdentity(
                pin = pairingCode,
                serial = sensorStore?.load()?.identity?.serial,
                gtin = sensorStore?.load()?.identity?.gtin,
                rawGs1 = sensorStore?.load()?.identity?.rawGs1,
            ),
        )
        sensorStore?.saveLastMac(deviceAddress)
        // Fresh (re)pairing from the UI: drop any stale KEKS shared key so attempt 0 runs full
        // J-PAKE instead of a short-auth that the transmitter rejects (field: stale preload →
        // auth=2 → a wasted connect cycle + FAILED). A successful auth re-persists a new key, so
        // silent reconnects inside the session loop still short-auth.
        sensorStore?.clearSharedKey()
        scanner.updateSessionHint(sensorStore?.load())
        try {
            lifecycleLock.withLock {
                if (generation != operationGeneration || executor !== bleExecutor) return
                executor.execute {
                    val created = lifecycleLock.withLock {
                        if (generation != operationGeneration) return@execute
                        createSession(
                            requestNewSensorStart = true,
                            generation = generation,
                        ).also { session = it }
                    }
                    try {
                        created.startWithPairingCode(deviceAddress, pairingCode)
                    } catch (t: Throwable) {
                        OnePlusLog.e("${OnePlusLogMarkers.ERROR}: [$slot] ${t.message}", t)
                        watchersSnapshot().forEach {
                            it.onError(t.message ?: "ONEPLUS_CONNECT_FAILED", fatal = false)
                            it.onWarmup(
                                OnePlusWarmupState(
                                    phase = OnePlusWarmupState.Phase.FAILED,
                                    message = t.message,
                                ),
                            )
                        }
                    } finally {
                        releaseSessionIfCurrent(generation, created)
                    }
                }
            }
        } catch (t: Throwable) {
            OnePlusLog.e(
                "${OnePlusLogMarkers.ERROR}: [$slot] connect queue ${t.message}",
                t,
            )
        }
    }

    /**
     * Restores an authenticated sensor after process/plugin recreation without clearing its KEKS
     * key and without allowing SessionStart.
     *
     * ⚠️ ASYNC IMPACT: queues at most one resume on [bleExecutor]. The resumed session itself owns
     * persistent ADV waiting and all later reconnects.
     */
    override fun resumeStoredSession(): Boolean {
        val stored = sensorStore?.load()
        if (!OnePlusCgmDriverResumePolicy.canResume(stored)) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SESSION}: [$slot] auto-resume skipped — stored session incomplete",
            )
            return false
        }
        val deviceAddress = stored?.lastMac ?: return false
        // The path that reproduced the collision on every plugin start for an install whose two
        // stores already held the same MAC. Claim before queueing anything.
        if (!OnePlusMacArbiter.claim(deviceAddress, arbiterOwner)) {
            OnePlusLog.w(
                "${OnePlusLogMarkers.SESSION}: [$slot] auto-resume skipped — the other slot owns this sensor",
            )
            return false
        }
        val pairingCode = stored.identity.pin
        // One result from the lock, see connect(): `withLock` declares no callsInPlace contract.
        val (generation, executor) = lifecycleLock.withLock {
            if (resumeQueued || session != null) {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.SESSION}: [$slot] auto-resume already active",
                )
                return false
            }
            resumeQueued = true
            operationGeneration++
            val queuedGeneration = operationGeneration
            val queuedExecutor = bleExecutor
            // A stored device name is identity metadata, not a fresh UI ADV selection.
            pendingDeviceName = null
            pendingAdvSightingElapsedMs = 0L
            scanner.updateSessionHint(stored)
            queuedGeneration to queuedExecutor
        }
        OnePlusLog.i(
            "${OnePlusLogMarkers.SESSION}: [$slot] auto-resume queued mac=***${deviceAddress.takeLast(5)} " +
                "savedKey=true newStart=false",
        )
        try {
            lifecycleLock.withLock {
                if (generation != operationGeneration || executor !== bleExecutor) return false
                executor.execute {
                    val created = lifecycleLock.withLock {
                        if (generation != operationGeneration) return@execute
                        createSession(
                            requestNewSensorStart = false,
                            generation = generation,
                        ).also { session = it }
                    }
                    try {
                        created.startWithPairingCode(deviceAddress, pairingCode)
                    } catch (t: Throwable) {
                        OnePlusLog.e(
                            "${OnePlusLogMarkers.ERROR}: [$slot] auto-resume ${t.message}",
                            t,
                        )
                        watchersSnapshot().forEach {
                            it.onError(t.message ?: "ONEPLUS_AUTO_RESUME_FAILED", fatal = false)
                        }
                    } finally {
                        releaseSessionIfCurrent(generation, created)
                    }
                }
            }
        } catch (t: Throwable) {
            lifecycleLock.withLock {
                if (generation == operationGeneration) {
                    resumeQueued = false
                }
            }
            OnePlusLog.e(
                "${OnePlusLogMarkers.ERROR}: [$slot] auto-resume queue ${t.message}",
                t,
            )
            return false
        }
        return true
    }

    /**
     * UI entry point: hands off the freshest ADV [sighting] (name + `seenElapsedMs`) so
     * [prepareConnect] can connect in-window without a blind re-scan.
     */
    fun connect(
        deviceAddress: String,
        pairingCode: String,
        sighting: OnePlusScanResult?,
    ) {
        val name = sighting?.name
        pendingDeviceName = name
        pendingAdvSightingElapsedMs = sighting?.seenElapsedMs ?: 0L
        if (!name.isNullOrBlank()) {
            sensorStore?.saveLastDeviceName(name)
        }
        connect(deviceAddress, pairingCode)
    }

    override fun disconnect() {
        // Must not queue behind a blocking Control/EGV loop on bleExecutor.
        val previousSession = lifecycleLock.withLock {
            operationGeneration++
            resumeQueued = false
            session.also { session = null }
        }
        previousSession?.stop("disconnect")
        OnePlusMacArbiter.release(arbiterOwner)
    }

    override fun shutdown() {
        try {
            scanner.stopScan()
        } catch (_: Throwable) {
        }
        // One result from the lock, see connect(): `withLock` declares no callsInPlace contract.
        val (previousSession, previousExecutor) = lifecycleLock.withLock {
            operationGeneration++
            resumeQueued = false
            val previous = session
            session = null
            val oldExecutor = bleExecutor
            bleExecutor = onePlusBleExecutor()
            previous to oldExecutor
        }
        try {
            previousSession?.stop("shutdown")
        } catch (_: Throwable) {
        }
        watchersLock.withLock { watchers.clear() }
        previousExecutor.shutdownNow()
        OnePlusMacArbiter.release(arbiterOwner)
        OnePlusLog.i("${OnePlusLogMarkers.SESSION}: [$slot] shutdown")
    }

    override fun warmupState(): OnePlusWarmupState =
        session?.warmupState() ?: OnePlusWarmupState(phase = OnePlusWarmupState.Phase.IDLE)

    override fun isSessionUp(): Boolean = session?.isUp() == true

    private fun createSession(
        requestNewSensorStart: Boolean,
        generation: Long,
    ): OnePlusBleSession {
        val host = platform ?: error("ONEPLUS_SESSION: attach required")
        val gatt = host.gattClient(profile)
        val auth = OnePlusSessionAuthKeks(gatt)
        val created = OnePlusBleSessionSkeleton(
            gatt = gatt,
            auth = auth,
            profile = profile,
            onWarmup = { state ->
                ifCurrentOperation(generation) {
                    watchersSnapshot().forEach { it.onWarmup(state) }
                }
            },
            onSession = { up, reason ->
                ifCurrentOperation(generation) {
                    watchersSnapshot().forEach { it.onSession(up, reason) }
                }
            },
            onError = { message, fatal ->
                ifCurrentOperation(generation) {
                    watchersSnapshot().forEach { it.onError(message, fatal) }
                }
            },
            onGlucose = { sample ->
                ifCurrentOperation(generation) {
                    watchersSnapshot().forEach { it.onGlucose(sample) }
                }
            },
            // Auto-resume always passes false; only an explicit new-sensor connect may start.
            requestNewSensorStart = requestNewSensorStart,
            beforeConnect = { address, attempt, scanMs -> prepareConnect(address, attempt, scanMs) },
            // Read `sensorStore` on every call, never through a captured local: a promotion calls
            // [rebindStore] under a LIVE link, and that link then runs for the rest of the session.
            // A captured store would keep reading the cleared staging file (no PIN -> load() null ->
            // a full re-pair on every reconnect) and would write the new key back into it.
            savedSharedKeyProvider = { savedSharedKey() },
            onAuthSuccess = { address, key ->
                ifCurrentOperation(generation) {
                    onAuthSucceeded(address, key)
                }
            },
            onAuthInvalidate = {
                ifCurrentOperation(generation) {
                    onAuthInvalidated()
                    OnePlusLog.i(
                        "${OnePlusLogMarkers.SESSION}: [$slot] cleared persisted KEKS shared key",
                    )
                }
            },
            cpuLease = host.cpuLease(slot),
            slot = slot,
        )
        return created
    }

    private fun ifCurrentOperation(generation: Long, action: () -> Unit) {
        lifecycleLock.withLock {
            if (generation == operationGeneration) {
                action()
            }
        }
    }

    /**
     * Ob1 / Samsung recovery before the GATT `connect`, returning [OnePlusConnectPrep]
     * (whether a **fresh ADV** is in hand → session does a fast direct connect).
     *
     * 1. **Handoff fast-path:** if the UI (or a prior in-window rescan) saw this MAC within
     *    [ADV_HANDOFF_FRESH_MS], connect immediately — no blind re-scan. This is what removes the
     *    ~8 s wasted scan + the ~48 s autoConnect park seen in the field log.
     * 2. Otherwise LE-scan via [OnePlusBleScanner.awaitTarget] (private ScanCallback so UI
     *    [stopScan] cannot cancel it). A hit → fresh; a miss with
     *    [OemDeviceProfile.requireAdvBeforeConnect] on attempt 0 (and no UI selection) → defer.
     *
     * ⚠️ ASYNC IMPACT: blocks bleExecutor; binder delivers ADV into awaitTarget.
     */
    private fun prepareConnect(
        deviceAddress: String,
        attempt: Int,
        /** Overrides [app.aaps.plugins.dexcomoneplus.oem.OemDeviceProfile.preConnectScanMs]; null keeps the profile value. */
        scanMsOverride: Long? = null,
    ): OnePlusConnectPrep {
        val target = deviceAddress.uppercase()
        val sightingAgeMs =
            if (pendingAdvSightingElapsedMs > 0L) {
                onePlusElapsedMs() - pendingAdvSightingElapsedMs
            } else {
                Long.MAX_VALUE
            }
        if (sightingAgeMs in 0..ADV_HANDOFF_FRESH_MS) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SCAN}: [$slot] pre-connect handoff — fresh ADV ${sightingAgeMs}ms old, " +
                    "skip rescan (attempt=$attempt mac=***${target.takeLast(5)})",
            )
            return OnePlusConnectPrep(advFresh = true)
        }

        // A long window costs the same single scan registration as a short one but hears the ADV the
        // moment it arrives, so the persistent wait overrides the profile value here.
        val scanMs = scanMsOverride ?: profile.preConnectScanMs
        if (scanMs <= 0L) return OnePlusConnectPrep(advFresh = false)
        // UI Connect just selected this ADV → don't hard-fail if re-scan misses briefly.
        //
        // A name alone is not enough. The start screen may pre-select a sensor it has only ever read
        // from the store, and that entry carries the stored ADV name with no sighting behind it. Such
        // an entry used to claim "the UI just selected this", which switched off
        // [OemDeviceProfile.requireAdvBeforeConnect] — the one guard that stops a blind connect. The
        // timestamp is what separates a real sighting from a remembered name.
        val uiJustSelected = !pendingDeviceName.isNullOrBlank() && pendingAdvSightingElapsedMs > 0L
        val hardRequireAdv =
            profile.requireAdvBeforeConnect && attempt == 0 && !uiJustSelected
        OnePlusLog.i(
            "${OnePlusLogMarkers.SCAN}: [$slot] pre-connect awaitTarget ${scanMs}ms attempt=$attempt " +
                "requireAdv=${profile.requireAdvBeforeConnect} hardRequire=$hardRequireAdv " +
                "uiJustSelected=$uiJustSelected",
        )
        try {
            val wait = scanner.awaitTarget(target, scanMs)
            val hit = wait.target
            if (hit != null) {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.SCAN}: [$slot] pre-connect ADV name=${hit.name} " +
                        "rssi=${hit.rssi} mac=***${target.takeLast(5)}",
                )
                if (!hit.name.isNullOrBlank()) pendingDeviceName = hit.name
                return OnePlusConnectPrep(advFresh = true)
            }
            // Our MAC stayed silent. Any OTHER ONE+ heard in the same window is the signature of a
            // stale stored MAC (sensor replaced, or started into the other slot) — the session turns
            // this into a user-visible error once the silence is long enough to be conclusive.
            val foreign = wait.strongestForeign()?.let { other ->
                "name=${other.name ?: "?"} rssi=${other.rssi} mac=***${other.address.takeLast(5)}"
            }
            if (hardRequireAdv) {
                throw IllegalStateException(
                    "ONEPLUS_SCAN: ADV not seen in ${scanMs}ms — defer connect (keep screen on, sensor close)",
                )
            }
            OnePlusLog.w(
                "${OnePlusLogMarkers.SCAN}: [$slot] pre-connect ADV not seen in ${scanMs}ms " +
                    "(attempt=$attempt known MAC uiJustSelected=$uiJustSelected foreign=${foreign ?: "-"})",
            )
            return OnePlusConnectPrep(advFresh = false, foreignAdv = foreign)
        } catch (t: IllegalStateException) {
            throw t
        } catch (t: Throwable) {
            OnePlusLog.w("${OnePlusLogMarkers.SCAN}: [$slot] pre-connect ${t.message}")
            if (hardRequireAdv) {
                throw IllegalStateException(
                    "ONEPLUS_SCAN: pre-connect failed — ${t.message}",
                    t,
                )
            }
            return OnePlusConnectPrep(advFresh = false)
        }
    }

    private companion object {
        /**
         * Max age of a handed-off ADV sighting still treated as "fresh" (skip rescan, direct
         * connect). G7/ONE+ advertises in short windows, so a sighting older than this may already
         * be quiet and a direct connect would miss the window.
         */
        const val ADV_HANDOFF_FRESH_MS = 6_000L

        /** Process-wide counter so every instance's [arbiterOwner] is unique — see its doc comment. */
        val nextInstance = AtomicInt(0)
    }
}
