package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public enum BeepType {
    SILENT((byte) 0x00),
    FOUR_TIMES_BIP_BEEP((byte) 0x02), // Used in low reservoir alert, user expiration alert, expiration alert, imminent expiration alert, lump of coal alert
    LONG_SINGLE_BEEP((byte) 0x06); // Used in stop delivery command

    private byte value;

    BeepType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
