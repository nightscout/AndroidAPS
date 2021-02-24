package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.CharacteristicType;

public class CouldNotEnableNotifications extends Exception {
    public CouldNotEnableNotifications(CharacteristicType cmd) {
        super(cmd.getValue());
    }
}
