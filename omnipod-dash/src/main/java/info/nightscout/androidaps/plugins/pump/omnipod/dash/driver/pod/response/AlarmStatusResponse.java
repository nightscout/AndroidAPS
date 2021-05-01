package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;
import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

public class AlarmStatusResponse extends AdditionalStatusResponseBase {
    private final byte messageType;
    private final short messageLength;
    private final byte additionalStatusResponseType;
    private final PodStatus podStatus;
    private final DeliveryStatus deliveryStatus;
    private final short bolusPulsesRemaining;
    private final short sequenceNumberOfLastProgrammingCommand;
    private final short totalPulsesDelivered;
    private final AlarmType alarmType;
    private final short alarmTime;
    private final short reservoirPulsesRemaining;
    private final short minutesSinceActivation;
    private final boolean alert0Active;
    private final boolean alert1Active;
    private final boolean alert2Active;
    private final boolean alert3Active;
    private final boolean alert4Active;
    private final boolean alert5Active;
    private final boolean alert6Active;
    private final boolean alert7Active;
    private final boolean occlusionAlarm;
    private final boolean pulseInfoInvalid;
    private final PodStatus podStatusWhenAlarmOccurred;
    private final boolean immediateBolusWhenAlarmOccurred;
    private final byte occlusionType;
    private final boolean occurredWhenFetchingImmediateBolusActiveInformation;
    private final short rssi;
    private final short receiverLowerGain;
    private final PodStatus podStatusWhenAlarmOccurred2;
    private final short returnAddressOfPodAlarmHandlerCaller;

    public AlarmStatusResponse(byte[] encoded) {
        super(ResponseType.AdditionalStatusResponseType.ALARM_STATUS, encoded);
        messageType = encoded[0];
        messageLength = (short) (encoded[1] & 0xff);
        additionalStatusResponseType = encoded[2];
        podStatus = PodStatus.byValue((byte) (encoded[3] & 0x0f));
        deliveryStatus = DeliveryStatus.byValue((byte) (encoded[4] & 0x0f));
        bolusPulsesRemaining = (short) (ByteBuffer.wrap(new byte[]{encoded[5], encoded[6]}).getShort() & 2047);
        sequenceNumberOfLastProgrammingCommand = (short) (encoded[7] & 0x0f);
        totalPulsesDelivered = ByteBuffer.wrap(new byte[]{encoded[8], encoded[9]}).getShort();
        alarmType = AlarmType.byValue(encoded[10]);
        alarmTime = ByteBuffer.wrap(new byte[]{encoded[11], encoded[12]}).getShort();
        reservoirPulsesRemaining = ByteBuffer.wrap(new byte[]{encoded[13], encoded[14]}).getShort();
        minutesSinceActivation = ByteBuffer.wrap(new byte[]{encoded[15], encoded[16]}).getShort();

        byte activeAlerts = encoded[17];
        alert0Active = (activeAlerts & 1) == 1;
        alert1Active = ((activeAlerts >>> 1) & 1) == 1;
        alert2Active = ((activeAlerts >>> 2) & 1) == 1;
        alert3Active = ((activeAlerts >>> 3) & 1) == 1;
        alert4Active = ((activeAlerts >>> 4) & 1) == 1;
        alert5Active = ((activeAlerts >>> 5) & 1) == 1;
        alert6Active = ((activeAlerts >>> 6) & 1) == 1;
        alert7Active = ((activeAlerts >>> 7) & 1) == 1;

        byte alarmFlags = encoded[18];
        occlusionAlarm = (alarmFlags & 1) == 1;
        pulseInfoInvalid = ((alarmFlags >> 1) & 1) == 1;

        byte byte19 = encoded[19];
        byte byte20 = encoded[20];
        podStatusWhenAlarmOccurred = PodStatus.byValue((byte) (byte19 & 0x0f));
        immediateBolusWhenAlarmOccurred = ((byte19 >> 4) & 1) == 1;
        occlusionType = (byte) ((byte19 >> 5) & 3);
        occurredWhenFetchingImmediateBolusActiveInformation = ((byte19 >> 7) & 1) == 1;
        rssi = (short) (byte20 & 0x3f);
        receiverLowerGain = (short) ((byte20 >> 6) & 0x03);
        podStatusWhenAlarmOccurred2 = PodStatus.byValue((byte) (encoded[21] & 0x0f));
        returnAddressOfPodAlarmHandlerCaller = ByteBuffer.wrap(new byte[]{encoded[22], encoded[23]}).getShort();
    }

    public byte getMessageType() {
        return messageType;
    }

