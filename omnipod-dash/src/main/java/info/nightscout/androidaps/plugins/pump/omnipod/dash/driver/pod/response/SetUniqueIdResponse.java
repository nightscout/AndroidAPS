package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response;

import java.nio.ByteBuffer;
import java.util.Arrays;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.PodStatus;

public final class SetUniqueIdResponse extends ActivationResponseBase {
    private byte messageType;
    private short messageLength;
    private short pulseVolumeInTenThousandthMicroLiter;
    private short pumpRate;
    private short primePumpRate;
    private short numberOfEngagingClutchDrivePulses;
    private short numberOfPrimePulses;
    private short podExpirationTimeInHours;
    private short firmwareVersionMajor;
    private short firmwareVersionMinor;
    private short firmwareVersionInterim;
    private short bleVersionMajor;
    private short bleVersionMinor;
    private short bleVersionInterim;
    private short productId;
    private PodStatus podStatus;
    private long lotNumber;
    private long podSequenceNumber;
    private long uniqueIdReceivedInCommand;

    public SetUniqueIdResponse(byte[] encoded) {
        super(ResponseType.ActivationResponseType.SET_UNIQUE_ID_RESPONSE, encoded);

        messageType = encoded[0];
        messageLength = (short) (encoded[1] & 0xff);
        pulseVolumeInTenThousandthMicroLiter = ByteBuffer.wrap(new byte[]{encoded[2], encoded[3]}).getShort();
        pumpRate = (short) (encoded[4] & 0xff);
        primePumpRate = (short) (encoded[5] & 0xff);
        numberOfEngagingClutchDrivePulses = (short) (encoded[6] & 0xff);
        numberOfPrimePulses = (short) (encoded[7] & 0xff);
        podExpirationTimeInHours = (short) (encoded[8] & 0xff);
        firmwareVersionMajor = (short) (encoded[9] & 0xff);
        firmwareVersionMinor = (short) (encoded[10] & 0xff);
        firmwareVersionInterim = (short) (encoded[11] & 0xff);
        bleVersionMajor = (short) (encoded[12] & 0xff);
        bleVersionMinor = (short) (encoded[13] & 0xff);
        bleVersionInterim = (short) (encoded[14] & 0xff);
        productId = (short) (encoded[15] & 0xff);
        podStatus = PodStatus.byValue(encoded[16]);
        lotNumber = ByteBuffer.wrap(new byte[]{0, 0, 0, 0, encoded[17], encoded[18], encoded[19], encoded[20]}).getLong();
        podSequenceNumber = ByteBuffer.wrap(new byte[]{0, 0, 0, 0, encoded[21], encoded[22], encoded[23], encoded[24]}).getLong();
        uniqueIdReceivedInCommand = ByteBuffer.wrap(new byte[]{0, 0, 0, 0, encoded[25], encoded[26], encoded[27], encoded[28]}).getLong();
    }

    public byte getMessageType() {
        return messageType;
    }

    public short getMessageLength() {
        return messageLength;
    }

    public short getPulseVolumeInTenThousandthMicroLiter() {
        return pulseVolumeInTenThousandthMicroLiter;
    }

    public short getDeliveryRate() {
        return pumpRate;
    }

    public short getPrimeRate() {
        return primePumpRate;
    }

    public short getNumberOfEngagingClutchDrivePulses() {
        return numberOfEngagingClutchDrivePulses;
    }

    public short getNumberOfPrimePulses() {
        return numberOfPrimePulses;
    }

    public short getPodExpirationTimeInHours() {
        return podExpirationTimeInHours;
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

    public long getUniqueIdReceivedInCommand() {
        return uniqueIdReceivedInCommand;
    }

    @Override public String toString() {
        return "SetUniqueIdResponse{" +
                "messageType=" + messageType +
                ", messageLength=" + messageLength +
                ", pulseVolume=" + pulseVolumeInTenThousandthMicroLiter +
                ", pumpRate=" + pumpRate +
                ", primePumpRate=" + primePumpRate +
                ", numberOfEngagingClutchDrivePulses=" + numberOfEngagingClutchDrivePulses +
                ", numberOfPrimePulses=" + numberOfPrimePulses +
                ", podExpirationTimeInHours=" + podExpirationTimeInHours +
                ", softwareVersionMajor=" + firmwareVersionMajor +
                ", softwareVersionMinor=" + firmwareVersionMinor +
                ", softwareVersionInterim=" + firmwareVersionInterim +
                ", bleVersionMajor=" + bleVersionMajor +
                ", bleVersionMinor=" + bleVersionMinor +
                ", bleVersionInterim=" + bleVersionInterim +
                ", productId=" + productId +
                ", podStatus=" + podStatus +
                ", lotNumber=" + lotNumber +
                ", podSequenceNumber=" + podSequenceNumber +
                ", uniqueIdReceivedInCommand=" + uniqueIdReceivedInCommand +
                ", activationResponseType=" + activationResponseType +
                ", responseType=" + responseType +
                ", encoded=" + Arrays.toString(encoded) +
                '}';
    }
}
