package app.aaps.pump.insight.satl

import app.aaps.pump.insight.utils.ByteBuf

class ErrorMessage : SatlMessage() {

    lateinit var error: SatlError
        private set

    override fun parse(byteBuf: ByteBuf) {
        error = SatlError.fromId(byteBuf.readByte())
    }
}