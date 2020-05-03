package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.comm.message.MessageBlock;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.FirmwareVersion;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodProgressStatus;

public class VersionResponse extends MessageBlock {
    private final PodProgressStatus podProgressStatus;
    private final FirmwareVersion pmVersion;
    private final FirmwareVersion piVersion;
    private final int lot;
    private final int tid;
    private final int address;

    public VersionResponse(byte[] encodedData) {
        int length = ByteUtil.convertUnsignedByteToInt(encodedData[1]) + 2;
        this.encodedData = ByteUtil.substring(encodedData, 2, length - 2);

        boolean extraByte;
        byte[] truncatedData;

        switch (length) {
            case 0x17:
                truncatedData = ByteUtil.substring(encodedData, 2);
                extraByte = true;
                break;
            case 0x1D:
                truncatedData = ByteUtil.substring(encodedData, 9);
                extraByte = false;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized VersionResponse message length: " + length);
        }

        this.podProgressStatus = PodProgressStatus.fromByte(truncatedData[7]);
        this.pmVersion = new FirmwareVersion(truncatedData[0], truncatedData[1], truncatedData[2]);
        this.piVersion = new FirmwareVersion(truncatedData[3], truncatedData[4], truncatedData[5]);
        this.lot = ByteUtil.toInt((int) truncatedData[8], (int) truncatedData[9],
                (int) truncatedData[10], (int) truncatedData[11], ByteUtil.BitConversion.BIG_ENDIAN);
        this.tid = ByteUtil.toInt((int) truncatedData[12], (int) truncatedData[13],
                (int) truncatedData[14], (int) truncatedData[15], ByteUtil.BitConversion.BIG_ENDIAN);

        int indexIncrementor = extraByte ? 1 : 0;

        this.address = ByteUtil.toInt((int) truncatedData[16 + indexIncrementor], (int) truncatedData[17 + indexIncrementor],
                (int) truncatedData[18 + indexIncrementor], (int) truncatedData[19 + indexIncrementor], ByteUtil.BitConversion.BIG_ENDIAN);
    }

    @Override
    public MessageBlockType getType() {
        return MessageBlockType.VERSION_RESPONSE;
    }

    public PodProgressStatus getPodProgressStatus() {
        return podProgressStatus;
    }

    public FirmwareVersion getPmVersion() {
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

    public int getAddress() {
        return address;
    }

    @Override
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
