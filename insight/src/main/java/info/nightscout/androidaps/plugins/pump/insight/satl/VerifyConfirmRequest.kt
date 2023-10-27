package info.nightscout.androidaps.plugins.pump.insight.satl

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class VerifyConfirmRequest : SatlMessage() {

    override val data: ByteBuf
        get() {
            val byteBuf = ByteBuf(2)
            byteBuf.putUInt16LE(PairingStatus.CONFIRMED.id)
            return byteBuf
        }
}