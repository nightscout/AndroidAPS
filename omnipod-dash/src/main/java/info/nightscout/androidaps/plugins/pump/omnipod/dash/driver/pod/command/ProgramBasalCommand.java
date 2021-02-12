package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.ProgramReminder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.program.InsulinProgramElement;

public final class ProgramBasalCommand extends CommandBase {
    private final List<InsulinProgramElement> insulinProgramElements;

    private ProgramBasalCommand(int address, short sequenceNumber, boolean multiCommandFlag, ProgramReminder programReminder, List<InsulinProgramElement> insulinProgramElements) {
        super(CommandType.PROGRAM_BASAL, address, sequenceNumber, multiCommandFlag);
        this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
        this.programReminder = programReminder;
    }

    private final ProgramReminder programReminder;

    @Override public byte[] getEncoded() {
        return new byte[0];
    }

    public static final class Builder extends CommandBase.Builder<Builder, ProgramBasalCommand> {
        private List<InsulinProgramElement> insulinProgramElements;
        private ProgramReminder programReminder;

        public Builder setInsulinProgramElements(List<InsulinProgramElement> insulinProgramElements) {
            if (insulinProgramElements == null) {
                throw new IllegalArgumentException("insulinProgramElements can not be null");
            }
            this.insulinProgramElements = new ArrayList<>(insulinProgramElements);
            return this;
        }

        public Builder setProgramReminder(ProgramReminder programReminder) {
            this.programReminder = programReminder;
            return this;
        }

        @Override final ProgramBasalCommand buildCommand() {
            if (insulinProgramElements == null) {
                throw new IllegalArgumentException("insulinProgramElements can not be null");
            }
            if (programReminder == null) {
                throw new IllegalArgumentException("programReminder can not be null");
            }
            return new ProgramBasalCommand(address, sequenceNumber, multiCommandFlag, programReminder, insulinProgramElements);
        }
    }

}
