package info.nightscout.interfaces.aps

interface APS {

    val lastAPSResult: APSResult?
    val lastAPSRun: Long
    var lastDetermineBasalAdapter: DetermineBasalAdapter?
    var lastAutosensResult: AutosensResult

    operator fun invoke(initiator: String, tempBasalFallback: Boolean)
}