package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.insulin.program;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;

public class LongInsulinProgramElement implements Encodable {
    private final short totalTenthPulses;
    private final int delayBetweenTenthPulses;

    public LongInsulinProgramElement(byte totalTenthPulses, short delayBetweenTenthPulses) {
        this.totalTenthPulses = totalTenthPulses;
        this.delayBetweenTenthPulses = delayBetweenTenthPulses;
    }

    @Override public byte[] getEncoded() {
        return ByteBuffer.allocate(6) //
                .putShort(totalTenthPulses) //
                .putInt(delayBetweenTenthPulses) //
                .array();
    }

    @Override public String toString() {
        return "LongInsulinProgramElement{" +
                "totalTenthPulses=" + totalTenthPulses +
                ", delayBetweenTenthPulses=" + delayBetweenTenthPulses +
                '}';
    }
}
