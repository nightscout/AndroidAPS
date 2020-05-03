package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import java.util.Locale;

import info.nightscout.androidaps.plugins.pump.omnipod.defs.MessageBlockType;
import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class IllegalResponseException extends OmnipodException {
    private final String actualClass;
    private final MessageBlockType expectedType;

    public IllegalResponseException(String actualClass, MessageBlockType expectedType) {
        super(String.format(Locale.getDefault(), "Illegal response type: got class of type %s " +
                "for message block type %s", actualClass, expectedType), false);
        this.actualClass = actualClass;
        this.expectedType = expectedType;
    }

    public String getActualClass() {
        return actualClass;
    }

    public MessageBlockType getExpectedType() {
        return expectedType;
    }
}
