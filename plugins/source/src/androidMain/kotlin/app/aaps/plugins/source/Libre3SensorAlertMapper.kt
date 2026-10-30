package app.aaps.plugins.source

import app.aaps.core.interfaces.source.CgmSensorAlertState
import app.aaps.core.interfaces.source.CgmSensorFailure
import app.aaps.plugins.libre3.Libre3WarmupState
import app.aaps.plugins.libre3.parse.Libre3PatchStatus
import app.aaps.plugins.libre3.parse.Libre3SensorError

/**
 * What a health message of the sensor means for the sensor alarms.
 *
 * - [ERROR] the sensor reports a fault.
 * - [ENDED] the sensor says it has ended or was shut down.
 */
internal enum class Libre3SensorFault { ERROR, ENDED }

/**
 * Turns what the Libre 3 plugin knows into the one generic sensor alarm state.
 *
 * Pure, without Android and without the DI graph, so the mapping and its priority order are unit
 * tested on their own. The order is the one of
 * [app.aaps.core.interfaces.source.CgmSensorAlertProvider.sensorAlertState]: Failed > Expired >
 * SignalLost > Expiring > SecondSensorReady > WarmupDone > Ok.
 *
 * The end of the sensor is the end of its usable time: the life read by the NFC scan plus the
 * 12 h grace a Libre 3 still reads after it ([Libre3Staging.SENSOR_GRACE_MS]). The status screen
 * and the Overview sensor age use the same end ([Libre3Staging.computeLifecycle]).
 */
internal object Libre3SensorAlertMapper {

    private const val MINUTE_MS = 60L * 1000L

    /**
     * A Libre 3 sends a reading every minute. No reading for this long is a lost signal, in every
     * driver phase except warm-up and pairing (where no reading is expected).
     *
     * This only makes the state visible. The central check waits for the delay the user has set
     * (`AlertCgmSignalLostMinutes`, 5 to 60 min) before it warns, so the user setting wins.
     */
    const val SILENCE_MS = 5L * MINUTE_MS

    /** Phases in which the sensor sends no reading on purpose, so silence is not a lost signal. */
    private val SILENCE_EXPECTED = setOf(Libre3WarmupState.Phase.WARMING, Libre3WarmupState.Phase.PAIRING)

    /**
     * Everything the mapping needs. Times are wall-clock epoch ms.
     *
     * @param stopped the plugin is stopped: no alarm at all.
     * @param sensorStored a production sensor is stored. Without one, only a ready second sensor
     *   is worth a message.
     * @param phase the production driver phase.
     * @param activatedAtMs when the production sensor was started, 0 when not known.
     * @param wearMinutes the sensor life read by the NFC scan, null or 0 for the default.
     * @param fault what the health messages of the sensor say, null when they are good. See
     *   [Libre3SensorFaultFilter].
     * @param linkLostSinceMs the link is down (reconnecting) since then, null while it is up.
     * @param lastReadingAtMs when the last reading was accepted, null when none yet.
     * @param warmupDoneAtMs the first usable reading after a warm-up, null when none.
     * @param secondSensorReadyAtMs the pre-soak sensor is ready since then, null when it is not.
     * @param expiringLeadMs the warning time the user has set for "ending soon". "Ending soon" is
     *   published only from then on, so before that a ready second sensor is not hidden by an end
     *   that is not worth a warning yet.
     */
    data class Input(
        val stopped: Boolean,
        val sensorStored: Boolean,
        val phase: Libre3WarmupState.Phase,
        val activatedAtMs: Long,
        val wearMinutes: Int?,
        val fault: Libre3SensorFault?,
        val linkLostSinceMs: Long?,
        val lastReadingAtMs: Long?,
        val warmupDoneAtMs: Long?,
        val secondSensorReadyAtMs: Long?,
        val expiringLeadMs: Long,
        val nowMs: Long,
    )

    /**
     * The fault in one health message of the sensor, or null when the message is good. A sending
     * problem ([Libre3SensorError.TRANSMISSION_ERROR]) often passes by itself and is no fault.
     * A state that says "ended or in error" without a fault code counts as an end: [map] makes it
     * "expired" when the life is over and "ended early" before. A code this driver does not know
     * ([Libre3SensorError.UNKNOWN]) is an error here; [Libre3SensorFaultFilter] waits for a second
     * one before it counts.
     */
    fun faultOf(status: Libre3PatchStatus): Libre3SensorFault? = when {
        status.sensorError == Libre3SensorError.INSERTION_FAILURE -> Libre3SensorFault.ERROR

        status.sensorError == Libre3SensorError.EXPIRED ||
            status.sensorError == Libre3SensorError.TERMINATED ||
            status.patchState.isTerminated ||
            status.patchState.isExpiredOrError                    -> Libre3SensorFault.ENDED

        status.sensorError == Libre3SensorError.UNKNOWN           -> Libre3SensorFault.ERROR
        else                                                      -> null
    }

