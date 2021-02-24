package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.command;

import java.nio.ByteBuffer;

public class BleCommandHello extends BleCommand {
    public BleCommandHello(int controllerId) {
        super(BleCommandType.HELLO,
                ByteBuffer.allocate(6)
                        .put((byte) 1) // TODO find the meaning of this constant
                        .put((byte) 4) // TODO find the meaning of this constant
                        .putInt(controllerId).array()
        );
    }
}
