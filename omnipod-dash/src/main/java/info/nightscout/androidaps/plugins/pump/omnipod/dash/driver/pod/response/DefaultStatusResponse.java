package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.DeliveryStatus;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

public class DefaultStatusResponse extends ResponseBase {
    private final byte messageType;
    private final DeliveryStatus deliveryStatus;
    private final PodStatus podStatus;
    private final short totalPulsesDelivered;
    private final short sequenceNumberOfLastProgrammingCommand;
    private final short bolusPulsesRemaining;
    private final boolean occlusionAlertActive;
    private final boolean alert1Active;
    private final boolean alert2Active;
    private final boolean alert3Active;
    private final boolean alert4Active;
    private final boolean alert5Active;
    private final boolean alert6Active;
    private final boolean alert7Active;
    private final short minutesSinceActivation;
    private final short reservoirPulsesRemaining;

    public DefaultStatusResponse(byte[] encoded) {
        super(ResponseType.DEFAULT_STATUS_RESPONSE, encoded);

        messageType = encoded[0];
        deliveryStatus = DeliveryStatus.byValue((byte) ((encoded[1] >> 4) & 0x0f));
        podStatus = PodStatus.byValue((byte) (encoded[1] & 0x0f));
        totalPulsesDelivered = (short) (((encoded[2] & 0x0f) << 12) | ((encoded[3] & 0xff) << 1) | ((encoded[4] & 0xff) >>> 7));
        sequenceNumberOfLastProgrammingCommand = (byte) ((encoded[4] >>> 3) & 0x0f);
        bolusPulsesRemaining = (short) ((((encoded[4] & 0x07) << 10) | (encoded[5] & 0xff)) & 2047);

        short activeAlerts = (short) (((encoded[6] & 0xff) << 1) | (encoded[7] >>> 7));
        occlusionAlertActive = (activeAlerts & 1) == 1;
        alert1Active = ((activeAlerts >> 1) & 1) == 1;
        alert2Active = ((activeAlerts >> 2) & 1) == 1;
        alert3Active = ((activeAlerts >> 3) & 1) == 1;
        alert4Active = ((activeAlerts >> 4) & 1) == 1;
        alert5Active = ((activeAlerts >> 5) & 1) == 1;
        alert6Active = ((activeAlerts >> 6) & 1) == 1;
        alert7Active = ((activeAlerts >> 7) & 1) == 1;

        minutesSinceActivation = (short) (((encoded[7] & 0x7f) << 6) | (((encoded[8] & 0xff) >>> 2) & 0x3f));
        reservoirPulsesRemaining = (short) (((encoded[8] << 8) | encoded[9]) & 0x3ff);
    }

    public byte getMessageType() {
        return messageType;
    }

    public DeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }

    public PodStatus getPodStatus() {
        return podStatus;
    }

    public short getTotalPulsesDelivered() {
        return totalPulsesDelivered;
    }

    public short getSequenceNumberOfLastProgrammingCommand() {
        return sequenceNumberOfLastProgrammingCommand;
    }

    public short getBolusPulsesRemaining() {
        return bolusPulsesRemaining;
    }

    public boolean isOcclusionAlertActive() {
        return occlusionAlertActive;
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

    public short getMinutesSinceActivation() {
        return minutesSinceActivation;
    }

    public short getReservoirPulsesRemaining() {
        return reservoirPulsesRemaining;
    }

    @Override public String toString() {
        return "DefaultStatusResponse{" +
                "messageType=" + messageType +
                ", deliveryStatus=" + deliveryStatus +
                ", podStatus=" + podStatus +
                ", totalPulsesDelivered=" + totalPulsesDelivered +
                ", sequenceNumberOfLastProgrammingCommand=" + sequenceNumberOfLastProgrammingCommand +
                ", bolusPulsesRemaining=" + bolusPulsesRemaining +
                ", occlusionAlertActive=" + occlusionAlertActive +
                ", alert1Active=" + alert1Active +
                ", alert2Active=" + alert2Active +
                ", alert3Active=" + alert3Active +
                ", alert4Active=" + alert4Active +
                ", alert5Active=" + alert5Active +
                ", alert6Active=" + alert6Active +
                ", alert7Active=" + alert7Active +
                ", minutesSinceActivation=" + minutesSinceActivation +
                ", reservoirPulsesRemaining=" + reservoirPulsesRemaining +
                ", responseType=" + responseType +
                ", encoded=" + Arrays.toString(encoded) +
                '}';
    }
}
