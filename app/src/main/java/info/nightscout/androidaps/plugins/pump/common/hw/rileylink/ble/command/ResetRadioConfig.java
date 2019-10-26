package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;

public class ResetRadioConfig extends RileyLinkCommand {

    public ResetRadioConfig() {
        super();
    }


    @Override
    public RileyLinkCommandType getCommandType() {
        return RileyLinkCommandType.ResetRadioConfig;
    }


    @Override
    public byte[] getRaw() {
        return super.getRawSimple();
    }
}
