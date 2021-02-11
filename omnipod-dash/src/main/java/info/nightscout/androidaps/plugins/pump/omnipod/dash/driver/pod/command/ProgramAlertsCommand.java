package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertConfiguration;

public class ProgramAlertsCommand extends CommandBase {
    private final List<AlertConfiguration> alertConfigurations;

    ProgramAlertsCommand(int address, short sequenceNumber, boolean unknown, List<AlertConfiguration> alertConfigurations) {
        super(CommandType.PROGRAM_ALERTS, address, sequenceNumber, unknown);
        this.alertConfigurations = new ArrayList<>(alertConfigurations);
    }

    @Override public byte[] getEncoded() {
        ByteBuffer byteBuffer = ByteBuffer.allocate(getLength() + HEADER_LENGTH) //
                .put(encodeHeader(address, sequenceNumber, getLength(), unknown)) //
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
                ", unknown=" + unknown +
                '}';
    }
}
