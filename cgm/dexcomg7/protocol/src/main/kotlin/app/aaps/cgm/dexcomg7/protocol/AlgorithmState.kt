package app.aaps.cgm.dexcomg7.protocol

/**
 * The sensor's own view of its session, sent with every reading.
 *
 * Only [State.OK] gives a glucose value that may be used for dosing. Values not in the table are kept
 * as their raw number, so a log still shows what the sensor said.
 */
data class AlgorithmState(val raw: Int) {

    enum class State(val code: Int) {

        STOPPED(1),
        WARMUP(2),
        EXCESS_NOISE(3),
        FIRST_OF_TWO_BGS_NEEDED(4),
        SECOND_OF_TWO_BGS_NEEDED(5),
        OK(6),
        NEEDS_CALIBRATION(7),
        CALIBRATION_ERROR_1(8),
        CALIBRATION_ERROR_2(9),
        CALIBRATION_LINEARITY_FIT_FAILURE(10),
        SENSOR_FAILED_COUNTS_ABERRATION(11),
        SENSOR_FAILED_RESIDUAL_ABERRATION(12),
        OUT_OF_CALIBRATION_OUTLIER(13),
        OUTLIER_CALIBRATION_REQUEST(14),
        SESSION_EXPIRED(15),
        SESSION_FAILED_UNRECOVERABLE_ERROR(16),
        SESSION_FAILED_TRANSMITTER_ERROR(17),
        TEMPORARY_SENSOR_ISSUE(18),
        SENSOR_FAILED_PROGRESSIVE_DECLINE(19),
        SENSOR_FAILED_HIGH_COUNTS_ABERRATION(20),
        SENSOR_FAILED_LOW_COUNTS_ABERRATION(21),
        SENSOR_FAILED_RESTART(22),
        EXPIRED(24),
        SENSOR_FAILED(25),
        SESSION_ENDED(26),
        TRANSMITTER_FAILED(27),
        SIV_FAILED(28),
        SESSION_FAILED_OUT_OF_RANGE(29)
    }

    val state: State? = State.entries.firstOrNull { it.code == raw }

    val sensorFailed: Boolean
        get() = when (state) {
            State.SENSOR_FAILED, State.SENSOR_FAILED_COUNTS_ABERRATION, State.SENSOR_FAILED_RESIDUAL_ABERRATION,
            State.SESSION_FAILED_TRANSMITTER_ERROR, State.SESSION_FAILED_UNRECOVERABLE_ERROR,
            State.SENSOR_FAILED_PROGRESSIVE_DECLINE, State.SENSOR_FAILED_HIGH_COUNTS_ABERRATION,
            State.SENSOR_FAILED_LOW_COUNTS_ABERRATION, State.SENSOR_FAILED_RESTART, State.TRANSMITTER_FAILED,
            State.SIV_FAILED, State.SESSION_FAILED_OUT_OF_RANGE -> true

            else -> false
        }

    val isInWarmup: Boolean get() = state == State.WARMUP

    val hasTemporaryError: Boolean get() = state == State.TEMPORARY_SENSOR_ISSUE

    /** True only for [State.OK]: the one state whose value may be given to the loop. */
    val hasReliableGlucose: Boolean get() = state == State.OK

    val isSessionEnded: Boolean get() = state == State.SESSION_ENDED

    override fun toString(): String = state?.name ?: "UNKNOWN($raw)"
}
