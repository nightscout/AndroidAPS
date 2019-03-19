package info.nightscout.androidaps.plugins.PumpInsightLocal.satl;

import info.nightscout.androidaps.plugins.PumpInsightLocal.ids.PairingStatusIDs;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class VerifyConfirmRequest extends SatlMessage {

    @Override
    protected ByteBuf getData() {
        ByteBuf byteBuf = new ByteBuf(2);
        byteBuf.putUInt16LE(PairingStatusIDs.IDS.getID(PairingStatus.CONFIRMED));
        return byteBuf;
    }
}
