package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command;

import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.NonceResyncableMessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertConfiguration;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;

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
        encodedData = ByteUtil.getBytesFromInt(nonce);
        for (AlertConfiguration config : configurations) {
            encodedData = ByteUtil.concat(encodedData, config.getRawData());
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

    @Override
    public String toString() {
        return "ConfigureAlertsCommand{" +
                "configurations=" + configurations +
                ", nonce=" + nonce +
                '}';
    }
}
