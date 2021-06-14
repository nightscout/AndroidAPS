package info.nightscout.androidaps.plugins.pump.insight.satl

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class ErrorMessage : SatlMessage() {

    var error: SatlError? = null
        private set

    override fun parse(byteBuf: ByteBuf) {
        error = SatlError.fromId(byteBuf.readByte())
    }
}