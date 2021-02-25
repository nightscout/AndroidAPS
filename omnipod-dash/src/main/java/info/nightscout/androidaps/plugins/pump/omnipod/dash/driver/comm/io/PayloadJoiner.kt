package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.BlePacket
import java.io.ByteArrayOutputStream

class PayloadJoiner() {
    private val payload = ByteArrayOutputStream()

    fun accumulate(packet: BlePacket) {

    }

    fun bytes(): ByteArray {
        return ByteArray(0);
    }
}