package info.nightscout.androidaps.plugins.pump.insight.satl

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf

class DataMessage : SatlMessage() {

    public override var data: ByteBuf? = null
    override fun parse(byteBuf: ByteBuf?) {
        data = byteBuf
    }
}