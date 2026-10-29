package app.aaps.plugins.libre3

import app.aaps.core.interfaces.source.SensorSlot
import app.aaps.plugins.libre3.crypto.Libre3FirstPairEphemeral
import app.aaps.plugins.libre3.gatt.Libre3BluetoothUuids
import app.aaps.plugins.libre3.gatt.Libre3GattClient
import app.aaps.plugins.libre3.identity.Libre3SensorIdentity
import app.aaps.plugins.libre3.identity.Libre3SensorStore
import app.aaps.plugins.libre3.parse.Libre3DataFrame
import app.aaps.plugins.libre3.parse.Libre3GlucoseParser
import app.aaps.plugins.libre3.parse.Libre3ParseException
import app.aaps.plugins.libre3.parse.Libre3PatchStatusParser
import app.aaps.plugins.libre3.parse.toSampleOrNull
import app.aaps.plugins.libre3.reconnect.Libre3ReconnectPolicy
import app.aaps.plugins.libre3.reconnect.Libre3RecoveryAction
import app.aaps.plugins.libre3.reconnect.Libre3ScanBudget
import app.aaps.plugins.libre3.session.Libre3BleSession
import app.aaps.plugins.libre3.session.Libre3DisconnectPolicy
import app.aaps.plugins.libre3.session.Libre3MacArbiter
import app.aaps.plugins.libre3.session.Libre3PairingBlockFactory
import app.aaps.plugins.libre3.session.Libre3StartRefusal
import app.aaps.plugins.libre3.warmup.Libre3WarmupClock
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The real driver: one Bluetooth session, one thread, one sensor.
 *
 * This is the driver the plugin uses whenever it is enabled and the pairing tables are present.
 *
 * ⚠️ ASYNC IMPACT: everything that talks to the sensor runs on [bleExecutor], one thing at a time.
 * The watchers are called from that thread, so they must not block it.
 *
 * @param storeNamespace which preferences file the sessions of this instance read and write. null is
 *   the production file, a name is a pre-soak file. See [Libre3SensorStore].
 */
