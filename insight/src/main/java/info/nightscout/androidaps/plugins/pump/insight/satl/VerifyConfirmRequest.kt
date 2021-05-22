package info.nightscout.androidaps.plugins.pump.insight.satl;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class VerifyConfirmRequest extends SatlMessage {

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(2);
        byteBuf.putUInt16LE(PairingStatus.CONFIRMED.getId());
        return byteBuf;
    }
}
