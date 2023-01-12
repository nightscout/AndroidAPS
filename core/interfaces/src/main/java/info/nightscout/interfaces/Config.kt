package info.nightscout.interfaces

@Suppress("PropertyName")
interface Config {
    val SUPPORTED_NS_VERSION: Int
    val APS: Boolean
    val NSCLIENT: Boolean
    val PUMPCONTROL: Boolean
    val PUMPDRIVERS: Boolean
    val FLAVOR: String
    val VERSION_NAME: String
    val BUILD_VERSION: String
    val REMOTE: String
    val BUILD_TYPE: String
    val VERSION: String
    val APPLICATION_ID: String
    val DEBUG: Boolean
    val currentDeviceModelString : String
    val appName: Int

    fun isEngineeringModeOrRelease(): Boolean
    fun isEngineeringMode(): Boolean
    fun isUnfinishedMode(): Boolean
    fun isDev(): Boolean
}