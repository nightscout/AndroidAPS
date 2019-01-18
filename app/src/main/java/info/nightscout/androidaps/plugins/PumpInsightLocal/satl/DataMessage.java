package info.nightscout.androidaps.plugins.PumpInsightLocal.satl;

import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class DataMessage extends SatlMessage {

    private ByteBuf data;

    @Override
    protected void parse(ByteBuf byteBuf) {
        data = byteBuf;
    }

    public ByteBuf getData() {
        return this.data;
    }

    public void setData(ByteBuf data) {
        this.data = data;
    }
}
