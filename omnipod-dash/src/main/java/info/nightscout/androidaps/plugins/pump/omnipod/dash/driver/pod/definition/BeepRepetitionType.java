package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

// FIXME names
public enum BeepRepetitionType {
    XXX((byte) 0x01), // Used in low reservoir alert
    XXX2((byte) 0x03), // Used in user pod expiration alert
    XXX3((byte) 0x05), // Used in pod expiration alert
    XXX4((byte) 0x06), // Used in imminent pod expiration alert
    XXX5((byte) 0x08); // Used in lump of coal alert

    private byte value;

    BeepRepetitionType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
