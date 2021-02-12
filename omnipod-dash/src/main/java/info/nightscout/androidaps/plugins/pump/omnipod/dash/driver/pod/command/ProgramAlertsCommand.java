package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration;

public final class ProgramAlertsCommand extends CommandBase {
    private final List<AlertConfiguration> alertConfigurations;

    private ProgramAlertsCommand(int address, short sequenceNumber, boolean multiCommandFlag, List<AlertConfiguration> alertConfigurations) {
        super(CommandType.PROGRAM_ALERTS, address, sequenceNumber, multiCommandFlag);
        this.alertConfigurations = new ArrayList<>(alertConfigurations);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(getLength() + HEADER_LENGTH) //
                .put(encodeHeader(address, sequenceNumber, getLength(), multiCommandFlag)) //
                .put(commandType.getValue()) //
                .put(getBodyLength()) //
                .putInt(1229869870); // FIXME ?? was: byte array of int 777211465 converted to little endian
        for (AlertConfiguration configuration : alertConfigurations) {
            byteBuffer.put(configuration.getEncoded());
        }
        return appendCrc(byteBuffer.array());
    }

    private short getLength() {
        return (short) (alertConfigurations.size() * 6 + 6);
    }

    private byte getBodyLength() {
        return (byte) (alertConfigurations.size() * 6 + 4);
    }

    @Override public String toString() {
        return "ProgramAlertsCommand{" +
                "alertConfigurations=" + alertConfigurations +
                ", commandType=" + commandType +
                ", address=" + address +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    public static final class Builder extends CommandBase.Builder<Builder, ProgramAlertsCommand> {
        private List<AlertConfiguration> alertConfigurations;

        public Builder setAlertConfigurations(List<AlertConfiguration> alertConfigurations) {
            this.alertConfigurations = alertConfigurations;
            return this;
        }

        @Override final ProgramAlertsCommand buildCommand() {
            if (this.alertConfigurations == null) {
                throw new IllegalArgumentException("alertConfigurations can not be null");
            }
            return new ProgramAlertsCommand(address, sequenceNumber, multiCommandFlag, alertConfigurations);
        }
    }
}
