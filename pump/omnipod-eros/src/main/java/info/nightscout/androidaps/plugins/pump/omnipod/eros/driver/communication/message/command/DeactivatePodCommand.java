package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.communication.message.NonceResyncableMessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.MessageBlockType;
import info.nightscout.pump.core.utils.ByteUtil;

public class DeactivatePodCommand extends NonceResyncableMessageBlock {
    private int nonce;

    public DeactivatePodCommand(int nonce) {
        this.nonce = nonce;
        encode();
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.DEACTIVATE_POD;
    }

    private void encode() {
        encodedData = ByteUtil.getBytesFromInt(nonce);
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
        return "DeactivatePodCommand{" +
                "nonce=" + nonce +
                '}';
    }
}
