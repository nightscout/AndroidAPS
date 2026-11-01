package app.aaps.plugins.dexcomoneplus.scan

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.PowerManager
import android.os.SystemClock
import app.aaps.plugins.dexcomoneplus.OnePlusLog
import app.aaps.plugins.dexcomoneplus.OnePlusLogMarkers
import app.aaps.plugins.dexcomoneplus.gatt.OnePlusGattUuids
import app.aaps.plugins.dexcomoneplus.identity.OnePlusAdvCandidate
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Platform LE scanner for Dexcom ONE+ / G7-family ADV.
 *
 * Soft filters (Juggluco `isG7`):
 * - Local name `DXCM` / `DX02` / `DX01`, and/or
 * - Advertisement includes service UUID FEBC ([OnePlusGattUuids.Advertisement])
 *   **without** a G6-style `Dexcom*` marketing name.
 * - Optional [sessionHint] sticky-matches last MAC / ADV name / serial.
 *
 * The discovery scan remains unfiltered so nameless transmitters still appear.
 * The known-MAC pre-connect wait uses a platform [ScanFilter], allowing delivery while the screen
 * is off on Android/Samsung.
 *
 * The 4-digit pairing code plays no part on this path. It is the KEKS password and is checked at
 * Connect only. It is not carried in the advertisement, so the scan can narrow the list down to the
 * G7/ONE+ family and rank it, and no further: telling two ONE+ apart is the user's call — see
 * [autoSelectSingle].
 *
 * ⚠️ ASYNC IMPACT: [OnePlusScanListener.onDevice] on binder thread — hop to main for Compose.
 */
