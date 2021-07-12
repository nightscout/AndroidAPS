package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;

public final class DeactivateCommand extends NonceEnabledCommand {
    private static final short LENGTH = 6;
    private static final byte BODY_LENGTH = 4;

    DeactivateCommand(int uniqueId, short sequenceNumber, boolean multiCommandFlag, int nonce) {
        super(CommandType.DEACTIVATE, uniqueId, sequenceNumber, multiCommandFlag, nonce);
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(nonce) //
                .array());
    }

    @Override @NonNull public String toString() {
        return "DeactivateCommand{" +
                "nonce=" + nonce +
                ", commandType=" + commandType +
                ", uniqueId=" + uniqueId +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    public static final class Builder extends NonceEnabledCommandBuilder<Builder, DeactivateCommand> {
        @Override protected final DeactivateCommand buildCommand() {
            return new DeactivateCommand(uniqueId, sequenceNumber, multiCommandFlag, nonce);
        }
    }
}
