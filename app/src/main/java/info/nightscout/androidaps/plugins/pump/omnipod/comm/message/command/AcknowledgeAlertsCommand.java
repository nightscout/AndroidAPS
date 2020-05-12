package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.command;

import java.util.Collections;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.NonceResyncableMessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;

public class AcknowledgeAlertsCommand extends NonceResyncableMessageBlock {

    private final AlertSet alerts;
    private int nonce;

    public AcknowledgeAlertsCommand(int nonce, AlertSet alerts) {
        this.nonce = nonce;
        this.alerts = alerts;
        encode();
    }

    public AcknowledgeAlertsCommand(int nonce, AlertSlot alertSlot) {
        this(nonce, new AlertSet(Collections.singletonList(alertSlot)));
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.ACKNOWLEDGE_ALERT;
    }

    private void encode() {
        encodedData = ByteUtil.getBytesFromInt(nonce);
        encodedData = ByteUtil.concat(encodedData, alerts.getRawValue());
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
        return "AcknowledgeAlertsCommand{" +
                "alerts=" + alerts +
                ", nonce=" + nonce +
                '}';
    }
}
