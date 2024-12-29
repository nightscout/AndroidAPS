package app.aaps.pump.common.hw.rileylink.ble.command;

import org.apache.commons.lang3.NotImplementedException;

import java.nio.ByteBuffer;

import javax.inject.Inject;

import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkCommandType;
import app.aaps.pump.common.hw.rileylink.ble.defs.RileyLinkFirmwareVersion;
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData;
import dagger.android.HasAndroidInjector;

public class SetPreamble extends RileyLinkCommand {

    @Inject RileyLinkServiceData rileyLinkServiceData;

    private final int preamble;

    public SetPreamble(HasAndroidInjector injector, int preamble) throws Exception {
        super();

        injector.androidInjector().inject(this);

        // this command was not supported before 2.0
        if (!rileyLinkServiceData.firmwareVersion.isSameVersion(RileyLinkFirmwareVersion.Version2AndHigher)) {
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
