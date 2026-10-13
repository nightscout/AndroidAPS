package app.aaps.pump.omnipod.eros.driver.exception;

import java.util.Locale;

import app.aaps.pump.omnipod.eros.driver.definition.MessageBlockType;

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
