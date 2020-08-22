package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import org.joda.time.Duration;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.AlertSet;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FaultEventCode;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.LogEventErrorCode;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.util.OmnipodConst;

public class PodInfoFaultEvent extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 21;

    private final PodProgressStatus podProgressStatus;
    private final DeliveryStatus deliveryStatus;
    private final double insulinNotDelivered;
    private final byte podMessageCounter;
    private final double totalInsulinDelivered;
    private final FaultEventCode faultEventCode;
    private final Duration faultEventTime;
    private final Double reservoirLevel;
    private final Duration timeSinceActivation;
    private final AlertSet unacknowledgedAlerts;
    private final boolean faultAccessingTables;
    private final LogEventErrorCode logEventErrorType;
    private final PodProgressStatus logEventErrorPodProgressStatus;
    private final byte receiverLowGain;
    private final byte radioRSSI;
    private final PodProgressStatus podProgressStatusAtTimeOfFirstLoggedFaultEvent;
    private final byte[] unknownValue;

    public PodInfoFaultEvent(byte[] encodedData) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        podProgressStatus = PodProgressStatus.fromByte(encodedData[1]);
        deliveryStatus = DeliveryStatus.fromByte(encodedData[2]);
        insulinNotDelivered = OmnipodConst.POD_PULSE_SIZE * ByteUtil.toInt(encodedData[3], encodedData[4]);
        podMessageCounter = encodedData[5];
        totalInsulinDelivered = OmnipodConst.POD_PULSE_SIZE * ByteUtil.toInt(encodedData[6], encodedData[7]);
        faultEventCode = FaultEventCode.fromByte(encodedData[8]);

        int minutesSinceActivation = ByteUtil.toInt(encodedData[9], encodedData[10]);
        if (minutesSinceActivation == 0xffff) {
            faultEventTime = null;
        } else {
            faultEventTime = Duration.standardMinutes(minutesSinceActivation);
        }

        double reservoirValue = ((encodedData[11] & 0x03) << 8) +
                ByteUtil.convertUnsignedByteToInt(encodedData[12]) * OmnipodConst.POD_PULSE_SIZE;
        if (reservoirValue > OmnipodConst.MAX_RESERVOIR_READING) {
            reservoirLevel = null;
        } else {
            reservoirLevel = reservoirValue;
        }

        int minutesActive = ByteUtil.toInt(encodedData[13], encodedData[14]);
        timeSinceActivation = Duration.standardMinutes(minutesActive);

        unacknowledgedAlerts = new AlertSet(encodedData[15]);
        faultAccessingTables = encodedData[16] == 0x02;
        logEventErrorType = LogEventErrorCode.fromByte((byte) (encodedData[17] >>> 4));
        logEventErrorPodProgressStatus = PodProgressStatus.fromByte((byte) (encodedData[17] & 0x0f));
        receiverLowGain = (byte) (ByteUtil.convertUnsignedByteToInt(encodedData[18]) >>> 6);
        radioRSSI = (byte) (encodedData[18] & 0x3f);
        podProgressStatusAtTimeOfFirstLoggedFaultEvent = PodProgressStatus.fromByte((byte) (encodedData[19] & 0x0f));
        unknownValue = ByteUtil.substring(encodedData, 20, 2);
    }

    @Override
    public PodInfoType getType() {
        return PodInfoType.FAULT_EVENT;
    }

    public PodProgressStatus getPodProgressStatus() {
        return podProgressStatus;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public double getInsulinNotDelivered() {
        return insulinNotDelivered;
    }

    public byte getPodMessageCounter() {
        return podMessageCounter;
    }

    public double getTotalInsulinDelivered() {
        return totalInsulinDelivered;
    }

    public FaultEventCode getFaultEventCode() {
        return faultEventCode;
    }

    public Duration getFaultEventTime() {
        return faultEventTime;
    }

    public Double getReservoirLevel() {
        return reservoirLevel;
    }

    public Duration getTimeSinceActivation() {
        return timeSinceActivation;
    }

    public AlertSet getUnacknowledgedAlerts() {
        return unacknowledgedAlerts;
    }

    public boolean isFaultAccessingTables() {
        return faultAccessingTables;
    }

    public LogEventErrorCode getLogEventErrorType() {
        return logEventErrorType;
    }

    public PodProgressStatus getLogEventErrorPodProgressStatus() {
        return logEventErrorPodProgressStatus;
    }

    public byte getReceiverLowGain() {
        return receiverLowGain;
    }

    public byte getRadioRSSI() {
        return radioRSSI;
    }

    public PodProgressStatus getPodProgressStatusAtTimeOfFirstLoggedFaultEvent() {
        return podProgressStatusAtTimeOfFirstLoggedFaultEvent;
    }

    public byte[] getUnknownValue() {
        return unknownValue;
    }

    @Override
    public String toString() {
        return "PodInfoFaultEvent{" +
                "podProgressStatus=" + podProgressStatus +
                ", deliveryStatus=" + deliveryStatus +
                ", insulinNotDelivered=" + insulinNotDelivered +
                ", podMessageCounter=" + podMessageCounter +
                ", totalInsulinDelivered=" + totalInsulinDelivered +
                ", faultEventCode=" + faultEventCode +
                ", faultEventTime=" + faultEventTime +
                ", reservoirLevel=" + reservoirLevel +
                ", timeSinceActivation=" + timeSinceActivation +
                ", unacknowledgedAlerts=" + unacknowledgedAlerts +
                ", faultAccessingTables=" + faultAccessingTables +
                ", logEventErrorType=" + logEventErrorType +
                ", logEventErrorPodProgressStatus=" + logEventErrorPodProgressStatus +
                ", receiverLowGain=" + receiverLowGain +
                ", radioRSSI=" + radioRSSI +
                ", podProgressStatusAtTimeOfFirstLoggedFaultEvent=" + podProgressStatusAtTimeOfFirstLoggedFaultEvent +
                ", unknownValue=" + ByteUtil.shortHexString(unknownValue) +
                '}';
    }
}
