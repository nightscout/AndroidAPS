package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public class DeactivateCommand extends CommandBase {
    private static final short LENGTH = 6;
    private static final byte BODY_LENGTH = 4;

    DeactivateCommand(int address, short sequenceNumber, boolean unknown) {
        super(CommandType.DEACTIVATE, address, sequenceNumber, unknown);
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(address, sequenceNumber, LENGTH, unknown)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(1229869870) // FIXME ?? was: byte array of int 777211465 converted to little endian
                .array());
    }

    @Override @NonNull public String toString() {
        return "DeactivateCommand{" +
                "commandType=" + commandType +
                ", address=" + address +
                ", sequenceNumber=" + sequenceNumber +
                ", unknown=" + unknown +
                '}';
    }
}
