package app.aaps.plugins.dexcomoneplus

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.source.SensorSlot
import kotlin.concurrent.Volatile

/**
 * The driver instances for the two sensor slots. Production always runs the real driver, never
 * [OnePlusCgmDriverStub]: a source that is switched on but never connects is worse than no source
 * at all.
 *
 * The instances are plain nullable fields and not `by lazy` on purpose.
 *
 * A driver that was shut down can never open a session again, because `shutdown` also stops its
 * executor for good. With `by lazy` the dead instance would be handed out for ever, and after one
 * promotion there would be no working production driver left. A dropped field is rebuilt on the
 * next call, so a second pre-soak and a second promotion still work.
 */
object OnePlusCgmDrivers {

    /** Key-value namespace for the staging slot's sensor store. */
    const val STAGING_NAMESPACE = "staging"

    private val lock = AapsLock()

    @Volatile
    private var productionReal: OnePlusCgmDriverReal? = null

    /**
     * Second, independent Real driver for the STAGING (pre-soak) sensor — its own BLE session,
     * executor, scanner and namespaced sensor store, so it can run concurrently with production
     * without sharing identity / key / ingest markers.
     *
     * ⚠️ This needs a platform that allows two Bluetooth scans at once, as Android does.
     */
    @Volatile
    private var stagingReal: OnePlusCgmDriverReal? = null

    /**
     * Sensor-store namespace of a slot — the ONE definition, shared by the drivers and by any UI that
     * opens a store (Start screen). null = production's original single-sensor file (non-breaking).
     *
     * Using the production store for the staging slot is what made a pre-soak silently adopt the
     * sensor already in use: same stored MAC, same PIN, same KEKS key, so no second sensor was ever
     * started and no new PIN could stick.
     */
    fun storeNamespace(slot: SensorSlot): String? = when (slot) {
        SensorSlot.PRODUCTION -> null
        SensorSlot.STAGING    -> STAGING_NAMESPACE
    }

    /** The real production instance. Built on first use. */
    fun realProduction(): OnePlusCgmDriverReal = lock.withLock {
        productionReal ?: OnePlusCgmDriverReal().also { productionReal = it }
    }

    /** The dedicated STAGING driver instance (pre-soak second sensor). Built on first use. */
    fun staging(): OnePlusCgmDriverReal = lock.withLock {
        stagingReal ?: OnePlusCgmDriverReal(storeNamespace = STAGING_NAMESPACE).also { stagingReal = it }
    }

    /** The driver feeding the loop. */
    fun default(): OnePlusCgmDriver = realProduction()

    /** The production driver, with [watcher] attached. Adding the same watcher twice is harmless. */
    fun production(watcher: OnePlusGlucoseWatcher): OnePlusCgmDriver =
        realProduction().also { it.addWatcher(watcher) }

    /**
     * Shuts both instances down and drops them, so the next call builds fresh ones. Tests use it to
     * start from a registry with no live session and no leftover pre-soak.
     */
    fun shutdownAll() {
        val instances = lock.withLock {
            val both = listOfNotNull(productionReal, stagingReal)
            productionReal = null
            stagingReal = null
            both
        }
        // Outside the lock: shutdown disconnects GATT.
        instances.forEach { runCatching { it.shutdown() } }
    }

    /**
     * Hands the staging instance the production role, keeping its live BLE link.
     *
     * Under [lock]: the promoted instance is pointed at the production preferences file and becomes
     * the production instance; the staging pointer is freed so the next pre-soak builds a fresh
     * instance. Without this swap, `default()` kept handing out the OLD (shut down) production
     * instance for ever after a promotion — every screen that reads it directly (Status, Warmup)
     * showed stale state, and the reconnect watchdog (which also reads `default()`) silently stopped
     * protecting the promoted sensor.
     *
     * ⚠️ Do not turn these fields back into `by lazy`. A retired instance must be dropped, or the
     * second promotion, days later, would hand out a driver whose executor is already stopped.
     *
     * @return the instance that has just been retired, for the caller to shut down **outside** the
     *   lock, or null when there was no staging instance to promote.
     */
    fun promoteStagingInstance(): OnePlusCgmDriverReal? =
        lock.withLock {
            val promoted = stagingReal ?: return@withLock null
            val retired = productionReal
            promoted.rebindStore(null)
            productionReal = promoted
            stagingReal = null
            retired
        }
}
