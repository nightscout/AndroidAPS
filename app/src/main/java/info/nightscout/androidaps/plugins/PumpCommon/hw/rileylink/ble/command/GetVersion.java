package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.command;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkCommandType;

public class GetVersion extends RileyLinkCommand {

    public GetVersion() {
        super();
    }


    @Override
    public RileyLinkCommandType getCommandType() {
        return RileyLinkCommandType.GetVersion;
    }


    @Override
    public byte[] getRaw() {
        return super.getRawSimple();
    }
}
