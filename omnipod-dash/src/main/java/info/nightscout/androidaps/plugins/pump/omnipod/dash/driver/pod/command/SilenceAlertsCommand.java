package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.BitSet;

public class SilenceAlertsCommand extends CommandBase {
    private static final short LENGTH = (short) 7;
    private static final byte BODY_LENGTH = (byte) 5;

    private final SilenceAlertCommandParameters parameters;

    public SilenceAlertsCommand(int address, short sequenceNumber, boolean unknown, SilenceAlertCommandParameters parameters) {
        super(CommandType.SILENCE_ALERTS, address, sequenceNumber, unknown);
        this.parameters = parameters;
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(address, sequenceNumber, LENGTH, unknown)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(1229869870) // FIXME ?? was: byte array of int 777211465 converted to little endian
                .put(parameters.getEncoded()) //
                .array());
    }

    public static final class SilenceAlertCommandParameters {
        private final boolean silenceAutoOffAlert;
        private final boolean silenceMultiCommandAlert;
        private final boolean silenceExpirationImminentAlert;
        private final boolean silenceUserSetExpirationAlert;
        private final boolean silenceLowReservoirAlert;
        private final boolean silenceSuspendInProgressAlert;
        private final boolean silenceSuspendEndedAlert;
        private final boolean silencePodExpirationAlert;

        public SilenceAlertCommandParameters(boolean silenceAutoOffAlert, boolean silenceMultiCommandAlert, boolean silenceExpirationImminentAlert, boolean silenceUserSetExpirationAlert, boolean silenceLowReservoirAlert, boolean silenceSuspendInProgressAlert, boolean silenceSuspendEndedAlert, boolean silencePodExpirationAlert) {
            this.silenceAutoOffAlert = silenceAutoOffAlert;
            this.silenceMultiCommandAlert = silenceMultiCommandAlert;
            this.silenceExpirationImminentAlert = silenceExpirationImminentAlert;
            this.silenceUserSetExpirationAlert = silenceUserSetExpirationAlert;
            this.silenceLowReservoirAlert = silenceLowReservoirAlert;
            this.silenceSuspendInProgressAlert = silenceSuspendInProgressAlert;
            this.silenceSuspendEndedAlert = silenceSuspendEndedAlert;
            this.silencePodExpirationAlert = silencePodExpirationAlert;
        }

        public byte[] getEncoded() {
            BitSet bitSet = new BitSet(8);
            bitSet.set(0, this.silenceAutoOffAlert);
            bitSet.set(1, this.silenceMultiCommandAlert);
            bitSet.set(2, this.silenceExpirationImminentAlert);
            bitSet.set(3, this.silenceUserSetExpirationAlert);
            bitSet.set(4, this.silenceLowReservoirAlert);
            bitSet.set(5, this.silenceSuspendInProgressAlert);
            bitSet.set(6, this.silenceSuspendEndedAlert);
            bitSet.set(7, this.silencePodExpirationAlert);
            return bitSet.toByteArray();
        }
    }
}
