package app.aaps.core.interfaces.aps

import app.aaps.core.data.model.RM
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult

interface Loop {

    /**
     * Check if plugin is currently enabled
     * @return true if enabled
     */
    fun isEnabled(): Boolean

    /**
     * Collected values from last APS run
     */
    class LastRun {

        var request: APSResult? = null
        var constraintsProcessed: APSResult? = null
        var tbrSetByPump: PumpEnactResult? = null
        var smbSetByPump: PumpEnactResult? = null
        var source: String? = null
        var lastAPSRun = System.currentTimeMillis()
        var lastTBREnact: Long = 0
        var lastSMBEnact: Long = 0
        var lastTBRRequest: Long = 0
        var lastSMBRequest: Long = 0
        var lastOpenModeAccept: Long = 0
    }

    /**
     * Last APS run result
     */
    var lastRun: LastRun?

    /**
     * Variable to store reasons of disabled loop
     */
    var closedLoopEnabled: Constraint<Boolean>?

    /**
     * Current running mode
     */
    val runningMode: RM.Mode

    /**
     * Current running mode
     */
    val runningModeRecord: RM

    /**
     * Allowed next (following) modes according to current running mode
     */
    fun allowedNextModes(): List<RM.Mode>

    /**
     * Handle RunningMode change
     * @param newRM New running mode if specified
     * @param durationInMinutes Duration for new mode in minutes
     * @return true if change is successful
     */
    fun handleRunningModeChange(newRM: RM.Mode, action: Action, source: Sources, listValues: List<ValueWithUnit> = emptyList(), durationInMinutes: Int = 0, profile: Profile): Boolean

    /**
     * Timestamp of last loop run triggered by new BG
     */
    var lastBgTriggeredRun: Long

    /**
     * Invoke new loop run
     *
     * @param initiator Identifies who triggered the run
     * @param allowNotification Allow notification to be sent (false in open loop mode)
     * @param tempBasalFallback true if called from failed SMB
     */
    fun invoke(initiator: String, allowNotification: Boolean, tempBasalFallback: Boolean = false)

    /**
     * Open loop mode trigger
     */
    fun acceptChangeRequest()

    /**
     * Returns minutes to end of suspended loop
     */
    fun minutesToEndOfSuspend(): Int

    fun disableCarbSuggestions(durationMinutes: Int)

    /**
     * Schedule building of device status before sending to NS
     *
     * @param reason Initiator
     */
    fun scheduleBuildAndStoreDeviceStatus(reason: String)
}