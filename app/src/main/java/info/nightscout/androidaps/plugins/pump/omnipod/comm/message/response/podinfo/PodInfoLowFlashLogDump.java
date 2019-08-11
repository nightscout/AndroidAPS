package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;

public class PodInfoLowFlashLogDump extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 8;

    private final byte numberOfBytes;
    private final byte[] dataFromFlashMemory;
    private final int podAddress;

    public PodInfoLowFlashLogDump(byte[] encodedData) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        numberOfBytes = encodedData[2];
        podAddress = ByteUtil.toInt((int) encodedData[3], (int) encodedData[4], (int) encodedData[5], (int) encodedData[6], ByteUtil.BitConversion.BIG_ENDIAN);
        dataFromFlashMemory = ByteUtil.substring(encodedData, 3, ByteUtil.convertUnsignedByteToInt(encodedData[2]));
    }

    @Override
    public PodInfoType getType() {
        return PodInfoType.LOW_FLASH_DUMP_LOG;
    }

    public byte getNumberOfBytes() {
        return numberOfBytes;
    }

    public byte[] getDataFromFlashMemory() {
        return dataFromFlashMemory;
    }

    public int getPodAddress() {
        return podAddress;
    }

    @Override
    public String toString() {
        return "PodInfoLowFlashLogDump{" +
                "numberOfBytes=" + numberOfBytes +
                ", dataFromFlashMemory=" + ByteUtil.shortHexString(dataFromFlashMemory) +
                ", podAddress=" + podAddress +
                '}';
    }
}
