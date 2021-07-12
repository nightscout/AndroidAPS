package info.nightscout.androidaps.plugins.pump.insight.satl

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class ErrorMessage : SatlMessage() {

    lateinit var error: SatlError
        private set

    override fun parse(byteBuf: ByteBuf) {
        error = SatlError.fromId(byteBuf.readByte())
    }
}