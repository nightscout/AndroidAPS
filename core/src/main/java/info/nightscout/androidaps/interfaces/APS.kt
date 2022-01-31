package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.aps.loop.APSResult

interface APS {

    val lastAPSResult: APSResult?
    val lastAPSRun: Long
    operator fun invoke(initiator: String, tempBasalFallback: Boolean)
}