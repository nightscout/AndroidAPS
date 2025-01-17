package app.aaps.pump.insight.satl

import app.aaps.pump.insight.utils.ByteBuf

class DataMessage : SatlMessage() {

    public override lateinit var data: ByteBuf

    override fun parse(byteBuf: ByteBuf) {
        data = byteBuf
    }
}