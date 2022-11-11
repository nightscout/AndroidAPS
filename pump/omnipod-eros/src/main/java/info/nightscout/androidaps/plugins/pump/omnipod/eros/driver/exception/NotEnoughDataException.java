package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception;

import info.nightscout.pump.core.utils.ByteUtil;

public class NotEnoughDataException extends OmnipodException {
    private final byte[] data;

    public NotEnoughDataException(byte[] data) {
        super("Not enough data: " + ByteUtil.shortHexString(data), false);
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
}
