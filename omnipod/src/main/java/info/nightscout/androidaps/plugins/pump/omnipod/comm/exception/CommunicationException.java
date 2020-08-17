package info.nightscout.androidaps.plugins.pump.omnipod.comm.exception;

import info.nightscout.androidaps.plugins.pump.omnipod.exception.OmnipodException;

public class CommunicationException extends OmnipodException {
    private final Type type;

    public CommunicationException(Type type) {
        super(type.getDescription(), false);
        this.type = type;
    }

    public CommunicationException(Type type, Throwable cause) {
        super(type.getDescription() + ": " + cause, cause, false);
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public enum Type {
        TIMEOUT("Communication timeout"),
        UNEXPECTED_EXCEPTION("Caught an unexpected Exception");

        private final String description;

        Type(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
