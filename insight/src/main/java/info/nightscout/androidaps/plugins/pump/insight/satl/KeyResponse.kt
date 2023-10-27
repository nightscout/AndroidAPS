package info.nightscout.androidaps.plugins.pump.insight.satl

import info.nightscout.androidaps.plugins.pump.insight.satl.SatlMessage
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class KeyResponse : SatlMessage() {

    lateinit var randomData: ByteArray
        private set
    lateinit var preMasterSecret: ByteArray
        private set

    override fun parse(byteBuf: ByteBuf) {
        randomData = byteBuf.readBytes(28)
        byteBuf.shift(4)
        preMasterSecret = byteBuf.getBytes(256)
    }
}