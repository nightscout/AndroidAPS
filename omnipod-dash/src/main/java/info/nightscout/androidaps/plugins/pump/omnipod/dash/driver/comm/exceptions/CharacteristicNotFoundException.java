package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

public class CharacteristicNotFoundException extends FailedToConnectException {
    public CharacteristicNotFoundException(String cmdCharacteristicUuid) {
        super("characteristic not found: " + cmdCharacteristicUuid);
    }
}