    public short getMessageLength() {
        return messageLength;
    }

    public byte getAdditionalStatusResponseType() {
        return additionalStatusResponseType;
    }

    public PodStatus getPodStatus() {
        return podStatus;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public short getBolusPulsesRemaining() {
        return bolusPulsesRemaining;
    }

    public short getSequenceNumberOfLastProgrammingCommand() {
        return sequenceNumberOfLastProgrammingCommand;
    }

    public short getTotalPulsesDelivered() {
        return totalPulsesDelivered;
    }

    public AlarmType getAlarmType() {
        return alarmType;
    }

    public short getAlarmTime() {
        return alarmTime;
    }

    public short getReservoirPulsesRemaining() {
        return reservoirPulsesRemaining;
    }

    public short getMinutesSinceActivation() {
        return minutesSinceActivation;
    }

    public boolean isAlert0Active() {
        return alert0Active;
    }

    public boolean isAlert1Active() {
        return alert1Active;
    }

    public boolean isAlert2Active() {
        return alert2Active;
    }

    public boolean isAlert3Active() {
        return alert3Active;
    }

    public boolean isAlert4Active() {
        return alert4Active;
    }

    public boolean isAlert5Active() {
        return alert5Active;
    }

    public boolean isAlert6Active() {
        return alert6Active;
    }

    public boolean isAlert7Active() {
        return alert7Active;
    }

    public boolean isOcclusionAlarm() {
        return occlusionAlarm;
    }

    public boolean isPulseInfoInvalid() {
        return pulseInfoInvalid;
    }

    public PodStatus getPodStatusWhenAlarmOccurred() {
        return podStatusWhenAlarmOccurred;
    }

    public boolean isImmediateBolusWhenAlarmOccurred() {
        return immediateBolusWhenAlarmOccurred;
    }

    public byte getOcclusionType() {
        return occlusionType;
    }

    public boolean isOccurredWhenFetchingImmediateBolusActiveInformation() {
        return occurredWhenFetchingImmediateBolusActiveInformation;
    }

    public short getRssi() {
        return rssi;
    }

    public short getReceiverLowerGain() {
        return receiverLowerGain;
    }

    public PodStatus getPodStatusWhenAlarmOccurred2() {
        return podStatusWhenAlarmOccurred2;
    }

    public short getReturnAddressOfPodAlarmHandlerCaller() {
        return returnAddressOfPodAlarmHandlerCaller;
    }

    @NonNull @Override public String toString() {
        return "AlarmStatusResponse{" +
                "messageType=" + messageType +
                ", messageLength=" + messageLength +
                ", additionalStatusResponseType=" + additionalStatusResponseType +
                ", podStatus=" + podStatus +
                ", deliveryStatus=" + deliveryStatus +
                ", bolusPulsesRemaining=" + bolusPulsesRemaining +
                ", sequenceNumberOfLastProgrammingCommand=" + sequenceNumberOfLastProgrammingCommand +
                ", totalPulsesDelivered=" + totalPulsesDelivered +
                ", alarmType=" + alarmType +
                ", alarmTime=" + alarmTime +
                ", reservoirPulsesRemaining=" + reservoirPulsesRemaining +
                ", minutesSinceActivation=" + minutesSinceActivation +
                ", alert0Active=" + alert0Active +
                ", alert1Active=" + alert1Active +
                ", alert2Active=" + alert2Active +
                ", alert3Active=" + alert3Active +
                ", alert4Active=" + alert4Active +
                ", alert5Active=" + alert5Active +
                ", alert6Active=" + alert6Active +
                ", alert7Active=" + alert7Active +
                ", occlusionAlarm=" + occlusionAlarm +
                ", pulseInfoInvalid=" + pulseInfoInvalid +
                ", podStatusWhenAlarmOccurred=" + podStatusWhenAlarmOccurred +
                ", immediateBolusWhenAlarmOccurred=" + immediateBolusWhenAlarmOccurred +
                ", occlusionType=" + occlusionType +
                ", occurredWhenFetchingImmediateBolusActiveInformation=" + occurredWhenFetchingImmediateBolusActiveInformation +
                ", rssi=" + rssi +
                ", receiverLowerGain=" + receiverLowerGain +
                ", podStatusWhenAlarmOccurred2=" + podStatusWhenAlarmOccurred2 +
                ", returnAddressOfPodAlarmHandlerCaller=" + returnAddressOfPodAlarmHandlerCaller +
                ", statusResponseType=" + statusResponseType +
                ", responseType=" + responseType +
                ", encoded=" + Arrays.toString(encoded) +
                '}';
    }
}
