package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertSlot;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;

public class PodInfoActiveAlerts extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 11;

    private final byte[] word278; // Unknown use
    private final List<AlertActivation> alertActivations;

    public PodInfoActiveAlerts(byte[] encodedData) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        word278 = ByteUtil.substring(encodedData, 1, 2);

        alertActivations = new ArrayList<>();

        for (AlertSlot alertSlot : AlertSlot.values()) {
            int valueHighBits = ByteUtil.convertUnsignedByteToInt(encodedData[3 + alertSlot.getValue() * 2]);
            int valueLowBits = ByteUtil.convertUnsignedByteToInt(encodedData[4 + alertSlot.getValue() * 2]);
            int value = (valueHighBits << 8) | valueLowBits;
            if (value != 0) {
                alertActivations.add(new AlertActivation(alertSlot, value));
            }
        }
    }

    @Override
    public PodInfoType getType() {
        return PodInfoType.ACTIVE_ALERTS;
    }

    public byte[] getWord278() {
        return word278;
    }

    public List<AlertActivation> getAlertActivations() {
        return new ArrayList<>(alertActivations);
    }

    @Override
    public String toString() {
        return "PodInfoActiveAlerts{" +
                "word278=" + ByteUtil.shortHexString(word278) +
                ", alertActivations=" + alertActivations +
                '}';
    }

    public static class AlertActivation {
        private final AlertSlot alertSlot;
        private final int value;

        private AlertActivation(AlertSlot alertSlot, int value) {
            this.alertSlot = alertSlot;
            this.value = value;
        }

        public double getValueAsUnits() {
            return value * OmnipodConstants.POD_PULSE_SIZE;
        }

        public Duration getValueAsDuration() {
            return Duration.standardMinutes(value);
        }

        public AlertSlot getAlertSlot() {
            return alertSlot;
        }

        @Override
        public String toString() {
            return "AlertActivation{" +
                    "alertSlot=" + alertSlot +
                    ", valueAsUnits=" + getValueAsUnits() +
                    ", valueAsDuration=" + getValueAsDuration() +
                    '}';
        }
    }
}
