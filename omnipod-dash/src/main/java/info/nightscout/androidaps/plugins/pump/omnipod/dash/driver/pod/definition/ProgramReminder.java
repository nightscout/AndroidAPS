package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public class ProgramReminder {
    private final boolean atStart;
    private final boolean atEnd;
    private final byte atInterval;

    public ProgramReminder(boolean atStart, boolean atEnd, byte atIntervalInMinutes) {
        this.atStart = atStart;
        this.atEnd = atEnd;
        this.atInterval = atIntervalInMinutes;
    }

    public byte getEncoded() {
        return (byte) (((this.atStart ? 0 : 1) << 7)
                | ((this.atEnd ? 0 : 1) << 6)
                | (this.atInterval & 0x3f));
    }
}
