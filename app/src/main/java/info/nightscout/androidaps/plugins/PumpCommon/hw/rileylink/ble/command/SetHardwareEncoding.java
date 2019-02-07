package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.command;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkCommandType;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkEncodingType;

public class SetHardwareEncoding extends RileyLinkCommand {

    private final RileyLinkEncodingType encoding;


    public SetHardwareEncoding(RileyLinkEncodingType encoding) {
        super();
        this.encoding = encoding;
    }


    @Override
    public RileyLinkCommandType getCommandType() {
        return RileyLinkCommandType.SetHardwareEncoding;
    }


    @Override
    public byte[] getRaw() {
        return getByteArray(getCommandType().code, encoding.value);
    }
}
