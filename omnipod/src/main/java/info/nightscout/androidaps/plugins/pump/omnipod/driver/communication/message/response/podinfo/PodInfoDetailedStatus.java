package info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.podinfo;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.communication.message.response.StatusUpdatableResponse;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.ErrorEventInfo;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.OmnipodConstants;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.driver.definition.PodProgressStatus;

public class PodInfoDetailedStatus extends PodInfo implements StatusUpdatableResponse {
    private static final int MINIMUM_MESSAGE_LENGTH = 21;

    private final PodProgressStatus podProgressStatus;
    private final DeliveryStatus deliveryStatus;
    private final double bolusNotDelivered;
    private final byte podMessageCounter;
    private final int ticksDelivered;
    private final double insulinDelivered;
    private final FaultEventCode faultEventCode;
    private final Duration faultEventTime;
    private final Double reservoirLevel;
    private final Duration timeActive;
    private final AlertSet unacknowledgedAlerts;
    private final boolean faultAccessingTables;
    private final ErrorEventInfo errorEventInfo;
    private final byte receiverLowGain;
    private final byte radioRSSI;
    private final PodProgressStatus previousPodProgressStatus;
    private final byte[] unknownValue;

    public PodInfoDetailedStatus(byte[] encodedData) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        podProgressStatus = PodProgressStatus.fromByte(encodedData[1]);
        deliveryStatus = DeliveryStatus.fromByte(encodedData[2]);
        bolusNotDelivered = OmnipodConstants.POD_PULSE_SIZE * ByteUtil.toInt(encodedData[3], encodedData[4]);
        podMessageCounter = encodedData[5];
        ticksDelivered = ByteUtil.toInt(encodedData[6], encodedData[7]);
        insulinDelivered = OmnipodConstants.POD_PULSE_SIZE * ticksDelivered;
        faultEventCode = FaultEventCode.fromByte(encodedData[8]);

        int minutesSinceActivation = ByteUtil.toInt(encodedData[9], encodedData[10]);
        if (minutesSinceActivation == 0xffff) {
            faultEventTime = null;
        } else {
            faultEventTime = Duration.standardMinutes(minutesSinceActivation);
        }

        double reservoirValue = ((encodedData[11] & 0x03) << 8) +
                ByteUtil.convertUnsignedByteToInt(encodedData[12]) * OmnipodConstants.POD_PULSE_SIZE;
        if (reservoirValue > OmnipodConstants.MAX_RESERVOIR_READING) {
            reservoirLevel = null;
        } else {
            reservoirLevel = reservoirValue;
        }

        int minutesActive = ByteUtil.toInt(encodedData[13], encodedData[14]);
        timeActive = Duration.standardMinutes(minutesActive);

        unacknowledgedAlerts = new AlertSet(encodedData[15]);
        faultAccessingTables = encodedData[16] == 0x02;
        byte rawErrorEventInfo = encodedData[17];
        if (rawErrorEventInfo == 0x00) {
            errorEventInfo = null;
        } else {
            errorEventInfo = ErrorEventInfo.fromByte(rawErrorEventInfo);
        }
        receiverLowGain = (byte) (ByteUtil.convertUnsignedByteToInt(encodedData[18]) >>> 6);
        radioRSSI = (byte) (encodedData[18] & 0x3f);
        if (ByteUtil.convertUnsignedByteToInt(encodedData[19]) == 0xff) { // this byte is not valid (no fault has occurred)
            previousPodProgressStatus = null;
        } else {
            previousPodProgressStatus = PodProgressStatus.fromByte((byte) (encodedData[19] & 0x0f));
        }
        unknownValue = ByteUtil.substring(encodedData, 20, 2);
    }

    @Override
    public PodInfoType getType() {
        return PodInfoType.DETAILED_STATUS;
    }

    public boolean isFaulted() {
        return faultEventCode != null;
    }

    public boolean isActivationTimeExceeded() {
        return podProgressStatus == PodProgressStatus.ACTIVATION_TIME_EXCEEDED;
    }

    @Override public PodProgressStatus getPodProgressStatus() {
        return podProgressStatus;
    }

    @Override public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    @Override public double getBolusNotDelivered() {
        return bolusNotDelivered;
    }

    @Override public byte getPodMessageCounter() {
        return podMessageCounter;
    }

    @Override public int getTicksDelivered() {
        return ticksDelivered;
    }

    @Override public double getInsulinDelivered() {
        return insulinDelivered;
    }

    public FaultEventCode getFaultEventCode() {
        return faultEventCode;
    }

    public Duration getFaultEventTime() {
        return faultEventTime;
    }

    @Override public Double getReservoirLevel() {
        return reservoirLevel;
    }

    @Override public Duration getTimeActive() {
        return timeActive;
    }

    @Override public AlertSet getUnacknowledgedAlerts() {
        return unacknowledgedAlerts;
    }

    public boolean isFaultAccessingTables() {
        return faultAccessingTables;
    }

    public ErrorEventInfo getErrorEventInfo() {
        return errorEventInfo;
    }

    public byte getReceiverLowGain() {
        return receiverLowGain;
    }

    public byte getRadioRSSI() {
        return radioRSSI;
    }

    public PodProgressStatus getPreviousPodProgressStatus() {
        return previousPodProgressStatus;
    }

    public byte[] getUnknownValue() {
        return unknownValue;
    }

    @Override public String toString() {
        return "PodInfoDetailedStatus{" +
                "podProgressStatus=" + podProgressStatus +
                ", deliveryStatus=" + deliveryStatus +
                ", bolusNotDelivered=" + bolusNotDelivered +
                ", podMessageCounter=" + podMessageCounter +
                ", ticksDelivered=" + ticksDelivered +
                ", insulinDelivered=" + insulinDelivered +
                ", faultEventCode=" + faultEventCode +
                ", faultEventTime=" + faultEventTime +
                ", reservoirLevel=" + reservoirLevel +
                ", timeActive=" + timeActive +
                ", unacknowledgedAlerts=" + unacknowledgedAlerts +
                ", faultAccessingTables=" + faultAccessingTables +
                ", errorEventInfo=" + errorEventInfo +
                ", receiverLowGain=" + receiverLowGain +
                ", radioRSSI=" + radioRSSI +
                ", previousPodProgressStatus=" + previousPodProgressStatus +
                ", unknownValue=" + ByteUtil.shortHexString(unknownValue) +
                '}';
    }
}
