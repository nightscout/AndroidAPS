package info.nightscout.androidaps.interfaces

interface BuildHelper {

    fun isEngineeringModeOrRelease(): Boolean
    fun isEngineeringMode(): Boolean
    fun isUnfinishedMode(): Boolean
    fun isDev(): Boolean
}
