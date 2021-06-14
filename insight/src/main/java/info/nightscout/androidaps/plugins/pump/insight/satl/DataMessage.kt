package info.nightscout.androidaps.plugins.pump.insight.satl

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class DataMessage : SatlMessage() {

    public override lateinit var data: ByteBuf

    override fun parse(byteBuf: ByteBuf) {
        data = byteBuf
    }
}