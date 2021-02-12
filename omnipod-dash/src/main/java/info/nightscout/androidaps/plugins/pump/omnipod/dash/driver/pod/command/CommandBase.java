package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.CrcUtil;

abstract class CommandBase implements Command {
    static final short HEADER_LENGTH = 6;

    final CommandType commandType;
    final int address;
    final short sequenceNumber;
    final boolean multiCommandFlag;

    CommandBase(CommandType commandType, int address, short sequenceNumber, boolean multiCommandFlag) {
        this.commandType = commandType;
        this.address = address;
        this.sequenceNumber = sequenceNumber;
        this.multiCommandFlag = multiCommandFlag;
    }

    @Override public CommandType getCommandType() {
        return commandType;
    }

    public int getAddress() {
        return address;
    }

    public short getSequenceNumber() {
        return sequenceNumber;
    }

    public boolean isMultiCommandFlag() {
        return multiCommandFlag;
    }

    static byte[] formatCommand(byte[] command) {
        List<Byte> temp = new ArrayList<>();

        byte[] prefix = "S0.0=".getBytes(StandardCharsets.UTF_8);
        for (byte b : prefix) {
            temp.add(b);
        }

        byte[] length = ByteBuffer.allocate(2).putShort((short) command.length).array();
        for (int i = 0; i < 2; i++) {
            temp.add(length[i]);
        }

        // Append command
        for (byte b : command) {
            temp.add(b);
        }

        byte[] suffix = ",G0.0".getBytes(StandardCharsets.UTF_8);
        for (byte b : suffix) {
            temp.add(b);
        }

        byte[] out = new byte[((short) temp.size())];
        for (int i2 = 0; i2 < temp.size(); i2++) {
            out[i2] = temp.get(i2);
        }
        return out;
    }

    static byte[] appendCrc(byte[] command) {
        return ByteBuffer.allocate(command.length + 2) //
                .put(command) //
                .putShort(CrcUtil.createCrc(command)) //
                .array();
    }

    static byte[] encodeHeader(int address, short sequenceNumber, short length, boolean multiCommandFlag) {
        return ByteBuffer.allocate(6) //
                .putInt(address) //
                .putShort((short) (((sequenceNumber & 0x0f) << 10) | length | ((multiCommandFlag ? 1 : 0) << 15))) //
                .array();
    }

    static abstract class Builder<T extends Builder<T, R>, R extends Command> {
        Integer address;
        Short sequenceNumber;
        boolean multiCommandFlag = false;

        public final R build() {
            if (address == null) {
                throw new IllegalArgumentException("address can not be null");
            }
            if (sequenceNumber == null) {
                throw new IllegalArgumentException("sequenceNumber can not be null");
            }
            return buildCommand();
        }

        public final T setAddress(int address) {
            this.address = address;
            return (T) this;
        }

        public final T setSequenceNumber(short sequenceNumber) {
            this.sequenceNumber = sequenceNumber;
            return (T) this;
        }

        public final T setMultiCommandFlag(boolean multiCommandFlag) {
            this.multiCommandFlag = multiCommandFlag;
            return (T) this;
        }

        abstract R buildCommand();
    }
}
