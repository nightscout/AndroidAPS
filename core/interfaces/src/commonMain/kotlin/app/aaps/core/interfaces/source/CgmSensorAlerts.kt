package app.aaps.core.interfaces.source

import kotlinx.coroutines.flow.StateFlow

/**
 * Implemented by a [BgSource] that knows the state of its sensor and wants AAPS to raise the
 * sensor alarms for it (sensor failed, ended, ending soon, no signal, warm-up done, second sensor
 * ready).
 *
 * Optional: a source that does not implement it gets no sensor alarm at all, and nothing changes
 * for it. The alarms are raised in one central place (`LocalAlertUtils.checkSensorAlerts`), which
 * reads the active source as `activeBgSource as? CgmSensorAlertProvider`.
 *
 * The source only says what is true now. The user settings (which alarm is on, how early "ending
 * soon" warns, how long a signal may be lost) are applied by the central check. The one exception
 * is the warning time, which the source may also read, see [CgmSensorAlertState.Expiring].
 *
 * "Ending soon" and "ended" are measured against the end of the sensor's usable time, including
 * any grace period the source knows of (for Libre 3: the sensor life plus 12 h). The source shows
 * the same end on its own status screen.
 */
interface CgmSensorAlertProvider {

    /**
     * The most important sensor state right now. One state at a time, in this order:
     * [CgmSensorAlertState.Failed] > [CgmSensorAlertState.Expired] > [CgmSensorAlertState.SignalLost] >
     * [CgmSensorAlertState.Expiring] > [CgmSensorAlertState.SecondSensorReady] >
     * [CgmSensorAlertState.WarmupDone] > [CgmSensorAlertState.Ok].
     *
     * A stopped source publishes [CgmSensorAlertState.Ok].
     *
     * The states that only depend on the clock ([CgmSensorAlertState.Expiring],
     * [CgmSensorAlertState.Expired], and [CgmSensorAlertState.SignalLost] when no reading came) may
     * change while nothing happens in the driver. A source may compute them again only when the
     * state is read, without a timer of its own. So a reader should read [StateFlow.value] when it
     * needs the state (the central check does so on every pass), and not rely on collecting the
     * flow alone. Reading it must be cheap and safe from any thread.
     */
    val sensorAlertState: StateFlow<CgmSensorAlertState>
}

/**
 * State of the sensor, for the sensor alarms. Times are wall-clock epoch ms.
 *
 * Each timestamp also names the episode: the same state with the same timestamp is the same
 * episode, and its alarm is raised only once.
 */
sealed interface CgmSensorAlertState {

    /** Nothing to report. */
    data object Ok : CgmSensorAlertState

    /** The first usable reading after a warm-up arrived at [atEpochMs]. */
    data class WarmupDone(val atEpochMs: Long) : CgmSensorAlertState

    /**
     * The usable time of the sensor ends at [endsAtEpochMs], including any grace period (for
     * Libre 3: the sensor life plus 12 h).
     *
     * The source publishes it from the warning time the user has set (`AlertCgmExpiringLeadHours`)
     * when no more important state applies. Not earlier: it would hide a less important state, for
     * example a ready second sensor, while there is nothing to warn about yet. The central check
     * also waits for the warning time before it warns.
     */
    data class Expiring(val endsAtEpochMs: Long) : CgmSensorAlertState

    /**
     * The usable time of the sensor ended at [atEpochMs], including any grace period, see
     * [Expiring].
     *
     * [atEpochMs] must stay the same for the whole episode: the end of the usable time when the
     * source knows it, also when the sensor reports its end by itself and after an app restart.
     * Never the moment a message happened to be seen, because each new value is a new episode and
     * sounds the urgent alarm again.
     */
    data class Expired(val atEpochMs: Long) : CgmSensorAlertState

    /** The sensor cannot be used. No free text, so the user always sees a translated message. */
    data class Failed(val reason: CgmSensorFailure) : CgmSensorAlertState

    /**
     * There has been no link to the sensor, or no reading from it, since [sinceEpochMs]. The source
     * publishes it at once; the central check waits for the delay the user has set before it warns.
     */
    data class SignalLost(val sinceEpochMs: Long) : CgmSensorAlertState

    /** A pre-soaked second sensor is ready since [atEpochMs], so the user can switch to it. */
    data class SecondSensorReady(val atEpochMs: Long) : CgmSensorAlertState
}

/** Why a sensor cannot be used, see [CgmSensorAlertState.Failed]. */
enum class CgmSensorFailure {

    /** The sensor itself reports a fault. */
    SENSOR_ERROR,

    /** The sensor stopped before the end of its life. */
    ENDED_EARLY,

    /** Any other final failure, for example the sensor cannot be paired. */
    OTHER
}
