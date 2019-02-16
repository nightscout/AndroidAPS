package info.nightscout.androidaps.plugins.PumpInsightLocal.satl;

import info.nightscout.androidaps.plugins.PumpInsightLocal.utils.ByteBuf;

public class KeyResponse extends SatlMessage {

    private byte[] randomData;
    private byte[] preMasterSecret;

    @Override
    protected void parse(ByteBuf byteBuf) {
        randomData = byteBuf.readBytes(28);
        byteBuf.shift(4);
        preMasterSecret = byteBuf.getBytes(256);
    }

    public byte[] getRandomData() {
        return this.randomData;
    }

    public byte[] getPreMasterSecret() {
        return this.preMasterSecret;
    }
}
