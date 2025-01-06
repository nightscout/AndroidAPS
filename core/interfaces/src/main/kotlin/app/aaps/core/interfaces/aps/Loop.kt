package app.aaps.core.interfaces.aps

import app.aaps.core.data.model.OE
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
     * Is loop suspended?
     */
    val isSuspended: Boolean

    /**
     * Is Low Glucose Suspended mode set?
     */
    val isLGS: Boolean

    /**
     * Is Superbolus running?
     */
    val isSuperBolus: Boolean

    /**
     * Is pump disconnected?
     */
    val isDisconnected: Boolean

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

    /**
     * Simulate pump disconnection
     */
    fun goToZeroTemp(durationInMinutes: Int, profile: Profile, reason: OE.Reason, action: Action, source: Sources, listValues: List<ValueWithUnit> = listOf())

    /**
     * Suspend loop
     */
    fun suspendLoop(durationInMinutes: Int, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>)
    fun disableCarbSuggestions(durationMinutes: Int)

    /**
     * Schedule building of device status before sending to NS
     *
     * @param reason Initiator
     */
    fun scheduleBuildAndStoreDeviceStatus(reason: String)

    /**
     * UI loop modes
     */
    fun entries(): Array<CharSequence>

    /**
     * loop modes
     */
    fun entryValues(): Array<CharSequence>
}