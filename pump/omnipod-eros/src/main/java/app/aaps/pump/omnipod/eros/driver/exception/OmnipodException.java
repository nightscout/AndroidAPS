package app.aaps.pump.omnipod.eros.driver.exception;

public abstract class OmnipodException extends RuntimeException {
    private boolean certainFailure;

    OmnipodException(String message, boolean certainFailure) {
        super(message);
        this.certainFailure = certainFailure;
    }

    OmnipodException(String message, Throwable cause, boolean certainFailure) {
        super(message, cause);
        this.certainFailure = certainFailure;
    }

    public boolean isCertainFailure() {
        return certainFailure;
    }

    public void setCertainFailure(boolean certainFailure) {
        this.certainFailure = certainFailure;
    }
}
