package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.plugins.iob.iobCobCalculator.AutosensResult
import info.nightscout.interfaces.aps.APSResult

interface APS {

    val lastAPSResult: APSResult?
    val lastAPSRun: Long
    var lastDetermineBasalAdapter: DetermineBasalAdapterInterface?
    var lastAutosensResult: AutosensResult

    operator fun invoke(initiator: String, tempBasalFallback: Boolean)
}