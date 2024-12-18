package app.aaps.pump.omnipod.eros.driver.communication.message.command;

import androidx.annotation.NonNull;

import java.util.Collections;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.NonceResyncableMessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSet;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSlot;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;

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

    @NonNull @Override
    public MessageBlockType getType() {
        return MessageBlockType.ACKNOWLEDGE_ALERT;
    }

    private void encode() {
        encodedData = ByteUtil.INSTANCE.getBytesFromInt(nonce);
        encodedData = ByteUtil.INSTANCE.concat(encodedData, alerts.getRawValue());
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

    @NonNull @Override
    public String toString() {
        return "AcknowledgeAlertsCommand{" +
                "alerts=" + alerts +
                ", nonce=" + nonce +
                '}';
    }
}
