package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;

public final class DeactivateCommand extends CommandBase {
    private static final short LENGTH = 6;
    private static final byte BODY_LENGTH = 4;

    private DeactivateCommand(int address, short sequenceNumber, boolean multiCommandFlag) {
        super(CommandType.DEACTIVATE, address, sequenceNumber, multiCommandFlag);
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(address, sequenceNumber, LENGTH, multiCommandFlag)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(1229869870) // FIXME ?? was: byte array of int 777211465 converted to little endian
                .array());
    }

    @Override public String toString() {
        return "DeactivateCommand{" +
                "commandType=" + commandType +
                ", address=" + address +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    public static final class Builder extends CommandBase.Builder<Builder, DeactivateCommand> {
        @Override final DeactivateCommand buildCommand() {
            return new DeactivateCommand(address, sequenceNumber, multiCommandFlag);
        }
    }
}