    fun map(input: Input): CgmSensorAlertState {
        if (input.stopped) return CgmSensorAlertState.Ok
        if (!input.sensorStored) {
            return input.secondSensorReadyAtMs?.let { CgmSensorAlertState.SecondSensorReady(it) } ?: CgmSensorAlertState.Ok
        }
        val now = input.nowMs
        val known = input.activatedAtMs > 0L
        val lifeMs = input.wearMinutes?.takeIf { it > 0 }?.let { it * MINUTE_MS } ?: Libre3Staging.DEFAULT_LIFE_MS
        val lifeEndMs = input.activatedAtMs + lifeMs
        // The end of the usable time, the same as the status screen uses: life + grace.
        val expiresAtMs = lifeEndMs + Libre3Staging.SENSOR_GRACE_MS

        // Failed
        if (input.phase == Libre3WarmupState.Phase.FAILED) return CgmSensorAlertState.Failed(CgmSensorFailure.OTHER)
        when (input.fault) {
            Libre3SensorFault.ERROR -> return CgmSensorAlertState.Failed(CgmSensorFailure.SENSOR_ERROR)
            // An end the sensor reports itself: expired when its life is over, else it ended early.
            // Expired always carries the end of the usable time, never the moment the message was
            // seen: that moment changes with every restart and with the first health message after
            // the end, and each change would sound the urgent alarm again.
            Libre3SensorFault.ENDED ->
                return if (known && now >= lifeEndMs) CgmSensorAlertState.Expired(expiresAtMs)
                else CgmSensorAlertState.Failed(CgmSensorFailure.ENDED_EARLY)

            null                    -> Unit
        }
        // Expired
        if (known && now >= expiresAtMs) return CgmSensorAlertState.Expired(expiresAtMs)
        // SignalLost
        input.linkLostSinceMs?.let { return CgmSensorAlertState.SignalLost(it) }
        val last = input.lastReadingAtMs
        if (last != null && input.phase !in SILENCE_EXPECTED && now - last >= SILENCE_MS) {
            return CgmSensorAlertState.SignalLost(last)
        }
        // Expiring
        if (known && expiresAtMs - now <= input.expiringLeadMs) return CgmSensorAlertState.Expiring(expiresAtMs)
        // SecondSensorReady, WarmupDone
        input.secondSensorReadyAtMs?.let { return CgmSensorAlertState.SecondSensorReady(it) }
        input.warmupDoneAtMs?.let { return CgmSensorAlertState.WarmupDone(it) }
        return CgmSensorAlertState.Ok
    }
}

/**
 * Turns the stream of health messages of the sensor into the fault the alarms use.
 *
 * A known fault code or an end counts at once. A code this driver does not know
 * ([Libre3SensorError.UNKNOWN]) counts only after [UNKNOWN_ERRORS_TO_FAIL] health messages with
 * an error in a row: one odd message must not sound the urgent alarm. A first unknown code changes
 * nothing, and any other message (a good one included) starts the count again.
 *
 * Not thread safe: the plugin calls it under its own lock.
 */
internal class Libre3SensorFaultFilter {

    /** The fault the alarms use now, null when there is none. */
    var fault: Libre3SensorFault? = null
        private set

    private var unknownInARow = 0

    /** Takes one health message into account and returns [fault]. */
    fun onStatus(status: Libre3PatchStatus): Libre3SensorFault? {
        val raw = Libre3SensorAlertMapper.faultOf(status)
        if (raw == Libre3SensorFault.ERROR && status.sensorError == Libre3SensorError.UNKNOWN) {
            unknownInARow++
            if (unknownInARow >= UNKNOWN_ERRORS_TO_FAIL) fault = Libre3SensorFault.ERROR
        } else {
            unknownInARow = 0
            fault = raw
        }
        return fault
    }

    /** Forgets everything, for a new sensor. */
    fun reset() {
        fault = null
        unknownInARow = 0
    }

    companion object {

        /** Health messages with an unknown error code in a row before they count as a fault. */
        const val UNKNOWN_ERRORS_TO_FAIL = 2
    }
}
