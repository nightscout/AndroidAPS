package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;

public class GetVersionCommand extends CommandBase {
    private static final int DEFAULT_ADDRESS = -1;
    private static final short LENGTH = 6;
    private static final byte BODY_LENGTH = 4;

    private final int address;
    private final boolean unknown;

    public GetVersionCommand(short sequenceNumber, boolean unknown) {
        this(sequenceNumber, DEFAULT_ADDRESS, unknown);
    }

    public GetVersionCommand(short sequenceNumber, int address, boolean unknown) {
        super(CommandType.GET_VERSION, sequenceNumber);
        this.address = address;
        this.unknown = unknown;
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(12) //
                .put(encodeHeader(address, sequenceNumber, LENGTH, unknown)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(address) //
                .array());
    }
}
