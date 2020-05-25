package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.common.utils.ByteUtil;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

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
