package app.aaps.pump.insight.satl

import app.aaps.pump.insight.utils.ByteBuf

class VerifyConfirmRequest : SatlMessage() {

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(2)
            byteBuf.putUInt16LE(PairingStatus.CONFIRMED.id)
            return byteBuf
        }
}