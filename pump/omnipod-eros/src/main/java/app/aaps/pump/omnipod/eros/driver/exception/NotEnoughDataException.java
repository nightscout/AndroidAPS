package app.aaps.pump.omnipod.eros.driver.exception;

import app.aaps.core.utils.pump.ByteUtil;

public class NotEnoughDataException extends OmnipodException {
    private final byte[] data;

    public NotEnoughDataException(byte[] data) {
        super("Not enough data: " + ByteUtil.INSTANCE.shortHexString(data), false);
        this.data = data;
    }

    public byte[] getData() {
        return data;
    }
}
