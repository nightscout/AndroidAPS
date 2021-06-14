package info.nightscout.androidaps.plugins.pump.insight.satl

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class VerifyConfirmResponse : SatlMessage() {

    var pairingStatus: PairingStatus? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        pairingStatus = PairingStatus.fromId(byteBuf.readUInt16LE())
    }
}