package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

public class CurrentSlot {
    private final byte index;
    private final short eighthSecondsRemaining;
    private final short pulsesRemaining;

    public CurrentSlot(byte index, short eighthSecondsRemaining, short pulsesRemaining) {
        this.index = index;
        this.eighthSecondsRemaining = eighthSecondsRemaining;
        this.pulsesRemaining = pulsesRemaining;
    }

    public byte getIndex() {
        return index;
    }

    public short getEighthSecondsRemaining() {
        return eighthSecondsRemaining;
    }

    public short getPulsesRemaining() {
        return pulsesRemaining;
    }

    @Override public String toString() {
        return "CurrentSlot{" +
                "index=" + index +
                ", eighthSecondsRemaining=" + eighthSecondsRemaining +
                ", pulsesRemaining=" + pulsesRemaining +
                '}';
    }
}
