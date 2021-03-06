package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt.Nonce

data class SessionKeys(val ck: ByteArray, val nonce: Nonce, var msgSequenceNumber: Byte) {
    init {
        require(ck.size == 16) { "CK has to be 16 bytes long" }
    }
}
