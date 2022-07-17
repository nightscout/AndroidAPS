package info.nightscout.androidaps.interfaces

interface Autotune {

    fun aapsAutotune(daysBack: Int, autoSwitch: Boolean, profileToTune: String = "", days: BooleanArray? = null)
    fun atLog(message: String)

    var lastRunSuccess: Boolean
    var calculationRunning: Boolean
}