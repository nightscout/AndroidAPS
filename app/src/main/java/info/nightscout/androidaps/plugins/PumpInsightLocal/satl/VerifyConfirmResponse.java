package info.nightscout.androidaps.plugins.PumpInsightLocal.satl;

import info.nightscout.androidaps.plugins.PumpInsightLocal.ids.PairingStatusIDs;
import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class VerifyConfirmResponse extends SatlMessage {

    private PairingStatus pairingStatus;

    @Override
    protected void parse(ByteBuf byteBuf) {
        pairingStatus = PairingStatusIDs.IDS.getType(byteBuf.readUInt16LE());
    }

    public PairingStatus getPairingStatus() {
        return this.pairingStatus;
    }
}
