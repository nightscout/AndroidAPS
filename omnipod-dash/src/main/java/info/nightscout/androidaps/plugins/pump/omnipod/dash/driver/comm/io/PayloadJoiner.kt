package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.io

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.packet.BlePacket
import java.io.ByteArrayOutputStream

sealed class PayloadJoinerAction

class PayloadJoinerActionAccept(): PayloadJoinerAction()
class PayloadJoinerActionReject(val idx: Byte): PayloadJoinerAction()

class PayloadJoiner() {
    var oneExtra: Boolean=false

    private val payload = ByteArrayOutputStream()

    fun start(payload: ByteArray): Int {
        return 0;
    }

    fun accumulate(payload: ByteArray): PayloadJoinerAction {
        return PayloadJoinerActionAccept()
    }

    fun finalize(): PayloadJoinerAction {
        return PayloadJoinerActionAccept()

    }
    
    fun bytes(): ByteArray {
        return ByteArray(0);
    }


}