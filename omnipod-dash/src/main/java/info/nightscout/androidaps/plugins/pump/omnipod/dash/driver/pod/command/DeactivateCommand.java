package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;

public final class DeactivateCommand extends NonceEnabledCommand {
    private static final short LENGTH = 6;
    private static final byte BODY_LENGTH = 4;

    private DeactivateCommand(int address, short sequenceNumber, boolean multiCommandFlag, int nonce) {
        super(CommandType.DEACTIVATE, address, sequenceNumber, multiCommandFlag, nonce);
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(address, sequenceNumber, LENGTH, multiCommandFlag)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(nonce) //
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

    public static final class Builder extends NonceEnabledCommandBuilder<Builder, DeactivateCommand> {
        @Override protected final DeactivateCommand buildCommand() {
            return new DeactivateCommand(Builder.this.address, sequenceNumber, multiCommandFlag, nonce);
        }
    }
}
