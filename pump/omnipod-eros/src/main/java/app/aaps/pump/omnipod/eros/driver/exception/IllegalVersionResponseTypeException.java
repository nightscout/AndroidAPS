package app.aaps.pump.omnipod.eros.driver.exception;

public class IllegalVersionResponseTypeException extends OmnipodException {
    public IllegalVersionResponseTypeException(String expected, String actual) {
        super("Invalid Version Response type. Expected=" + expected + ", actual=" + actual, false);
    }
}