@SuppressLint("MissingPermission")
class OnePlusBleScannerAndroid(
    context: Context,
    /** Optional stored session for Juggluco-style candidate ranking / sticky match. */
    @Volatile var sessionHint: OnePlusStoredSession? = null,
    /** Sensor slot owning this scanner (`prod` / `staging`) — logged so field traces are attributable. */
    private val slot: String = OnePlusLogMarkers.SLOT_PRODUCTION,
) : OnePlusBleScanner {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    /** Screen state — the unfiltered throttle probe is only meaningful while the screen is on. */
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val scanning = AtomicBoolean(false)
    private val seen = ConcurrentHashMap<String, OnePlusScanResult>()

    /** Consecutive filtered `awaitTarget` windows that heard nothing — see unfiltered probe. */
    @Volatile
    private var consecutiveFilteredSilent = 0

    /** Consecutive **unfiltered** silent probes — see [noteScanOutcome]. */
    @Volatile
    private var silentScans = 0

    @Volatile
    private var listener: OnePlusScanListener? = null

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handle(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { handle(it) }
        }

        override fun onScanFailed(errorCode: Int) {
            OnePlusLog.e("${OnePlusLogMarkers.SCAN}: [$slot] failed errorCode=$errorCode")
            scanning.set(false)
        }
    }

    override fun startScan(listener: OnePlusScanListener) {
        val adapter = bluetoothManager.adapter
        val leScanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || leScanner == null) {
            OnePlusLog.e("${OnePlusLogMarkers.SCAN}: [$slot] adapter unavailable")
            return
        }
        stopScan()
        seen.clear()
        this.listener = listener
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        // UI discovery runs on the main thread: book the platform quota slot but never wait for one
        // (the reserve kept by OnePlusScanBudget is precisely what makes this always affordable).
        OnePlusScanBudget.record(SystemClock.elapsedRealtime())
        leScanner.startScan(null, settings, callback)
        scanning.set(true)
        OnePlusLog.i("${OnePlusLogMarkers.SCAN}: [$slot] started")
    }

    override fun stopScan() {
        val leScanner = bluetoothManager.adapter?.bluetoothLeScanner
        try {
            leScanner?.stopScan(callback)
        } catch (_: Throwable) {
        }
        scanning.set(false)
        listener = null
        OnePlusLog.i("${OnePlusLogMarkers.SCAN}: [$slot] stopped")
    }

    override fun isScanning(): Boolean = scanning.get()

    override fun updateSessionHint(session: OnePlusStoredSession?) {
        sessionHint = session
    }

    /**
     * Pre-connect ADV wait isolated from UI [stopScan] (own [ScanCallback]).
     * Field log: StartActivity onDispose stopped shared scan ~0.6s into prepareConnect.
     *
     * The platform filter list is OR'ed: the target MAC **plus** the ONE+/G7 advertisement service
     * (FEBC). The extra filter costs no additional scan start, and lets a wait that never hears its
     * own MAC still report that another ONE+ is nearby — the stale-stored-MAC signature.
     *
     * ⚠️ ASYNC IMPACT: blocks the calling BLE executor for up to [timeoutMs] plus any wait imposed
     * by [OnePlusScanBudget]; [OnePlusBleSession.stop] does not need this to return (it clears
     * `running` and disconnects GATT).
     */
    override fun awaitTarget(address: String, timeoutMs: Long): OnePlusAdvWaitResult {
        val adapter = bluetoothManager.adapter
        val leScanner = adapter?.bluetoothLeScanner
        if (adapter == null || !adapter.isEnabled || leScanner == null) {
            OnePlusLog.e("${OnePlusLogMarkers.SCAN}: [$slot] awaitTarget adapter unavailable")
            return OnePlusAdvWaitResult()
        }
        val target = normalizeTargetAddress(address)
        val found = AtomicReference<OnePlusScanResult?>(null)
        val foreign = ConcurrentHashMap<String, OnePlusScanResult>()
        val latch = CountDownLatch(1)
        // Any callback at all proves the scan really registered with the OS — see silentScans.
        val heardAnything = AtomicBoolean(false)
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                heardAnything.set(true)
                val device = result.device ?: return
                val addr = device.address ?: return
                val name = device.name ?: result.scanRecord?.deviceName
                if (addr.uppercase() != target) {
                    // FEBC-matched neighbour: keep it only if it looks like a G7-family transmitter
                    // (never a G6 `Dexcom*` companion), so the diagnostic cannot cry wolf.
                    if (isG7FamilyAdvertisement(name)) {
                        foreign[addr.uppercase()] = OnePlusScanResult(addr, name, result.rssi)
                            .apply { seenElapsedMs = SystemClock.elapsedRealtime() }
                    }
                    return
                }
                val hit = OnePlusScanResult(addr, name, result.rssi)
                    .apply { seenElapsedMs = SystemClock.elapsedRealtime() }
                if (found.compareAndSet(null, hit)) {
                    latch.countDown()
                }
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { onScanResult(0, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                if (shouldBackOffOnScanFailed(errorCode)) {
                    OnePlusScanBudget.blockFor(SystemClock.elapsedRealtime(), OnePlusScanBudget.WINDOW_MS)
                    OnePlusLog.w(
                        "${OnePlusLogMarkers.SCAN}: [$slot] awaitTarget failed errorCode=$errorCode — " +
                            "OS refused the scan; backing off one budget window",
                    )
                } else {
                    OnePlusLog.e(
                        "${OnePlusLogMarkers.SCAN}: [$slot] awaitTarget failed errorCode=$errorCode",
                    )
                }
                heardAnything.set(true)
                latch.countDown()
            }
        }
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        awaitScanBudgetSlot()
        return try {
            val targetFilter = ScanFilter.Builder()
                .setDeviceAddress(target)
                .build()
            val familyFilter = ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(OnePlusGattUuids.Advertisement))
                .build()
            leScanner.startScan(listOf(targetFilter, familyFilter), settings, cb)
            OnePlusLog.i(
                "${OnePlusLogMarkers.SCAN}: [$slot] awaitTarget filtered started " +
                    "mac=***${target.takeLast(5)} timeoutMs=$timeoutMs",
            )
            latch.await(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            OnePlusAdvWaitResult(target = found.get(), foreign = foreign.values.toList())
        } catch (t: Throwable) {
            OnePlusLog.w("${OnePlusLogMarkers.SCAN}: [$slot] awaitTarget ${t.message}")
            // An exception is not evidence about the OS, so do not let it feed the throttle
            // heuristic: claim we heard something and leave the counters alone.
            heardAnything.set(true)
            OnePlusAdvWaitResult(foreign = foreign.values.toList())
        } finally {
            try {
                leScanner.stopScan(cb)
            } catch (_: Throwable) {
            }
            OnePlusLog.i("${OnePlusLogMarkers.SCAN}: [$slot] awaitTarget stopped")
        }.also { result ->
            // Deliberately after the `finally`: the probe below starts its own scanner, and running
            // it while this one is still registered doubled the registration pressure at exactly the
            // moment the platform quota was tightest (CUBOT field log 2026-08-20, "unfiltered
            // throttle probe started" logged before the matching "awaitTarget stopped").
            noteFilteredWindowOutcome(
                heardAnything = heardAnything.get(),
                foundTarget = result.target != null,
            )
        }
    }

    /**
     * A filtered pre-connect window cannot tell "OS refused the scan" from "my sensor is asleep".
     * The MAC+FEBC filter never delivers a neighbour phone or watch, so silence is the normal
     * one-sensor house (CUBOT field log 2026-08-16). Back-off is fed only by:
     * - a direct [ScanCallback.onScanFailed] refusal, or
     * - a short **unfiltered** probe every [UNFILTERED_THROTTLE_PROBE_EVERY] silent filtered windows.
     * An unfiltered 1 s window in a lived-in room almost always hears something; if it hears
     * nothing, throttling is a fair guess.
     */
    private fun noteFilteredWindowOutcome(heardAnything: Boolean, foundTarget: Boolean) {
        if (foundTarget || heardAnything) {
            consecutiveFilteredSilent = 0
            silentScans = 0
            return
        }
        consecutiveFilteredSilent = nextFilteredSilentWindows(consecutiveFilteredSilent)
        if (!shouldRunUnfilteredThrottleProbe(consecutiveFilteredSilent)) return
        consecutiveFilteredSilent = 0
        // AOSP ScanManager suspends UNFILTERED scans while the screen is off ("Cannot start
        // unfiltered scan in screen-off. This scan will be resumed later"), so with the screen off
        // the probe can only ever report silence and the back-off would fire on a premise the OS
        // guarantees false. CUBOT field log 2026-08-20: 16 probes, 16 refusals, 0 valid samples —
        // the probe was causing the very throttling it was meant to detect.
        if (!powerManager.isInteractive) {
            OnePlusLog.i(
                "${OnePlusLogMarkers.SCAN}: [$slot] throttle probe skipped — screen off " +
                    "(the OS suspends unfiltered scans, so silence would prove nothing)",
            )
            // Do not carry half a count into the next screen-on period.
            silentScans = 0
            return
        }
        val heardOpen = probeUnfiltered(UNFILTERED_THROTTLE_PROBE_MS)
        noteScanOutcome(heardOpen, UNFILTERED_THROTTLE_PROBE_MS)
    }

    /**
     * Open 1 s scan used only as a throttle detector. Counts against [OnePlusScanBudget] like any
     * other start. ⚠️ ASYNC IMPACT: blocks the BLE executor for up to [timeoutMs] plus budget wait.
     */
    private fun probeUnfiltered(timeoutMs: Long): Boolean {
        val leScanner = bluetoothManager.adapter?.bluetoothLeScanner ?: return false
        val heard = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                heard.set(true)
                latch.countDown()
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                if (results.isNotEmpty()) {
                    heard.set(true)
                    latch.countDown()
                }
            }

            override fun onScanFailed(errorCode: Int) {
                if (shouldBackOffOnScanFailed(errorCode)) {
                    OnePlusScanBudget.blockFor(SystemClock.elapsedRealtime(), OnePlusScanBudget.WINDOW_MS)
                    OnePlusLog.w(
                        "${OnePlusLogMarkers.SCAN}: [$slot] unfiltered probe failed errorCode=$errorCode — " +
                            "backing off one budget window",
                    )
                }
                heard.set(true)
                latch.countDown()
            }
        }
        awaitScanBudgetSlot()
        return try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            leScanner.startScan(null, settings, cb)
            OnePlusLog.i(
                "${OnePlusLogMarkers.SCAN}: [$slot] unfiltered throttle probe started timeoutMs=$timeoutMs",
            )
            latch.await(timeoutMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            heard.get()
        } catch (t: Throwable) {
            OnePlusLog.w("${OnePlusLogMarkers.SCAN}: [$slot] unfiltered probe ${t.message}")
            false
        } finally {
            try {
                leScanner.stopScan(cb)
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Direct OS refusal. Some builds still omit this callback and fail silent — that path is the
     * unfiltered probe, not filtered-window silence.
     */
    private fun noteScanOutcome(heardAnything: Boolean, timeoutMs: Long) {
        val next = nextSilentScanState(silentScans, heardAnything)
        silentScans = next.silentScans
        if (!next.backOff) return
        OnePlusLog.w(
            "${OnePlusLogMarkers.SCAN}: [$slot] $SILENT_SCANS_BEFORE_BACKOFF silent unfiltered probes " +
                "(${timeoutMs}ms each, no advertisement at all) — the OS may be refusing our scans " +
                "(\"scanning too frequently\"); backing off one budget window",
        )
        OnePlusScanBudget.blockFor(SystemClock.elapsedRealtime(), OnePlusScanBudget.WINDOW_MS)
    }

    /**
     * Hold this (background) thread until the shared [OnePlusScanBudget] grants a start slot, so
     * two slots waiting for their sensors cannot together trip the platform's silent scan throttle.
     * Returns with the slot already booked.
     */
    private fun awaitScanBudgetSlot() {
        var logged = false
        while (true) {
            val wait = OnePlusScanBudget.reserve(SystemClock.elapsedRealtime())
            if (wait <= 0L) return
            if (!logged) {
                OnePlusLog.i(
                    "${OnePlusLogMarkers.SCAN}: [$slot] scan budget full — deferring startScan ${wait}ms",
                )
                logged = true
            }
            try {
                Thread.sleep(wait.coerceAtMost(OnePlusScanBudget.MAX_SLEEP_SLICE_MS))
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return
            }
        }
    }

    private fun handle(result: ScanResult) {
        val device = result.device ?: return
        val address = device.address ?: return
        val name = device.name ?: result.scanRecord?.deviceName
        val hasAdvUuid = result.scanRecord?.serviceUuids?.any {
            it.uuid == OnePlusGattUuids.Advertisement
        } == true
        val hint = sessionHint
        val candidate = OnePlusAdvCandidate.isCandidate(name, address, hint)
        val g6MarketingName = name?.startsWith("Dexcom", ignoreCase = true) == true
        // FEBC alone is OK for nameless G7 ADVs; reject FEBC+Dexcom* (G6 companions).
        val febcOk = hasAdvUuid && !g6MarketingName
        if (!candidate && !febcOk && !nameMatches(name)) return
        // When we have a sticky session, drop weak unrelated FEBC noise unless name/MAC matches.
        // "Sticky" means the hint really knows a transmitter. A hint that only carries the code on
        // screen knows none, and must not hide the nameless FEBC advertisement of a new sensor.
        val sticky = !hint?.lastMac.isNullOrBlank() || !hint?.lastDeviceName.isNullOrBlank()
        if (sticky && !candidate && febcOk && !nameMatches(name)) return

        val hit = OnePlusScanResult(address = address, name = name, rssi = result.rssi)
            .apply { seenElapsedMs = SystemClock.elapsedRealtime() }
        val previous = seen.put(address, hit)
        if (previous == null || previous.rssi != hit.rssi || previous.name != hit.name) {
            val score = OnePlusAdvCandidate.rankScore(name, address, hit.rssi, hint)
            OnePlusLog.d(
                "${OnePlusLogMarkers.SCAN}: [$slot] device name=${name ?: "?"} rssi=${hit.rssi} " +
                    "score=$score addr=***${address.takeLast(5)}",
            )
            listener?.onDevice(hit)
        }
    }

    companion object {

        /**
         * Consecutive filtered windows with no callback at all before we run a 1 s unfiltered
         * probe. Five, so a quiet sensor does not pay for a probe on every pair of 3 s waits.
         */
        const val UNFILTERED_THROTTLE_PROBE_EVERY = 5

        /** Length of the unfiltered throttle probe. Short: it only needs to hear *anything*. */
        const val UNFILTERED_THROTTLE_PROBE_MS = 1_000L

        /**
         * Consecutive scan windows with no advertisement at all before we assume the OS is refusing
         * our scans. Two, so a genuinely quiet moment (every sensor asleep, no neighbour) does not
         * trigger a needless cool-down. Fed only by the unfiltered probe, never by a filtered wait.
         */
        const val SILENT_SCANS_BEFORE_BACKOFF = 2

        /** Result of one scan window: silent windows counted so far, and whether to cool down now. */
        internal data class SilentScanState(val silentScans: Int, val backOff: Boolean)

        /**
         * Pure counter step of [noteScanOutcome], so the back-off rule can be tested without the
         * Android BLE stack. The counter restarts after a back-off, so a permanently blind scanner
         * cools down once per [SILENT_SCANS_BEFORE_BACKOFF] windows and not on every window.
         */
        internal fun nextSilentScanState(previousSilentScans: Int, heardAnything: Boolean): SilentScanState =
            when {
                heardAnything                                        -> SilentScanState(silentScans = 0, backOff = false)
                previousSilentScans + 1 >= SILENT_SCANS_BEFORE_BACKOFF -> SilentScanState(silentScans = 0, backOff = true)
                else                                                 -> SilentScanState(previousSilentScans + 1, backOff = false)
            }

        internal fun nextFilteredSilentWindows(previous: Int): Int = previous + 1

        internal fun shouldRunUnfilteredThrottleProbe(consecutiveFilteredSilentWindows: Int): Boolean =
            consecutiveFilteredSilentWindows > 0 &&
                consecutiveFilteredSilentWindows % UNFILTERED_THROTTLE_PROBE_EVERY == 0

        /**
         * Direct `onScanFailed` codes that mean the OS refused or starved the scanner.
         * `ALREADY_STARTED` is not a throttle.
         */
        internal fun shouldBackOffOnScanFailed(errorCode: Int): Boolean = when (errorCode) {
            ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED,
            ScanCallback.SCAN_FAILED_INTERNAL_ERROR,
            ScanCallback.SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES,
            ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY -> true
            else -> false
        }

        fun nameMatches(name: String?): Boolean = OnePlusAdvCandidate.nameMatchesSoft(name)

        /** Unchanged behaviour, kept here so the Android call sites and their tests still read the same. */
        fun autoSelectSingle(devices: List<OnePlusScanResult>): OnePlusScanResult? =
            OnePlusScanSelection.autoSelectSingle(devices)

        /**
         * Unchanged behaviour - see [OnePlusScanSelection.autoSelect] for the rule itself.
         *
         * The rule is in shared code. This stays as the name Android already called.
         */
        fun autoSelect(devices: List<OnePlusScanResult>, hint: OnePlusStoredSession?): OnePlusScanResult? =
            OnePlusScanSelection.autoSelect(devices, hint)

        internal fun normalizeTargetAddress(address: String): String = address.uppercase()

        /**
         * A FEBC advertiser that is a G7-family transmitter: either a nameless ADV (G7/ONE+ often
         * advertise without a local name) or a `DXCM`/`DX0x` name — never a G6 `Dexcom*` companion.
         */
        internal fun isG7FamilyAdvertisement(name: String?): Boolean = when {
            name.isNullOrBlank()                            -> true
            name.startsWith("Dexcom", ignoreCase = true)    -> false
            else                                            -> nameMatches(name)
        }
    }
}
