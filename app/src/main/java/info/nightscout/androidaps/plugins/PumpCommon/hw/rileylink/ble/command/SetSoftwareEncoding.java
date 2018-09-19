package info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.command;

import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkCommandType;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkEncodingType;
import info.nightscout.androidaps.plugins.PumpCommon.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;

public class SetSoftwareEncoding extends RileyLinkCommand {

    private final RileyLinkEncodingType encoding;


    public SetSoftwareEncoding(RileyLinkFirmwareVersion version, RileyLinkEncodingType encoding) {
        super(version);
        this.encoding = encoding;
    }


    @Override
    public RileyLinkCommandType getCommandType() {
        return RileyLinkCommandType.SetSWEncoding;
    }


    @Override
    public byte[] getRaw() {
        return getByteArray(getCommandType().code, encoding.value);
    }
}
