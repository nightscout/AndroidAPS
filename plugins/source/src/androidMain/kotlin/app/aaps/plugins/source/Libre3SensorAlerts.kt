package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmSensorAlertRules
import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import app.aaps.core.interfaces.source.StagingState
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.plugins.libre3.parse.Libre3PatchStatus
import app.aaps.plugins.libre3.parse.Libre3SensorError

/**
 * What the Libre 3 driver says, turned into the generic sensor alarm state
 * ([app.aaps.core.interfaces.source.CgmSensorAlertProvider]).
 *
 * The plugin hands every driver event to it; the times of the changes are kept here, so each state
 * names its episode with a time that does not move. [state] then applies [CgmSensorAlertRules],
 * which holds the order of the states for every BG source.
 *
 * - The driver phase `FAILED` is a failed sensor.
 * - The link is lost from the first `RECONNECTING` phase, or from a session that ended because the
 *   link died. `CONNECTING` is not "link back": the driver passes through it on every retry.
 * - Warm-up is done at the first accepted reading after a `WARMING` phase.
 * - The end is the end of the life read by the NFC scan. A Libre 3 has no grace period.
 * - The health messages of the sensor ([onStatus]): a fault code is a sensor error, and an end the
 *   sensor reports by itself is "expired" when the life is over, else "ended early". A code this
 *   driver does not know counts only after [UNKNOWN_ERRORS_TO_FAIL] messages in a row, so one odd
 *   message does not sound the urgent alarm. A sending problem often passes by itself and is no fault.
 *
 * It only reads what the driver said. It changes nothing in the driver and nothing in the readings
 * the loop receives.
 *
 * Not thread safe on its own: the plugin calls it under one lock.
 */
internal class Libre3SensorAlerts {

    private var linkLostSinceMs: Long? = null
    private var lastReadingAtMs: Long? = null
    private var warmupEndPending = false
    private var warmupDoneAtMs: Long? = null
    private var secondSensorReadyAtMs: Long? = null
    private var fault: Fault? = null
    private var unknownInARow = 0

    /** What a health message of the sensor says for the alarms. */
    private enum class Fault { ERROR, ENDED }

    /** A new phase of the production driver. */
    fun onWarmup(phase: Libre3WarmupState.Phase, nowMs: Long) {
        when (phase) {
            Libre3WarmupState.Phase.RECONNECTING -> if (linkLostSinceMs == null) linkLostSinceMs = nowMs

            Libre3WarmupState.Phase.WARMING      -> {
                linkLostSinceMs = null
                warmupEndPending = true
            }

            Libre3WarmupState.Phase.READY,
            Libre3WarmupState.Phase.IDLE         -> linkLostSinceMs = null

            else                                 -> Unit
        }
    }

    /**
     * The session went up or down. Only a link that died on its own ([linkLost]) is a lost signal;
     * a session that somebody ended (a plugin switch, a disconnect) is not.
     */
    fun onSession(up: Boolean, linkLost: Boolean, nowMs: Long) {
        if (up) linkLostSinceMs = null
        else if (linkLost && linkLostSinceMs == null) linkLostSinceMs = nowMs
    }

    /** A reading of the production sensor was accepted at [acceptedAtMs]. */
    fun onReading(acceptedAtMs: Long) {
        lastReadingAtMs = acceptedAtMs
        linkLostSinceMs = null
        if (warmupEndPending) {
            warmupEndPending = false
            warmupDoneAtMs = acceptedAtMs
        }
    }

    /** After an app restart: the last stored reading, so a sensor that stays silent is still seen as silent. */
    fun restoreLastReading(atMs: Long?) {
        lastReadingAtMs = atMs
    }

    /** The state of the pre-soak slot. */
    fun onStaging(state: StagingState, nowMs: Long) {
        secondSensorReadyAtMs = if (state == StagingState.READY) secondSensorReadyAtMs ?: nowMs else null
    }

    /** One health message of the sensor that feeds the loop. */
    fun onStatus(status: Libre3PatchStatus) {
        val raw = faultOf(status)
        if (raw == Fault.ERROR && status.sensorError == Libre3SensorError.UNKNOWN) {
            unknownInARow++
            if (unknownInARow >= UNKNOWN_ERRORS_TO_FAIL) fault = Fault.ERROR
        } else {
            unknownInARow = 0
            fault = raw
        }
    }

    /**
     * A new sensor in the production slot: what was known of the old one is forgotten. The ready
     * second sensor is not about the production sensor, so it stays.
     */
    fun onNewSensor() {
        linkLostSinceMs = null
        lastReadingAtMs = null
        warmupEndPending = false
        warmupDoneAtMs = null
        fault = null
        unknownInARow = 0
    }

    private fun faultOf(status: Libre3PatchStatus): Fault? = when {
        status.sensorError == Libre3SensorError.INSERTION_FAILURE -> Fault.ERROR

        status.sensorError == Libre3SensorError.EXPIRED ||
            status.sensorError == Libre3SensorError.TERMINATED ||
            status.patchState.isTerminated ||
            status.patchState.isExpiredOrError                    -> Fault.ENDED

        status.sensorError == Libre3SensorError.UNKNOWN           -> Fault.ERROR
        else                                                      -> null
    }

    /**
     * The sensor alarm state now.
     *
     * @param stopped the plugin is stopped: no alarm at all.
     * @param sensorStored a production sensor is stored.
     * @param phase the production driver phase.
     * @param expiresAtMs the end of the sensor life, null when not known.
     */
    fun state(
        stopped: Boolean,
        sensorStored: Boolean,
        phase: Libre3WarmupState.Phase,
        expiresAtMs: Long?,
        nowMs: Long,
        expiringLeadMs: Long
    ): CgmSensorAlertState =
        CgmSensorAlertRules.map(
            CgmSensorAlertRules.Input(
                stopped = stopped,
                sensorStored = sensorStored,
                failure = when {
                    phase == Libre3WarmupState.Phase.FAILED -> CgmSensorFailure.OTHER
                    fault == Fault.ERROR                    -> CgmSensorFailure.SENSOR_ERROR
                    else                                    -> null
                },
                endedBySensor = fault == Fault.ENDED,
                expiresAtMs = expiresAtMs,
                linkLostSinceMs = linkLostSinceMs,
                lastReadingAtMs = lastReadingAtMs,
                readingExpected = phase != Libre3WarmupState.Phase.WARMING && phase != Libre3WarmupState.Phase.PAIRING,
                issueSinceMs = null,
                secondSensorReadyAtMs = secondSensorReadyAtMs,
                warmupDoneAtMs = warmupDoneAtMs,
                expiringLeadMs = expiringLeadMs,
                nowMs = nowMs,
            )
        )

    companion object {

        /** Health messages with an unknown error code in a row before they count as a fault. */
        const val UNKNOWN_ERRORS_TO_FAIL = 2
    }
}
