package info.nightscout.interfaces.aps

interface APS {

    val lastAPSResult: APSResult?
    val lastAPSRun: Long
    var lastDetermineBasalAdapter: DetermineBasalAdapter?
    var lastAutosensResult: AutosensResult

    fun isEnabled(): Boolean
    fun invoke(initiator: String, tempBasalFallback: Boolean)
}