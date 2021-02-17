package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

public class CurrentSlot {
    private byte index;
    private short secondsRemaining;
    private short pulsesRemaining;

    public CurrentSlot(byte index, short secondsRemaining, short pulsesRemaining) {
        this.index = index;
        this.secondsRemaining = secondsRemaining;
        this.pulsesRemaining = pulsesRemaining;
    }

    public byte getIndex() {
        return index;
    }

    public short getSecondsRemaining() {
        return secondsRemaining;
    }

    public short getPulsesRemaining() {
        return pulsesRemaining;
    }

    @Override public String toString() {
        return "CurrentSlot{" +
                "index=" + index +
                ", secondsRemaining=" + secondsRemaining +
                ", pulsesRemaining=" + pulsesRemaining +
                '}';
    }
}
