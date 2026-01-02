package app.aaps.pump.omnipod.eros.driver.communication.message.response.podinfo;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.util.ArrayList;
import java.util.List;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSlot;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.definition.PodInfoType;

public class PodInfoActiveAlerts extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 11;

    private final byte[] word278; // Unknown use
    @NonNull private final List<AlertActivation> alertActivations;

    public PodInfoActiveAlerts(byte[] encodedData) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        word278 = ByteUtil.INSTANCE.substring(encodedData, 1, 2);

        alertActivations = new ArrayList<>();

        for (AlertSlot alertSlot : AlertSlot.values()) {
            int valueHighBits = ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[3 + alertSlot.getValue() * 2]);
            int valueLowBits = ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[4 + alertSlot.getValue() * 2]);
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

    @NonNull @Override
    public String toString() {
        return "PodInfoActiveAlerts{" +
                "word278=" + ByteUtil.INSTANCE.shortHexString(word278) +
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

        @NonNull @Override
        public String toString() {
            return "AlertActivation{" +
                    "alertSlot=" + alertSlot +
                    ", valueAsUnits=" + getValueAsUnits() +
                    ", valueAsDuration=" + getValueAsDuration() +
                    '}';
        }
    }
}
