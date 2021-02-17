package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition;

public class ProgramReminder implements Encodable {
    private final boolean atStart;
    private final boolean atEnd;
    private final byte atInterval;

    public ProgramReminder(boolean atStart, boolean atEnd, byte atIntervalInMinutes) {
        this.atStart = atStart;
        this.atEnd = atEnd;
        this.atInterval = atIntervalInMinutes;
    }

    @Override public byte[] getEncoded() {
        return new byte[]{(byte) (((this.atStart ? 1 : 0) << 7)
                | ((this.atEnd ? 1 : 0) << 6)
                | (this.atInterval & 0x3f))};
    }
}
