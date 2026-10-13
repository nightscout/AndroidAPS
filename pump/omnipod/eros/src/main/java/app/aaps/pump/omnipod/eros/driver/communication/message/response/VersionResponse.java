package app.aaps.pump.omnipod.eros.driver.communication.message.response;

import androidx.annotation.NonNull;

import app.aaps.core.utils.pump.ByteUtil;
import app.aaps.pump.omnipod.eros.driver.communication.message.MessageBlock;
import app.aaps.pump.omnipod.eros.driver.definition.FirmwareVersion;
import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;
import app.aaps.pump.omnipod.eros.driver.definition.PodProgressStatus;

public class VersionResponse extends MessageBlock {
    private static final int ASSIGN_ADDRESS_VERSION_RESPONSE_LENGTH = 0x15;
    private static final int SETUP_POD_VERSION_RESPONSE_LENGTH = 0x1b;

    private final PodProgressStatus podProgressStatus;
    @NonNull private final FirmwareVersion pmVersion;
    private final FirmwareVersion piVersion;
    private final int lot;
    private final int tid;
    private final int address;
    private Byte gain; // Only in the assign address version response
    private Byte rssi; // Only in the assign address version response

    public VersionResponse(@NonNull byte[] data) {
        int length = ByteUtil.INSTANCE.convertUnsignedByteToInt(data[1]);
        this.encodedData = ByteUtil.INSTANCE.substring(data, 2, length);

        switch (length) {
            case ASSIGN_ADDRESS_VERSION_RESPONSE_LENGTH:
                podProgressStatus = PodProgressStatus.fromByte(data[9]);
                pmVersion = new FirmwareVersion(data[2], data[3], data[4]);
                piVersion = new FirmwareVersion(data[5], data[6], data[7]);
                lot = ByteUtil.INSTANCE.toInt(data[10], (int) data[11],
                        (int) data[12], (int) data[13], ByteUtil.BitConversion.BIG_ENDIAN);
                tid = ByteUtil.INSTANCE.toInt(data[14], (int) data[15],
                        (int) data[16], (int) data[17], ByteUtil.BitConversion.BIG_ENDIAN);
                gain = (byte) ((data[18] & 0xc0) >>> 6);
                rssi = (byte) (data[18] & 0x3f);
                address = ByteUtil.INSTANCE.toInt(data[19], (int) data[20],
                        (int) data[21], (int) data[22], ByteUtil.BitConversion.BIG_ENDIAN);
                break;
            case SETUP_POD_VERSION_RESPONSE_LENGTH:
                podProgressStatus = PodProgressStatus.fromByte(data[16]);
                pmVersion = new FirmwareVersion(data[9], data[10], data[11]);
                piVersion = new FirmwareVersion(data[12], data[13], data[14]);
                lot = ByteUtil.INSTANCE.toInt(data[17], (int) data[18],
                        (int) data[19], (int) data[20], ByteUtil.BitConversion.BIG_ENDIAN);
                tid = ByteUtil.INSTANCE.toInt(data[21], (int) data[22],
                        (int) data[23], (int) data[24], ByteUtil.BitConversion.BIG_ENDIAN);

                address = ByteUtil.INSTANCE.toInt(data[25], (int) data[26],
                        (int) data[27], (int) data[28], ByteUtil.BitConversion.BIG_ENDIAN);
                break;
            default:
                throw new IllegalArgumentException("Unrecognized VersionResponse message length: " + length);
        }
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.VERSION_RESPONSE;
    }

    public PodProgressStatus getPodProgressStatus() {
        return podProgressStatus;
    }

    @NonNull public FirmwareVersion getPmVersion() {
        return pmVersion;
    }

    public FirmwareVersion getPiVersion() {
        return piVersion;
    }

    public int getLot() {
        return lot;
    }

    public int getTid() {
        return tid;
    }

    public Byte getGain() {
        return gain;
    }

    public Byte getRssi() {
        return rssi;
    }

    public boolean isAssignAddressVersionResponse() {
        return encodedData.length == ASSIGN_ADDRESS_VERSION_RESPONSE_LENGTH;
    }

    public boolean isSetupPodVersionResponse() {
        return encodedData.length == SETUP_POD_VERSION_RESPONSE_LENGTH;
    }

    public int getAddress() {
        return address;
    }

    @NonNull @Override
    public String toString() {
        return "VersionResponse{" +
                "podProgressStatus=" + podProgressStatus +
                ", pmVersion=" + pmVersion +
                ", piVersion=" + piVersion +
                ", lot=" + lot +
                ", tid=" + tid +
                ", address=" + address +
                '}';
    }
}
