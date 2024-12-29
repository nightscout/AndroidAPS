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
    var closedLoopEnabled: Constraint<Boolean>?
    val isSuspended: Boolean
    val isLGS: Boolean
    val isSuperBolus: Boolean
    val isDisconnected: Boolean

    var lastBgTriggeredRun: Long

    fun invoke(initiator: String, allowNotification: Boolean, tempBasalFallback: Boolean = false)

    fun acceptChangeRequest()
    fun minutesToEndOfSuspend(): Int
    fun goToZeroTemp(durationInMinutes: Int, profile: Profile, reason: OE.Reason, action: Action, source: Sources, listValues: List<ValueWithUnit> = listOf())
    fun suspendLoop(durationInMinutes: Int, action: Action, source: Sources, note: String? = null, listValues: List<ValueWithUnit>)
    fun disableCarbSuggestions(durationMinutes: Int)
    fun buildAndStoreDeviceStatus(reason: String)

    fun entries(): Array<CharSequence>
    fun entryValues(): Array<CharSequence>
}