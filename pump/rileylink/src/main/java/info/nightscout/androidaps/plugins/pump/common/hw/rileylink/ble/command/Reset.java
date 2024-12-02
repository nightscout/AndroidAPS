package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command;

import androidx.annotation.NonNull;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;

public class Reset extends RileyLinkCommand {

    public Reset() {
        super();
    }


    @NonNull @Override
    public RileyLinkCommandType getCommandType() {
        return RileyLinkCommandType.Reset;
    }


    @Override
    public byte[] getRaw() {
        return super.getRawSimple();
    }
}
