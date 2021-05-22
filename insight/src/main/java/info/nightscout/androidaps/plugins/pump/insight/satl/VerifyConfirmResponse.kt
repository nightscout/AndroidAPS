package info.nightscout.androidaps.plugins.pump.insight.satl;

import info.nightscout.androidaps.plugins.pump.insight.utils.ByteBuf;

public class VerifyConfirmResponse extends SatlMessage {

    private PairingStatus pairingStatus;

    @Override
    protected void parse(ByteBuf byteBuf) {
        pairingStatus = PairingStatus.Companion.fromId(byteBuf.readUInt16LE());
    }

    public PairingStatus getPairingStatus() {
        return this.pairingStatus;
    }
}
