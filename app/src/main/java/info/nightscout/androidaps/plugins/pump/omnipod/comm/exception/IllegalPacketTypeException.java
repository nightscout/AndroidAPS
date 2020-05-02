package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.PacketType;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class IllegalPacketTypeException extends OmnipodException {
    private final PacketType expected;
    private final PacketType actual;

    public IllegalPacketTypeException(PacketType expected, PacketType actual) {
        super(String.format(Locale.getDefault(), "Illegal packet type: %s, expected %s",
                actual, expected), false);
        this.expected = expected;
        this.actual = actual;
    }

    public PacketType getExpected() {
        return expected;
    }

    public PacketType getActual() {
        return actual;
    }

}
