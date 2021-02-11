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
    final boolean unknown;

    CommandBase(CommandType commandType, int address, short sequenceNumber, boolean unknown) {
        this.commandType = commandType;
        this.address = address;
        this.sequenceNumber = sequenceNumber;
        this.unknown = unknown;
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

    public boolean isUnknown() {
        return unknown;
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

    static byte[] encodeHeader(int address, short sequenceNumber, short length, boolean unknown) {
        return ByteBuffer.allocate(6) //
                .putInt(address) //
                .putShort((short) (((sequenceNumber & 0x0f) << 10) | length | ((unknown ? 1 : 0) << 15))) //
                .array();
    }
}
