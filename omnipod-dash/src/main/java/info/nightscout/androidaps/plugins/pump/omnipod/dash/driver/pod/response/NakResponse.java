package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlarmType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.NakErrorType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

public class NakResponse extends ResponseBase {
    private final byte messageType;
    private final short messageLength;
    private final NakErrorType nakErrorType;
    private final AlarmType alarmType;
    private final PodStatus podStatus;
    private final short securityNakSyncCount;

    public NakResponse(byte[] encoded) {
        super(ResponseType.NAK_RESPONSE, encoded);
        this.messageType = encoded[0];
        this.messageLength = encoded[1];
        this.nakErrorType = NakErrorType.byValue(encoded[2]);
        byte byte3 = encoded[3];
        byte byte4 = encoded[4];
        if (nakErrorType == NakErrorType.ILLEGAL_SECURITY_CODE) {
            this.securityNakSyncCount = (short) ((byte3 << 8) | byte4);
            this.alarmType = null;
            this.podStatus = null;
        } else {
            this.securityNakSyncCount = 0;
            this.alarmType = AlarmType.byValue(byte3);
            this.podStatus = PodStatus.byValue(byte4);
        }
    }

    public byte getMessageType() {
        return messageType;
    }

    public short getMessageLength() {
        return messageLength;
    }

    public NakErrorType getNakErrorType() {
        return nakErrorType;
    }

    public AlarmType getAlarmType() {
        return alarmType;
    }

    public PodStatus getPodStatus() {
        return podStatus;
    }

    public short getSecurityNakSyncCount() {
        return securityNakSyncCount;
    }

    @Override public String toString() {
        return "NakResponse{" +
                "messageType=" + messageType +
                ", messageLength=" + messageLength +
                ", nakErrorType=" + nakErrorType +
                ", alarmType=" + alarmType +
                ", podStatus=" + podStatus +
                ", securityNakSyncCount=" + securityNakSyncCount +
                ", responseType=" + responseType +
                ", encoded=" + Arrays.toString(encoded) +
                '}';
    }
}
