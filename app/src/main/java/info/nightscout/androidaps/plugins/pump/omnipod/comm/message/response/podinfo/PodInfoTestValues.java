package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;

public class PodInfoTestValues extends PodInfo {
    private static final int MINIMUM_MESSAGE_LENGTH = 5;
    private final byte byte1;
    private final byte byte2;
    private final byte byte3;
    private final byte byte4;

    public PodInfoTestValues(byte[] encodedData) {
        super(encodedData);

        if (encodedData.length < MINIMUM_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Not enough data");
        }

        byte1 = encodedData[1];
        byte2 = encodedData[2];
        byte3 = encodedData[3];
        byte4 = encodedData[4];
    }

    @Override
    public PodInfoType getType() {
        return PodInfoType.HARDCODED_TEST_VALUES;
    }

    public byte getByte1() {
        return byte1;
    }

    public byte getByte2() {
        return byte2;
    }

    public byte getByte3() {
        return byte3;
    }

    public byte getByte4() {
        return byte4;
    }

    @Override
    public String toString() {
        return "PodInfoTestValues{" +
                "byte1=" + byte1 +
                ", byte2=" + byte2 +
                ", byte3=" + byte3 +
                ", byte4=" + byte4 +
                '}';
    }
}
