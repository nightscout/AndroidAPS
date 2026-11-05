package app.aaps.plugins.source

import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.plugins.dexcomoneplus.identity.OnePlusStoredSession
import app.aaps.plugins.dexcomoneplus.session.OnePlusSessionStart

/**
 * The rules of direct-to-watch on the phone side, with no platform in them, so they are tested on the
 * JVM.
 *
 * Direct-to-watch: a Wear OS watch reads the same sensor in the sensor's watch slot, with its own
 * key, next to the phone. The phone stays the master: it decides which sensor the watch follows and
 * when the watch lets it go. The watch's readings stay on the watch.
 *
 * Behaviour follows the phone handover of nightscout/AndroidAPS pull request 5397
 * (`G7WatchHandover`), re-written for this driver's sensor store and its two sensor slots.
 */
object DexcomOnePlusWatchDirectRules {

    /** At most one corrective message a minute, so a watch that cannot follow is not flooded. */
    const val RESEND_INTERVAL_MS = 60_000L

    /** What the watch needs to read the production sensor itself. */
    data class Sensor(
        val address: String?,
        val pairingCode: String?,
        val serial: String?,
        val sensorName: String?,
        /**
         * True when this phone has a key for the sensor. The phone always asks for the phone slot,
         * so a key means this phone holds the phone slot and the watch slot is the one left free.
         */
        val holdsPhoneSlot: Boolean,
    ) {

        // Never print the pairing code in a log line.
        override fun toString(): String =
            "Sensor(address=$address, serial=$serial, sensorName=$sensorName, holdsPhoneSlot=$holdsPhoneSlot)"
    }

    /**
     * The production sensor, as the handover sees it. Only the PRODUCTION store may be passed here:
     * a pre-soak sensor is never handed to the watch. Null when no sensor is stored.
     */
    fun sensorOf(production: OnePlusStoredSession?): Sensor? {
        if (production == null) return null
        return Sensor(
            address = production.lastMac,
            pairingCode = production.identity.pin,
            serial = production.identity.serial,
            sensorName = production.lastDeviceName,
            holdsPhoneSlot = production.sharedKey?.size == SHARED_KEY_SIZE,
        )
    }

    /**
     * True when the phone can hand [sensor] to the watch: it has an address and a valid pairing code,
     * and the phone holds the phone slot of it.
     */
    fun isAvailable(sensor: Sensor?): Boolean =
        sensor != null &&
            sensor.holdsPhoneSlot &&
            !sensor.address.isNullOrBlank() &&
            sensor.pairingCode != null &&
            OnePlusSessionStart.isValidPairingCode(sensor.pairingCode)

    /** The message the watch should act on now. Inactive when off, or when nothing can be handed over. */
    fun wanted(enabled: Boolean, sensor: Sensor?): EventData.CgmWatchDirect =
        if (enabled && sensor != null && isAvailable(sensor))
            EventData.CgmWatchDirect(
                active = true,
                pairingCode = sensor.pairingCode,
                address = sensor.address,
                serial = sensor.serial,
                sensorName = sensor.sensorName,
            )
        else EventData.CgmWatchDirect(active = false)

    /** True when the watch already does what the phone wants. */
    fun matches(wanted: EventData.CgmWatchDirect, status: EventData.CgmWatchStatus): Boolean = when {
        // A watch that cannot run it has nothing to change.
        status.state == EventData.CgmWatchState.NOT_SUPPORTED -> true
        wanted.active                                          -> status.state != EventData.CgmWatchState.OFF && status.address == wanted.address
        else                                                   -> status.state == EventData.CgmWatchState.OFF
    }

    /**
     * True when [wanted] has to go to the watch again because it differs from what was [lastSent].
     * The serial and the name alone do not count: they do not change what the watch connects to.
     */
    fun changed(lastSent: EventData.CgmWatchDirect?, wanted: EventData.CgmWatchDirect): Boolean =
        lastSent == null ||
            lastSent.active != wanted.active ||
            lastSent.address != wanted.address ||
            lastSent.pairingCode != wanted.pairingCode

    /**
     * True when a watch [status] that does not match [wanted] earns a corrective message now, at
     * most once per [RESEND_INTERVAL_MS].
     */
    fun shouldCorrect(wanted: EventData.CgmWatchDirect, status: EventData.CgmWatchStatus, lastCorrectionAtMs: Long, nowMs: Long): Boolean =
        !matches(wanted, status) && nowMs - lastCorrectionAtMs >= RESEND_INTERVAL_MS

    private const val SHARED_KEY_SIZE = 16
}
