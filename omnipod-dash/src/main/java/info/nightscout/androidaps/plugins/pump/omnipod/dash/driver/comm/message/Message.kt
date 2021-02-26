package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message

interface Message {

    /**
     *
     */
    fun messagePacket(): MessagePacket
}