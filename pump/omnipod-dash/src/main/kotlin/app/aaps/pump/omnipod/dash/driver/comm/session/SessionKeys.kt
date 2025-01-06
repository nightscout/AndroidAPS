package app.aaps.pump.omnipod.dash.driver.comm.session

import app.aaps.pump.omnipod.dash.driver.comm.endecrypt.Nonce

sealed class SessionNegotiationResponse

data class SessionKeys(
    val ck: ByteArray,
    val nonce: Nonce,
    var msgSequenceNumber: Byte
) : SessionNegotiationResponse() {

    init {
        require(ck.size == 16) { "CK has to be 16 bytes long" }
    }
}

data class SessionNegotiationResynchronization(
    val synchronizedEapSqn: EapSqn,
    val msgSequenceNumber: Byte
) : SessionNegotiationResponse()
