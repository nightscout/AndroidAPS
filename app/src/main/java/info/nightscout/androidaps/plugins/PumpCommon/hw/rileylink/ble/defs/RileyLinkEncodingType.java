package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs;

public enum RileyLinkEncodingType {
    None(0x00), //
    Manchester(0x01), //
    FourByteSixByte(0x02), //
    ;

    public byte value;


    RileyLinkEncodingType(int value) {
        this.value = (byte)value;
    }
}
