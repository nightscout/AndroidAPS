package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.NonceResyncableMessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.MessageBlockType;
import info.nightscout.pump.common.utils.ByteUtil;

public class FaultConfigCommand extends NonceResyncableMessageBlock {
    private final byte tab5sub16;
    private final byte tab5sub17;
    private int nonce;

    public FaultConfigCommand(int nonce, byte tab5sub16, byte tab5sub17) {
        this.nonce = nonce;
        this.tab5sub16 = tab5sub16;
        this.tab5sub17 = tab5sub17;

        encode();
    }

    private void encode() {
        encodedData = ByteUtil.INSTANCE.getBytesFromInt(nonce);
        encodedData = ByteUtil.INSTANCE.concat(encodedData, tab5sub16);
        encodedData = ByteUtil.INSTANCE.concat(encodedData, tab5sub17);
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.FAULT_CONFIG;
    }

    @Override
    public int getNonce() {
        return nonce;
    }

    @Override
    public void setNonce(int nonce) {
        this.nonce = nonce;
        encode();
    }

    @Override @NonNull
    public String toString() {
        return "FaultConfigCommand{" +
                "tab5sub16=" + tab5sub16 +
                ", tab5sub17=" + tab5sub17 +
                ", nonce=" + nonce +
                '}';
    }
}
