package info.nightscout.interfaces

@Suppress("PropertyName")
interface Config {
    val SUPPORTED_NS_VERSION: Int
    val APS: Boolean
    val NSCLIENT: Boolean // aapsclient || aapsclient2
    val NSCLIENT1: Boolean // aapsclient
    val NSCLIENT2: Boolean // aapsclient2
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

    var appInitialized: Boolean

    fun isEngineeringModeOrRelease(): Boolean
    fun isEngineeringMode(): Boolean
    fun isUnfinishedMode(): Boolean
    fun isDev(): Boolean
}