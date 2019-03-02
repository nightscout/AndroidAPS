package info.nightscout.androidaps.plugins.pump.insight.satl;

import info.nightscout.androidaps.plugins.pump.insight.ids.SatlErrorIDs;
import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

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
