package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

public enum RFSpyRLResponse {
    // 0xaa == timeout
    // 0xbb == interrupted
    // 0xcc == zero-data
    // 0xdd == success
    // 0x11 == invalidParam
    // 0x22 == unknownCommand

    Invalid(0), // default, just fail
    Timeout(0xAA),
    Interrupted(0xBB),
    ZeroData(0xCC),
    Success(0xDD),
    OldSuccess(0x01),
    InvalidParam(0x11),
    UnknownCommand(0x22), ;

    byte value;


    RFSpyRLResponse(int value) {
        this.value = (byte)value;
    }


    public static RFSpyRLResponse fromByte(byte input) {
        for (RFSpyRLResponse type : values()) {
            if (type.value == input) {
                return type;
            }
        }
        return null;
    }

}
