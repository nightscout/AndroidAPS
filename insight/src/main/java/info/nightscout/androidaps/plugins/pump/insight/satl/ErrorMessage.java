package info.nightscout.androidaps.plugins.pump.insight.satl;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class ErrorMessage extends SatlMessage {

    private SatlError error;

    @Override
    protected void parse(ByteBuf byteBuf) {
        error = SatlError.Companion.fromId(byteBuf.readByte());
    }

    public SatlError getError() {
        return this.error;
    }
}
