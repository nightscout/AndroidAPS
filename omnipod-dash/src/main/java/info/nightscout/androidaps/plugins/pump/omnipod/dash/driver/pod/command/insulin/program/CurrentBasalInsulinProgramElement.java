package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

public class CurrentBasalInsulinProgramElement {
    private final byte index;
    private final int delayUntilNextTenthPulseInUsec;
    private final short remainingTenthPulses;

    public CurrentBasalInsulinProgramElement(byte index, int delayUntilNextTenthPulseInUsec, short remainingTenthPulses) {
        this.index = index;
        this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec;
        this.remainingTenthPulses = remainingTenthPulses;
    }

    public byte getIndex() {
        return index;
    }

    public int getDelayUntilNextTenthPulseInUsec() {
        return delayUntilNextTenthPulseInUsec;
    }

    public short getRemainingTenthPulses() {
        return remainingTenthPulses;
    }

    @Override public String toString() {
        return "CurrentLongInsulinProgramElement{" +
                "index=" + index +
                ", delayUntilNextTenthPulseInUsec=" + delayUntilNextTenthPulseInUsec +
                ", remainingTenthPulses=" + remainingTenthPulses +
                '}';
    }
}
