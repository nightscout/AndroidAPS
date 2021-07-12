package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base;

import java.nio.ByteBuffer;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.MessageUtil;

public abstract class HeaderEnabledCommand implements Command {
    protected static final short HEADER_LENGTH = 6;

    protected final CommandType commandType;
    protected final int uniqueId;
    protected final short sequenceNumber;
    protected final boolean multiCommandFlag;

    protected HeaderEnabledCommand(CommandType commandType, int uniqueId, short sequenceNumber, boolean multiCommandFlag) {
        this.commandType = commandType;
        this.uniqueId = uniqueId;
        this.sequenceNumber = sequenceNumber;
        this.multiCommandFlag = multiCommandFlag;
    }

    @Override public CommandType getCommandType() {
        return commandType;
    }

    protected static byte[] appendCrc(byte[] command) {
        return ByteBuffer.allocate(command.length + 2) //
                .put(command) //
                .putShort(MessageUtil.createCrc(command)) //
                .array();
    }

    protected static byte[] encodeHeader(int uniqueId, short sequenceNumber, short length, boolean multiCommandFlag) {
        return ByteBuffer.allocate(6) //
                .putInt(uniqueId) //
                .putShort((short) (((sequenceNumber & 0x0f) << 10) | length | ((multiCommandFlag ? 1 : 0) << 15))) //
                .array();
    }

}
