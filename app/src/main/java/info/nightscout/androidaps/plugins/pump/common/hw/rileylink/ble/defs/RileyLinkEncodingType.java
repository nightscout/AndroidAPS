package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs;

public enum RileyLinkEncodingType {

    None(0x00), // No encoding on RL
    Manchester(0x01), // Manchester encoding on RL (for Omnipod)
    FourByteSixByteRileyLink(0x02), // 4b6b encoding on RL (for Medtronic)
    FourByteSixByteLocal(0x00), // No encoding on RL, but 4b6b encoding in code
    ;

    public byte value;


    RileyLinkEncodingType(int value) {
        this.value = (byte)value;
    }
}
