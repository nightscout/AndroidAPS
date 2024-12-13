package app.aaps.pump.omnipod.eros.driver.communication.message.response;

import androidx.annotation.NonNull;

import org.joda.time.Duration;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.AlertSet;
import app.aaps.pump.omnipod.eros.driver.definition.DeliveryStatus;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;
import app.aaps.pump.omnipod.eros.driver.definition.OmnipodConstants;
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;

public class StatusResponse extends MessageBlock implements StatusUpdatableResponse {
    private static final int MESSAGE_LENGTH = 10;

    private final DeliveryStatus deliveryStatus;
    private final PodProgressStatus podProgressStatus;
    private final Duration timeActive;
    private final Double reservoirLevel;
    private final int ticksDelivered;
    private final double insulinDelivered;
    private final double bolusNotDelivered;
    private final byte podMessageCounter;
    private final AlertSet unacknowledgedAlerts;

    public StatusResponse(byte[] encodedData) {
        if (encodedData.length < MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }
        this.encodedData = ByteUtil.INSTANCE.substring(encodedData, 1, MESSAGE_LENGTH - 1);

        deliveryStatus = DeliveryStatus.fromByte((byte) (ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[1]) >>> 4));
        podProgressStatus = PodProgressStatus.fromByte((byte) (encodedData[1] & 0x0F));

        int minutes = ((encodedData[7] & 0x7F) << 6) | ((encodedData[8] & 0xFC) >>> 2);
        timeActive = Duration.standardMinutes(minutes);

        int highInsulinBits = (encodedData[2] & 0xF) << 9;
        int middleInsulinBits = ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[3]) << 1;
        int lowInsulinBits = ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[4]) >>> 7;
        ticksDelivered = (highInsulinBits | middleInsulinBits | lowInsulinBits);
        insulinDelivered = OmnipodConstants.POD_PULSE_SIZE * ticksDelivered;
        podMessageCounter = (byte) ((encodedData[4] >>> 3) & 0xf);

        bolusNotDelivered = OmnipodConstants.POD_PULSE_SIZE * (((encodedData[4] & 0x03) << 8) | ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[5]));
        unacknowledgedAlerts = new AlertSet((byte) (((encodedData[6] & 0x7f) << 1) | (ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[7]) >>> 7)));

        double reservoirValue = (((encodedData[8] & 0x3) << 8) + ByteUtil.INSTANCE.convertUnsignedByteToInt(encodedData[9])) * OmnipodConstants.POD_PULSE_SIZE;
        if (reservoirValue > OmnipodConstants.MAX_RESERVOIR_READING) {
            reservoirLevel = null;
        } else {
            reservoirLevel = reservoirValue;
        }
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.STATUS_RESPONSE;
    }

    @Override public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    @Override public PodProgressStatus getPodProgressStatus() {
        return podProgressStatus;
    }

    @Override public Duration getTimeActive() {
        return timeActive;
    }

    @Override public Double getReservoirLevel() {
        return reservoirLevel;
    }

    @Override public int getTicksDelivered() {
        return ticksDelivered;
    }

    @Override public double getInsulinDelivered() {
        return insulinDelivered;
    }

    @Override public double getBolusNotDelivered() {
        return bolusNotDelivered;
    }

    @Override public byte getPodMessageCounter() {
        return podMessageCounter;
    }

    @Override public AlertSet getUnacknowledgedAlerts() {
        return unacknowledgedAlerts;
    }

    @Override public byte[] getRawData() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            stream.write(getType().getValue());
            stream.write(encodedData);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
        return stream.toByteArray();
    }

    @NonNull @Override public String toString() {
        return "StatusResponse{" +
                "deliveryStatus=" + deliveryStatus +
                ", podProgressStatus=" + podProgressStatus +
                ", timeActive=" + timeActive +
                ", reservoirLevel=" + reservoirLevel +
                ", ticksDelivered=" + ticksDelivered +
                ", insulinDelivered=" + insulinDelivered +
                ", bolusNotDelivered=" + bolusNotDelivered +
                ", podMessageCounter=" + podMessageCounter +
                ", unacknowledgedAlerts=" + unacknowledgedAlerts +
                ", encodedData=" + ByteUtil.INSTANCE.shortHexStringWithoutSpaces(encodedData) +
                '}';
    }
}
