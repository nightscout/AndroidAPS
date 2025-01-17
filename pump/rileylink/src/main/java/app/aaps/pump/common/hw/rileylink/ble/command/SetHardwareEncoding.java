package app.aaps.pump.common.hw.rileylink.ble.command;

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkEncodingType;

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
