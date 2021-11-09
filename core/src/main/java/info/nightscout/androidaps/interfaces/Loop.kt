package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.database.entities.OfflineEvent
import info.nightscout.androidaps.plugins.aps.loop.APSResult

interface Loop {

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

    var lastRun: LastRun?
    val isSuspended: Boolean
    var enabled: Boolean

    fun minutesToEndOfSuspend(): Int
    fun goToZeroTemp(durationInMinutes: Int, profile: Profile, reason: OfflineEvent.Reason)
    fun suspendLoop(durationInMinutes: Int)
}