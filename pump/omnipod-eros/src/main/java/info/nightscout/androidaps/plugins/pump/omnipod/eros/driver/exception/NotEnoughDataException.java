package info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.exception;

import info.nightscout.pump.common.utils.ByteUtil;

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
