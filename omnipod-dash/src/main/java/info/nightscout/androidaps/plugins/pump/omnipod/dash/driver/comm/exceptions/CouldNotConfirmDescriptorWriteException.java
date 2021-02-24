package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

public class CouldNotConfirmDescriptorWriteException extends Exception{
    private final String received;
    private final String expected;

    public CouldNotConfirmDescriptorWriteException(String received, String expected) {
        super();
        this.received = received;
        this.expected = expected;
    }
}
