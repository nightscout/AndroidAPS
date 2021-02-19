package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public class GetVersionCommand extends CommandBase {
    private static final int DEFAULT_ADDRESS = -1;
    private static final short LENGTH = 6;
    private static final byte BODY_LENGTH = 4;

    public GetVersionCommand(short sequenceNumber, boolean unknown) {
        this(DEFAULT_ADDRESS, sequenceNumber, unknown);
    }

    public GetVersionCommand(int address, short sequenceNumber, boolean unknown) {
        super(CommandType.GET_VERSION, address, sequenceNumber, unknown);
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(address, sequenceNumber, LENGTH, unknown)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(address) //
                .array());
    }

    @Override @NonNull public String toString() {
        return "GetVersionCommand{" +
                "commandType=" + commandType +
                ", address=" + address +
                ", sequenceNumber=" + sequenceNumber +
                ", unknown=" + unknown +
                '}';
    }
}
