package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.NonceResyncableMessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;

public class DeactivatePodCommand extends NonceResyncableMessageBlock {
    private int nonce;

    public DeactivatePodCommand(int nonce) {
        this.nonce = nonce;
        encode();
    }

    @NonNull @Override
    public MessageBlockType getType() {
        return MessageBlockType.DEACTIVATE_POD;
    }

    private void encode() {
        encodedData = ByteUtil.INSTANCE.getBytesFromInt(nonce);
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
