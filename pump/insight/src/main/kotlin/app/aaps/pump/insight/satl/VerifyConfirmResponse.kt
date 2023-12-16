package app.aaps.pump.insight.satl

import app.aaps.pump.insight.utils.ByteBuf

class VerifyConfirmResponse : SatlMessage() {

    lateinit var pairingStatus: PairingStatus
        private set

    override fun parse(byteBuf: ByteBuf) {
        pairingStatus = PairingStatus.fromId(byteBuf.readUInt16LE())
    }
}