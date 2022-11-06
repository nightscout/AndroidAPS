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
    val DEBUG: Boolean
    val currentDeviceModelString : String
}