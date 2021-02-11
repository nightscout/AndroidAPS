package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import java.nio.ByteBuffer;
import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

public final class VersionResponse extends ActivationResponseBase {

    private final byte messageType;
    private final short messageLength;
    private final short firmwareVersionMajor;
    private final short firmwareVersionMinor;
    private final short firmwareVersionInterim;
    private final short bleVersionMajor;
    private final short bleVersionMinor;
    private final short bleVersionInterim;
    private final short productId;
    private final PodStatus podStatus;
    private final long lotNumber;
    private final long podSequenceNumber;
    private final byte rssi;
    private final byte receiverLowerGain;
    private final long uniqueIdReceivedInCommand;

    public VersionResponse(byte[] encoded) {
        super(ResponseType.ActivationResponseType.GET_VERSION_RESPONSE, encoded);

        messageType = encoded[0];
        messageLength = (short) (encoded[1] & 0xff);
        firmwareVersionMajor = (short) (encoded[2] & 0xff);
        firmwareVersionMinor = (short) (encoded[3] & 0xff);
        firmwareVersionInterim = (short) (encoded[4] & 0xff);
        bleVersionMajor = (short) (encoded[5] & 0xff);
        bleVersionMinor = (short) (encoded[6] & 0xff);
        bleVersionInterim = (short) (encoded[7] & 0xff);
        productId = (short) (encoded[8] & 0xff);
        podStatus = PodStatus.byValue((byte) (encoded[9] & 0xf));
        lotNumber = ByteBuffer.wrap(new byte[]{0, 0, 0, 0, encoded[10], encoded[11], encoded[12], encoded[13]}).getLong();
        podSequenceNumber = ByteBuffer.wrap(new byte[]{0, 0, 0, 0, encoded[14], encoded[15], encoded[16], encoded[17]}).getLong();
        rssi = (byte) (encoded[18] & 0x3f);
        receiverLowerGain = (byte) ((encoded[18] >> 6) & 0x03);
        uniqueIdReceivedInCommand = ByteBuffer.wrap(new byte[]{0, 0, 0, 0, encoded[19], encoded[20], encoded[21], encoded[22]}).getLong();
    }

    public byte getMessageType() {
        return messageType;
    }

    public short getMessageLength() {
        return messageLength;
    }

    public short getFirmwareVersionMajor() {
        return firmwareVersionMajor;
    }

    public short getFirmwareVersionMinor() {
        return firmwareVersionMinor;
    }

    public short getFirmwareVersionInterim() {
        return firmwareVersionInterim;
    }

    public short getBleVersionMajor() {
        return bleVersionMajor;
    }

    public short getBleVersionMinor() {
        return bleVersionMinor;
    }

    public short getBleVersionInterim() {
        return bleVersionInterim;
    }

    public short getProductId() {
        return productId;
    }

    public PodStatus getPodStatus() {
        return podStatus;
    }

    public long getLotNumber() {
        return lotNumber;
    }

    public long getPodSequenceNumber() {
        return podSequenceNumber;
    }

    public byte getRssi() {
        return rssi;
    }

    public byte getReceiverLowerGain() {
        return receiverLowerGain;
    }

    public long getUniqueIdReceivedInCommand() {
        return uniqueIdReceivedInCommand;
    }

    @Override public String toString() {
        return "VersionResponse{" +
                "messageType=" + messageType +
                ", messageLength=" + messageLength +
                ", firmwareVersionMajor=" + firmwareVersionMajor +
                ", firmwareVersionMinor=" + firmwareVersionMinor +
                ", firmwareVersionInterim=" + firmwareVersionInterim +
                ", bleVersionMajor=" + bleVersionMajor +
                ", bleVersionMinor=" + bleVersionMinor +
                ", bleVersionInterim=" + bleVersionInterim +
                ", productId=" + productId +
                ", podStatus=" + podStatus +
                ", lotNumber=" + lotNumber +
                ", podSequenceNumber=" + podSequenceNumber +
                ", rssi=" + rssi +
                ", receiverLowerGain=" + receiverLowerGain +
                ", uniqueIdReceivedInCommand=" + uniqueIdReceivedInCommand +
                ", activationResponseType=" + activationResponseType +
                ", responseType=" + responseType +
                ", encoded=" + Arrays.toString(encoded) +
                '}';
    }
}
