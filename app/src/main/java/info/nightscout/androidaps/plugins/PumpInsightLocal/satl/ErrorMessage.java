package info.nightscout.androidaps.plugins.PumpInsightLocal.satl;

import info.nightscout.androidaps.plugins.PumpInsightLocal.ids.SatlErrorIDs;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class ErrorMessage extends SatlMessage {

    private SatlError error;

    @Override
    protected void parse(ByteBuf byteBuf) {
        error = SatlErrorIDs.IDS.getType(byteBuf.readByte());
    }

    public SatlError getError() {
        return this.error;
    }
}
