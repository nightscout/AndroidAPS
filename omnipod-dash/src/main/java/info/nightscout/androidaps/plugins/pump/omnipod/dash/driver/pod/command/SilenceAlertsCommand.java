package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command;

import java.nio.ByteBuffer;
import java.util.BitSet;

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder;
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable;

public final class SilenceAlertsCommand extends NonceEnabledCommand {
    private static final short LENGTH = (short) 7;
    private static final byte BODY_LENGTH = (byte) 5;

    private final SilenceAlertCommandParameters parameters;

    SilenceAlertsCommand(int uniqueId, short sequenceNumber, boolean multiCommandFlag, SilenceAlertCommandParameters parameters, int nonce) {
        super(CommandType.SILENCE_ALERTS, uniqueId, sequenceNumber, multiCommandFlag, nonce);
        this.parameters = parameters;
    }

    @Override public byte[] getEncoded() {
        return appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag)) //
                .put(commandType.getValue()) //
                .put(BODY_LENGTH) //
                .putInt(nonce) //
                .put(parameters.getEncoded()) //
                .array());
    }

    @Override public String toString() {
        return "SilenceAlertsCommand{" +
                "parameters=" + parameters +
                ", nonce=" + nonce +
                ", commandType=" + commandType +
                ", uniqueId=" + uniqueId +
                ", sequenceNumber=" + sequenceNumber +
                ", multiCommandFlag=" + multiCommandFlag +
                '}';
    }

    private static final class SilenceAlertCommandParameters implements Encodable {
        private final boolean silenceAutoOffAlert;
        private final boolean silenceMultiCommandAlert;
        private final boolean silenceExpirationImminentAlert;
        private final boolean silenceUserSetExpirationAlert;
        private final boolean silenceLowReservoirAlert;
        private final boolean silenceSuspendInProgressAlert;
        private final boolean silenceSuspendEndedAlert;
        private final boolean silencePodExpirationAlert;

        private SilenceAlertCommandParameters(boolean silenceAutoOffAlert, boolean silenceMultiCommandAlert, boolean silenceExpirationImminentAlert, boolean silenceUserSetExpirationAlert, boolean silenceLowReservoirAlert, boolean silenceSuspendInProgressAlert, boolean silenceSuspendEndedAlert, boolean silencePodExpirationAlert) {
            this.silenceAutoOffAlert = silenceAutoOffAlert;
            this.silenceMultiCommandAlert = silenceMultiCommandAlert;
            this.silenceExpirationImminentAlert = silenceExpirationImminentAlert;
            this.silenceUserSetExpirationAlert = silenceUserSetExpirationAlert;
            this.silenceLowReservoirAlert = silenceLowReservoirAlert;
            this.silenceSuspendInProgressAlert = silenceSuspendInProgressAlert;
            this.silenceSuspendEndedAlert = silenceSuspendEndedAlert;
            this.silencePodExpirationAlert = silencePodExpirationAlert;
        }

        @Override
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

    public static class Builder extends NonceEnabledCommandBuilder<Builder, SilenceAlertsCommand> {
        private boolean silenceAutoOffAlert;
        private boolean silenceMultiCommandAlert;
        private boolean silenceExpirationImminentAlert;
        private boolean silenceUserSetExpirationAlert;
        private boolean silenceLowReservoirAlert;
        private boolean silenceSuspendInProgressAlert;
        private boolean silenceSuspendEndedAlert;
        private boolean silencePodExpirationAlert;

        public Builder setSilenceAutoOffAlert(boolean silenceAutoOffAlert) {
            this.silenceAutoOffAlert = silenceAutoOffAlert;
            return this;
        }

        public Builder setSilenceMultiCommandAlert(boolean silenceMultiCommandAlert) {
            this.silenceMultiCommandAlert = silenceMultiCommandAlert;
            return this;
        }

        public Builder setSilenceExpirationImminentAlert(boolean silenceExpirationImminentAlert) {
            this.silenceExpirationImminentAlert = silenceExpirationImminentAlert;
            return this;
        }

        public Builder setSilenceUserSetExpirationAlert(boolean silenceUserSetExpirationAlert) {
            this.silenceUserSetExpirationAlert = silenceUserSetExpirationAlert;
            return this;
        }

        public Builder setSilenceLowReservoirAlert(boolean silenceLowReservoirAlert) {
            this.silenceLowReservoirAlert = silenceLowReservoirAlert;
            return this;
        }

        public Builder setSilenceSuspendInProgressAlert(boolean silenceSuspendInProgressAlert) {
            this.silenceSuspendInProgressAlert = silenceSuspendInProgressAlert;
            return this;
        }

        public Builder setSilenceSuspendEndedAlert(boolean silenceSuspendEndedAlert) {
            this.silenceSuspendEndedAlert = silenceSuspendEndedAlert;
            return this;
        }

        public Builder setSilencePodExpirationAlert(boolean silencePodExpirationAlert) {
            this.silencePodExpirationAlert = silencePodExpirationAlert;
            return this;
        }

        @Override protected final SilenceAlertsCommand buildCommand() {
            return new SilenceAlertsCommand(uniqueId, sequenceNumber, multiCommandFlag, new SilenceAlertCommandParameters(silenceAutoOffAlert, silenceMultiCommandAlert, silenceExpirationImminentAlert, silenceUserSetExpirationAlert, silenceLowReservoirAlert, silenceSuspendInProgressAlert, silenceSuspendEndedAlert, silencePodExpirationAlert), nonce);
        }
    }
}
