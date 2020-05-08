package info.nightscout.androidaps.interfaces

interface ConfigInterface {
    val SUPPORTEDNSVERSION: Int
    val APS: Boolean
    val NSCLIENT: Boolean
    val PUMPCONTROL: Boolean
    val PUMPDRIVERS: Boolean
}