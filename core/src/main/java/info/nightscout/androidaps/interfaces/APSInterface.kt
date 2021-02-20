package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.aps.loop.APSResult

interface APSInterface {

    val lastAPSResult: APSResult?
    val lastAPSRun: Long
    operator fun invoke(initiator: String, tempBasalFallback: Boolean)
}