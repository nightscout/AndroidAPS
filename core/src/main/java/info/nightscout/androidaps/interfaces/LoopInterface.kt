package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.aps.loop.APSResult
import info.nightscout.androidaps.utils.DateUtil

interface LoopInterface {

    class LastRun {
        var request: APSResult? = null
        var constraintsProcessed: APSResult? = null
        var tbrSetByPump: PumpEnactResult? = null
        var smbSetByPump: PumpEnactResult? = null
        var source: String? = null
        var lastAPSRun = DateUtil.now()
        var lastTBREnact: Long = 0
        var lastSMBEnact: Long = 0
        var lastTBRRequest: Long = 0
        var lastSMBRequest: Long = 0
        var lastOpenModeAccept: Long = 0
    }

    var lastRun: LastRun?
}