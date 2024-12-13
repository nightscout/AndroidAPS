package app.aaps.pump.common.hw.rileylink.ble.command;

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;

public abstract class RileyLinkCommand {

    public RileyLinkCommand() {
    }


    public abstract RileyLinkCommandType getCommandType();


    public abstract byte[] getRaw();


    protected byte[] getRawSimple() {
        return getByteArray(getCommandType().code);

    }


    protected byte[] getByteArray(byte... input) {
        return input;
    }

}
