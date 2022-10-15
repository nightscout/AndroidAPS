package info.nightscout.androidaps.interfaces

interface BuildHelper {

    fun isEngineeringModeOrRelease(): Boolean
    fun isEngineeringMode(): Boolean
    fun isDev(): Boolean
}
