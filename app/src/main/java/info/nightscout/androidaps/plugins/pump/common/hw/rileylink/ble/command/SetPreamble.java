package info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.command;

import java.nio.ByteBuffer;

import org.apache.commons.lang3.NotImplementedException;

import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;

public class SetPreamble extends RileyLinkCommand {

    private int preamble;


    public SetPreamble(int preamble) throws Exception {
        super();

        // this command was not supported before 2.0
        if (!RileyLinkUtil.getFirmwareVersion().isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher)) {
            throw new NotImplementedException("Old firmware does not support SetPreamble command");
        }

        if (preamble < 0 || preamble > 0xFFFF) {
            throw new Exception("preamble value is out of range");
        }
        this.preamble = preamble;
    }


    @Override
    public RileyLinkCommandType getCommandType() {
        return RileyLinkCommandType.SetPreamble;
    }


    @Override
    public byte[] getRaw() {
        byte[] bytes = ByteBuffer.allocate(4).putInt(preamble).array();
        return getByteArray(this.getCommandType().code, bytes[2], bytes[3]);
    }
}
