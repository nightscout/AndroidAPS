package info.nightscout.androidaps.plugins.pump.omnipod.comm.message.response.podinfo;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PodInfoType;

public abstract class PodInfo {
    private final byte[] encodedData;

    public PodInfo(byte[] encodedData) {
        this.encodedData = encodedData;
    }

    public abstract PodInfoType getType();

    public byte[] getEncodedData() {
        return encodedData;
    }
}
