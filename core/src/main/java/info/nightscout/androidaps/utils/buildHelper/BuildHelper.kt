package info.nightscout.androidaps.utils.buildHelper

interface BuildHelper {

    fun isEngineeringModeOrRelease(): Boolean
    fun isEngineeringMode(): Boolean
    fun isDev(): Boolean
}
