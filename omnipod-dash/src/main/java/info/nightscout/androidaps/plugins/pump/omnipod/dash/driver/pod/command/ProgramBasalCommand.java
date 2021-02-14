package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.program.InsulinProgramElement;

// Always preceded by 0x1a ProgramInsulinCommand
public final class ProgramBasalCommand implements Command {
    private final List<InsulinProgramElement> uniqueInsulinProgramElements;

    private ProgramBasalCommand(List<InsulinProgramElement> uniqueInsulinProgramElements) {
        this.uniqueInsulinProgramElements = new ArrayList<>(uniqueInsulinProgramElements);
    }

    public short getLength() {
        return (short) (uniqueInsulinProgramElements.size() * 2 + 14);
    }

    public byte getBodyLength() {
        return (byte) (uniqueInsulinProgramElements.size() * 2 + 12);
    }

    @Override public byte[] getEncoded() {
        return ByteBuffer.allocate(getLength()) //
                // TODO
                .array();
    }

    @Override public CommandType getCommandType() {
        return CommandType.PROGRAM_BASAL;
    }

    // TODO builder
}