class Libre3CgmDriverReal(
    private val pairingBlocks: Libre3PairingBlockFactory,
    storeNamespace: String? = null,
) : Libre3CgmDriver {

    /**
     * Which preferences file this instance's sessions read and write.
     *
     * A `var` and not a constructor `val`, because a promotion hands a **live link** from the
     * pre-soak slot over to production: the link must not be dropped, but from that moment on every
     * new session of this instance has to open against the production file. See [rebindStore].
     */
    @Volatile
    private var storeNamespace: String? = storeNamespace

    /**
     * Which slot this instance was built for.
     *
     * It stays what it was, even after a promotion, because the thread it named was already
     * started and a bug report has to stay readable across the swap.
     */
    private val slot: SensorSlot =
        if (storeNamespace.isNullOrBlank()) SensorSlot.PRODUCTION else SensorSlot.STAGING

    /** Short name of this instance for the log: "prod" or "presoak". */
    private val slotName: String = if (slot == SensorSlot.PRODUCTION) SLOT_PRODUCTION else SLOT_STAGING

    /**
     * Which slot this instance really plays for **right now**.
     *
     * This is the role, not the name. [slot] and [slotName] are the identity of the instance and
     * never change, so a bug report stays readable across a promotion. The role does change: a
     * promoted instance feeds the loop from that moment on, so it must get the production reconnect
     * pace, not the slow pre-soak one. See [rebindStore].
     */
    @Volatile
    private var role: SensorSlot = slot

    /** Which slot this instance plays for right now. It follows [rebindStore], unlike the log name. */
    val currentRole: SensorSlot get() = role

    /**
     * What this instance is called in [Libre3MacArbiter], for as long as it lives.
     *
     * It is **not** [slotName]. That name stays "presoak" even after a promotion, so two different
     * instances could carry it at once and the second one would take the first one's sensor away.
     * A counter cannot be mistaken like that.
     */
    private val arbiterOwner: String = "$slotName#${nextInstance.incrementAndGet()}"

    private val watchers = CopyOnWriteArrayList<Libre3GlucoseWatcher>()

    private val bleExecutor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "libre3-ble-$slotName").apply { isDaemon = true }
    }

    @Volatile
    private var platform: Libre3Platform? = null

    @Volatile
    private var store: Libre3SensorStore? = null

    @Volatile
    private var session: Libre3BleSession? = null

    @Volatile
    private var gatt: Libre3GattClient? = null

    @Volatile
    private var warmup = Libre3WarmupState(phase = Libre3WarmupState.Phase.IDLE)

    @Volatile
    private var sessionUp = false

    @Volatile
    private var failedAttempts = 0

    /**
     * Raised on every new [connect] and every [stopSession], so a retry that was already queued
     * cannot start after the user asked to stop, or after a newer connect has begun.
     */
    @Volatile
    private var connectGeneration = 0

    override fun attach(platform: Libre3Platform) {
        this.platform = platform
        this.store = platform.sensorStore(storeNamespace)
    }

    /**
     * Points this instance at another slot's preferences file, without dropping the running link.
     *
     * Used once, by a promotion. The running read loop kept its store in a local, so it goes on
     * writing into the old file until the link ends, which is harmless. The next session opens
     * against the new file.
     *
     * The [role] moves with the file. Without that the promoted instance would keep the slow
     * pre-soak reconnect pace for the rest of that sensor's life, so the sensor that feeds the loop
     * would come back more slowly than it did before the pre-soak feature existed.
     */
    fun rebindStore(namespace: String?) {
        storeNamespace = namespace
        role = if (namespace.isNullOrBlank()) SensorSlot.PRODUCTION else SensorSlot.STAGING
        platform?.let { store = it.sensorStore(namespace) }
    }

    /** True when this instance is the one the arbiter says owns [mac]. */
    fun ownsSensor(mac: String): Boolean = Libre3MacArbiter.ownerOf(mac) == arbiterOwner

    /** True when a sensor with a PIN is stored for this instance's slot. */
    fun hasStoredSensor(): Boolean = store?.isReadyForBle() == true

    /** True once [shutdown] has run. Such an instance can never open a session again. */
    fun isShutDown(): Boolean = bleExecutor.isShutdown

    override fun addWatcher(watcher: Libre3GlucoseWatcher) {
        if (!watchers.contains(watcher)) watchers.add(watcher)
    }

    override fun removeWatcher(watcher: Libre3GlucoseWatcher) {
        watchers.remove(watcher)
    }

    override fun connect(deviceAddress: String) {
        failedAttempts = 0
        val generation = synchronized(this) { ++connectGeneration }
        // Drop a connect that is already waiting, so a new NFC scan is not stuck behind it.
        gatt?.disconnect()
        bleExecutor.execute { openSession(generation) }
    }

    override fun disconnect() {
        stopSession(Libre3DisconnectPolicy.Reason.USER_DISCONNECTED)
    }

    override fun shutdown() {
        stopSession(Libre3DisconnectPolicy.Reason.USER_STOPPED_PLUGIN)
        // Said again, because this instance can never open a session after this point and its claim
        // must not outlive it. `release` on an owner that holds nothing does nothing.
        Libre3MacArbiter.release(arbiterOwner)
        watchers.clear()
        bleExecutor.shutdownNow()
    }

    /**
     * Ends a running session from **any** thread.
     *
     * The read loop below holds the driver's only executor for the whole life of a session, so a
     * stop that was queued on that same executor could never run while a sensor was connected: the
     * plugin could be switched off and the link would stay up for days. So the two steps that
     * actually stop it are done here, on the caller's thread:
     *
     * 1. the loop is told to end, and
     * 2. the link is dropped, which wakes the loop out of its wait at once.
     *
     * Dropping the link is a link level action only. No command is sent to the sensor, whatever
     * the reason, see [Libre3DisconnectPolicy].
     */
    private fun stopSession(reason: Libre3DisconnectPolicy.Reason) {
        check(!Libre3DisconnectPolicy.mayWriteSensorCommand(reason)) {
            "no reason may ever write a command to the sensor"
        }
        synchronized(this) { connectGeneration++ }
        sessionUp = false
        val current = session
        session = null
        gatt = null
        current?.close(reason)
        // The sensor is free again. A stop that kept the claim would block the other slot from ever
        // taking this sensor on, and a promotion from ever starting a second pre-soak.
        Libre3MacArbiter.release(arbiterOwner)
        publishWarmup(Libre3WarmupState.Phase.IDLE)
        watchers.forEach { it.onSession(false, reason.name) }
    }

    /**
     * Ends a session whose link died, and gets the driver ready to try again.
     *
     * It does everything [stopSession] does **except raise the generation**, and that is the whole
     * point: the generation is what tells a queued retry that it is stale, so raising it here would
     * cancel the very retry this failure has to start. A newer [connect] or a [stopSession] still
     * raises it, and still wins over anything queued here.
     */
    private fun endSessionAfterLinkLoss(generation: Int) {
        sessionUp = false
        val current = session
        session = null
        gatt = null
        current?.close(Libre3DisconnectPolicy.Reason.LINK_LOST)
        watchers.forEach { it.onSession(false, Libre3DisconnectPolicy.Reason.LINK_LOST.name) }
        Libre3Log.i("[$slotName] ${Libre3LogMarkers.SESSION}: reading stopped, the link is gone")

        if (generation != connectGeneration) return
        failedAttempts++
        val message = "the link to the sensor was lost"
        publishWarmup(Libre3WarmupState.Phase.RECONNECTING, message = message)
        Libre3Log.w("[$slotName] ${Libre3LogMarkers.RECONNECT}: link lost, attempt $failedAttempts")
        watchers.forEach { it.onError(message, false) }
        scheduleRetry(generation)
    }

    override fun warmupState(): Libre3WarmupState = warmup

    override fun isSessionUp(): Boolean = sessionUp

    private fun openSession(generation: Int) {
        if (generation != connectGeneration) return
        val host = platform ?: return
        val sensorStore = store ?: return
        // One sensor, one driver instance. Taken before the scan, because from the scan on there is
        // a link, and two links on one sensor break both pairings. A refusal is final on purpose: a
        // retry could only keep asking for a sensor that belongs to somebody else.
        val storedAddress = sensorStore.loadIdentity()?.bleAddress
        if (storedAddress != null && !Libre3MacArbiter.claim(storedAddress, arbiterOwner)) {
            // The slot that feeds the loop must never be the one that is starved. Two files can end
            // up holding one sensor after a promotion that was cut off half way, and then whichever
            // thread gets here first would keep it for good, because a refusal is final. So the
            // production role takes the sensor back: the pre-soak link is dropped, which is the only
            // way to stay at one link per sensor, and then the claim is asked for again.
            val takenBack = role == SensorSlot.PRODUCTION &&
                Libre3CgmDrivers.yieldStagingSensorToProduction(storedAddress, this) &&
                Libre3MacArbiter.claim(storedAddress, arbiterOwner)
            if (!takenBack) {
                val message = "this sensor is already used by the other slot"
                publishWarmup(Libre3WarmupState.Phase.FAILED, message = message)
                watchers.forEach { it.onError(message, true) }
                return
            }
            Libre3Log.w(
                "[$slotName] ${Libre3LogMarkers.SESSION}: the pre-soak slot held the sensor of the loop, " +
                    "it was stopped and the sensor was taken back",
            )
        }
        // Opening a session starts a scan, and a scan over the platform quota reports success and
        // then finds nothing. Waiting is the only way to be heard again.
        val now = System.currentTimeMillis()
        if (!Libre3ScanBudget.tryAcquire(now)) {
            val waitMs = Libre3ScanBudget.waitMsUntilNextStart(now)
            Libre3Log.i("[$slotName] ${Libre3LogMarkers.RECONNECT}: scan budget is full, waiting ${waitMs}ms")
            scheduleRetryAfter(generation, waitMs)
            return
        }
        val client = host.gattClient()
        gatt = client
        val newSession = Libre3BleSession(client, sensorStore, pairingBlocks)
        session = newSession

        publishWarmup(Libre3WarmupState.Phase.CONNECTING)
        when (val result = newSession.open(firstPairAvailable = Libre3FirstPairEphemeral.isAvailable)) {
            is Libre3BleSession.Result.Up      -> {
                failedAttempts = 0
                sessionUp = true
                watchers.forEach { it.onSession(true, null) }
                startReading(newSession, client, sensorStore, generation)
            }

            is Libre3BleSession.Result.Refused -> {
                sessionUp = false
                publishWarmup(Libre3WarmupState.Phase.FAILED, message = refusalMessage(result.refusal))
                watchers.forEach { it.onError(refusalMessage(result.refusal), true) }
            }

            is Libre3BleSession.Result.Failed  -> {
                sessionUp = false
                failedAttempts++
                val action = Libre3ReconnectPolicy.actionAfterFailure(failedAttempts, result.handshakeReached)
                val fatal = action == Libre3RecoveryAction.ASK_FOR_NFC_SCAN
                publishWarmup(
                    if (fatal) Libre3WarmupState.Phase.FAILED else Libre3WarmupState.Phase.RECONNECTING,
                    message = result.reason,
                )
                Libre3Log.w(
                    "[$slotName] ${Libre3LogMarkers.RECONNECT}: attempt $failedAttempts failed, " +
                        "handshakeReached=${result.handshakeReached}, next action $action",
                )
                watchers.forEach { it.onError(result.reason, fatal) }
                if (action == Libre3RecoveryAction.RETRY_CACHED_RECONNECT) {
                    scheduleRetry(generation)
                }
            }
        }
    }

    /**
     * Waits then opens the same stored sensor again, unless a newer [connect] or a stop has
     * cancelled this generation.
     *
     * ⚠️ ASYNC IMPACT: the wait runs on [bleExecutor]. A stop interrupts it through
     * [ExecutorService.shutdownNow].
     */
    private fun scheduleRetry(generation: Int) {
        val delayMs = Libre3ReconnectPolicy.nextDelayMs(failedAttempts, role)
        scheduleRetryAfter(generation, delayMs)
    }

    /**
     * The same retry, with a wait the caller has worked out itself.
     *
     * The scan budget needs this: its wait is the rest of the platform window, and the ladder of
     * [Libre3ReconnectPolicy] knows nothing about that window.
     *
     * ⚠️ ASYNC IMPACT: same as [scheduleRetry], the wait runs on [bleExecutor].
     */
    private fun scheduleRetryAfter(generation: Int, delayMs: Long) {
        Libre3Log.i("[$slotName] ${Libre3LogMarkers.RECONNECT}: retry in ${delayMs}ms")
        bleExecutor.execute {
            try {
                Thread.sleep(delayMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return@execute
            }
            if (generation != connectGeneration) return@execute
            if (sessionUp) return@execute
            openSession(generation)
        }
    }

    /**
     * Reads whatever the sensor sends, for as long as the link holds.
     *
     * Every glucose message is put together from its two pieces, read, checked against the sensor
     * life, and only then handed on. A message that fails any check is dropped here and never
     * reaches the plugin.
     */
    private fun startReading(
        openSession: Libre3BleSession,
        client: Libre3GattClient,
        sensorStore: Libre3SensorStore,
        generation: Int,
    ) {
        val crypto = openSession.dataPlaneCrypto() ?: return
        val identity = sensorStore.loadIdentity() ?: return
        while (sessionUp) {
            // One stream for all seven channels. Glucose and sensor health arrive mixed, and the
            // health channel is the only thing that speaks during the hour of warm-up, so waiting
            // on glucose alone would look like a dead session.
            val event = client.awaitDataPlaneNotify(DATA_WAIT_MS)
            // A stop from another thread drops the link, which is what wakes this wait.
            if (!sessionUp) break
            if (event == null) {
                if (!client.isConnected()) break
                continue
            }
            val (channel, piece) = event
            if (piece.isEmpty()) break
            val isGlucose = channel == Libre3BluetoothUuids.GLUCOSE_DATA
            val isPatchStatus = channel == Libre3BluetoothUuids.PATCH_STATUS
            // Only two channels are read in this version. The others are armed because the sensor
            // will not send without them, but nothing is made of what they say.
            if (!isGlucose && !isPatchStatus) continue
            // Only the glucose channel arrives in two pieces. Everything else is whole already.
            val whole = openSession.glucoseFrames.feed(piece, isGlucoseChannel = isGlucose) ?: continue
            try {
                // The packet number comes from the message itself. Counting on this side would
                // drift apart from the sensor after the first missed packet, and every message
                // after that would fail its own check.
                val frame = Libre3DataFrame.parse(whole)
                val plaintext = crypto.decryptTryingAllKinds(frame.encrypted, frame.sequenceNumber).plaintext
                handlePlaintext(plaintext, isGlucose, identity, sensorStore)
            } catch (e: Libre3ParseException) {
                Libre3Log.w("[$slotName] ${Libre3LogMarkers.BG}: unreadable message dropped, ${e.message}")
            } catch (e: Exception) {
                Libre3Log.w("[$slotName] ${Libre3LogMarkers.BG}: message dropped, ${e.javaClass.simpleName}")
            }
        }
        // The loop ends for one of two reasons, and they must not be treated alike.
        //
        // - A stop was asked for. Then [sessionUp] is already false, the generation was already
        //   raised, and the driver must stay quiet.
        // - The link died on its own. Then nobody asked for anything, the sensor is still on the
        //   arm and its key is still good, so the driver has to knock again. Before this existed
        //   the session simply ended here and only a new NFC scan brought the sensor back.
        if (sessionUp) {
            endSessionAfterLinkLoss(generation)
        } else {
            Libre3Log.i("[$slotName] ${Libre3LogMarkers.SESSION}: reading stopped")
        }
    }

    /**
     * @param fromGlucoseChannel which channel the message came from. The kind of record is decided
     *   by that and never by how long the plain text happens to be: another channel could one day
     *   send a record of the same length, and it would then be published as a glucose value.
     */
    private fun handlePlaintext(
        plaintext: ByteArray,
        fromGlucoseChannel: Boolean,
        identity: Libre3SensorIdentity,
        sensorStore: Libre3SensorStore,
    ) {
        if (!fromGlucoseChannel) {
            if (plaintext.size != Libre3PatchStatusParser.PLAINTEXT_SIZE) return
            val status = Libre3PatchStatusParser.parse(plaintext)
            if (status.hasSensorError()) {
                watchers.forEach { it.onError("the sensor reports a problem: ${status.sensorError}", false) }
            }
            return
        }
        if (plaintext.size != Libre3GlucoseParser.PLAINTEXT_SIZE) return

        val reading = Libre3GlucoseParser.parse(plaintext)
        // A sensor that is still sending good readings is alive, whatever the stored end says. One
        // bad NFC reading of the wear time must not be able to end the stream in silence.
        val wearMinutes = if (reading.dataQualityGood && reading.glucoseMgdl != null) {
            sensorStore.extendWearIfStillAlive(reading.lifeCount)
        } else {
            identity.wearDurationMinutes
        }
        val clock = Libre3WarmupClock(
            lifeCountMinutes = reading.lifeCount,
            warmupMinutes = identity.warmupMinutes,
            wearMinutes = wearMinutes.takeIf { it > 0 },
        )
        if (clock.isWarmingUp) {
            publishWarmup(Libre3WarmupState.Phase.WARMING, remainingMs = clock.remainingWarmupMs)
            return
        }

        // The one and only way a reading becomes something the loop may dose on.
        val sample = reading.toSampleOrNull(
            activatedAtMs = identity.activatedAtMs,
            wearMinutes = wearMinutes.takeIf { it > 0 },
            warmupMinutes = identity.warmupMinutes,
        ) ?: return
        // Ready is said only once a reading has really passed every check, so the screen never
        // shows a working sensor while its readings are being refused.
        publishWarmup(Libre3WarmupState.Phase.READY)
        // The restart mark is written by the plugin, after the reading has reached the database.
        // Writing it here would raise it for a reading that was then dropped, and that reading
        // could never be offered again.
        watchers.forEach { it.onGlucose(sample) }
    }

    private fun publishWarmup(
        phase: Libre3WarmupState.Phase,
        remainingMs: Long? = null,
        message: String? = null,
    ) {
        warmup = Libre3WarmupState(phase = phase, remainingMs = remainingMs, message = message)
        watchers.forEach { it.onWarmup(warmup) }
    }

    private fun refusalMessage(refusal: Libre3StartRefusal): String = when (refusal) {
        Libre3StartRefusal.NO_SENSOR_STORED         -> "no sensor has been scanned yet"
        Libre3StartRefusal.PIN_NOT_WRITTEN_YET      -> "the sensor was not stored yet"
        Libre3StartRefusal.FIRST_PAIR_NOT_AVAILABLE -> "a new sensor cannot be paired by this build"
    }

    companion object {

        /** How long one wait for a sensor message lasts before the link is checked again. */
        const val DATA_WAIT_MS = 90_000L

        /** Name of the slot that feeds the loop, used in the thread name and in every log line. */
        private const val SLOT_PRODUCTION = "prod"

        /** Name of the pre-soak slot, used in the thread name and in every log line. */
        private const val SLOT_STAGING = "presoak"

        /** Makes every instance's arbiter token different, including two instances of one slot. */
        private val nextInstance = AtomicInteger(0)
    }
}
