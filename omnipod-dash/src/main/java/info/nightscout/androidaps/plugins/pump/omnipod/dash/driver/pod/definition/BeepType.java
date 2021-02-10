package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

// FIXME names
public enum BeepType {
    SILENT((byte) 0x00),
    XXX((byte) 0x02); //// Used in low reservoir alert, user expiration alert, expiration alert, imminent expiration alert, lump of coal alert

    private byte value;

    BeepType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
