package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.command;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.CC111XRegister;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkCommandType;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;

public class UpdateRegister extends RileyLinkCommand {

    CC111XRegister register;
    byte registerValue;


    public UpdateRegister(RileyLinkFirmwareVersion version, CC111XRegister register, byte registerValue) {
        super(version);
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
