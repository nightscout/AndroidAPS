package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.CC111XRegister;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;

public class UpdateRegister extends RileyLinkCommand {

    CC111XRegister register;
    byte registerValue;


    public UpdateRegister(CC111XRegister register, byte registerValue) {
        super();
        this.register = register;
        this.registerValue = registerValue;
    }


    @Override
    public RileyLinkCommandType getCommandType() {
        return RileyLinkCommandType.UpdateRegister;
    }


    @Override
    public byte[] getRaw() {
        return getByteArray(getCommandType().code, register.value, registerValue);
    }
}
