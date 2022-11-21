package info.nightscout.interfaces

@Suppress("PropertyName")
interface Config {
    val SUPPORTEDNSVERSION: Int
    val APS: Boolean
    val NSCLIENT: Boolean
    val PUMPCONTROL: Boolean
    val PUMPDRIVERS: Boolean
    val FLAVOR: String
    val VERSION_NAME: String
    val BUILD_VERSION: String
    val DEBUG: Boolean
    val currentDeviceModelString : String
    val appName: Int

    fun isEngineeringModeOrRelease(): Boolean
    fun isEngineeringMode(): Boolean
    fun isUnfinishedMode(): Boolean
    fun isDev(): Boolean
}