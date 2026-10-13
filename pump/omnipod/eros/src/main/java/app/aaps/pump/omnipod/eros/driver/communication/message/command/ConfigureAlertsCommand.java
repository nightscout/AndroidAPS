package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import java.util.List;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.NonceResyncableMessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.AlertConfiguration;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;

public class ConfigureAlertsCommand extends NonceResyncableMessageBlock {
    private final List<AlertConfiguration> configurations;
    private int nonce;

    public ConfigureAlertsCommand(int nonce, List<AlertConfiguration> configurations) {
        this.nonce = nonce;
        this.configurations = configurations;
        encode();
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.CONFIGURE_ALERTS;
    }

    private void encode() {
        encodedData = ByteUtil.INSTANCE.getBytesFromInt(nonce);
        for (AlertConfiguration config : configurations) {
            encodedData = ByteUtil.INSTANCE.concat(encodedData, config.getRawData());
        }
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
        return "ConfigureAlertsCommand{" +
                "configurations=" + configurations +
                ", nonce=" + nonce +
                '}';
    }
}
