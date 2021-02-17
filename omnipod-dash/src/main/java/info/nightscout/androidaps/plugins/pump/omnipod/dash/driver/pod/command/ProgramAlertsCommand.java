package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration;

public final class ProgramAlertsCommand extends NonceEnabledCommand {
    private final List<AlertConfiguration> alertConfigurations;

    ProgramAlertsCommand(int uniqueId, short sequenceNumber, boolean multiCommandFlag, List<AlertConfiguration> alertConfigurations, int nonce) {
        super(CommandType.PROGRAM_ALERTS, uniqueId, sequenceNumber, multiCommandFlag, nonce);
        this.alertConfigurations = new ArrayList<>(alertConfigurations);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(getLength() + HEADER_LENGTH) //
                .put(encodeHeader(uniqueId, sequenceNumber, getLength(), multiCommandFlag)) //
                .put(commandType.getValue()) //
                .put(getBodyLength()) //
                .putInt(nonce);
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
                ", nonce=" + nonce +
                ", commandType=" + commandType +
                ", uniqueId=" + uniqueId +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    public static final class Builder extends NonceEnabledCommandBuilder<Builder, ProgramAlertsCommand> {
        private List<AlertConfiguration> alertConfigurations;

        public Builder setAlertConfigurations(List<AlertConfiguration> alertConfigurations) {
            this.alertConfigurations = alertConfigurations;
            return this;
        }

        @Override protected final ProgramAlertsCommand buildCommand() {
            if (this.alertConfigurations == null) {
                throw new IllegalArgumentException("alertConfigurations can not be null");
            }
            return new ProgramAlertsCommand(uniqueId, sequenceNumber, multiCommandFlag, alertConfigurations, nonce);
        }
    }
}
