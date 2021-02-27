package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.CommandType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.Encodable
import java.nio.ByteBuffer
import java.util.*

class SilenceAlertsCommand private constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    private val parameters: SilenceAlertCommandParameters,
    nonce: Int
) : NonceEnabledCommand(CommandType.SILENCE_ALERTS, uniqueId, sequenceNumber, multiCommandFlag, nonce) {

    override val encoded: ByteArray
        get() =
            appendCrc(ByteBuffer.allocate(LENGTH + HEADER_LENGTH) //
                .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag)) //
                .put(commandType.value) //
                .put(BODY_LENGTH) //
                .putInt(nonce) //
                .put(parameters.encoded) //
                .array())

    override fun toString(): String {
        return "SilenceAlertsCommand{" +
            "parameters=" + parameters +
            ", nonce=" + nonce +
            ", commandType=" + commandType +
            ", uniqueId=" + uniqueId +
            ", sequenceNumber=" + sequenceNumber +
            ", multiCommandFlag=" + multiCommandFlag +
            '}'
    }

    class SilenceAlertCommandParameters(private val silenceAutoOffAlert: Boolean, private val silenceMultiCommandAlert: Boolean, private val silenceExpirationImminentAlert: Boolean, private val silenceUserSetExpirationAlert: Boolean, private val silenceLowReservoirAlert: Boolean, private val silenceSuspendInProgressAlert: Boolean, private val silenceSuspendEndedAlert: Boolean, private val silencePodExpirationAlert: Boolean) : Encodable {

        override val encoded: ByteArray
            get() {
                val bitSet = BitSet(8)
                bitSet[0] = silenceAutoOffAlert
                bitSet[1] = silenceMultiCommandAlert
                bitSet[2] = silenceExpirationImminentAlert
                bitSet[3] = silenceUserSetExpirationAlert
                bitSet[4] = silenceLowReservoirAlert
                bitSet[5] = silenceSuspendInProgressAlert
                bitSet[6] = silenceSuspendEndedAlert
                bitSet[7] = silencePodExpirationAlert
                return bitSet.toByteArray()
            }
    }

    class Builder : NonceEnabledCommandBuilder<Builder, SilenceAlertsCommand>() {

        private var silenceAutoOffAlert = false
        private var silenceMultiCommandAlert = false
        private var silenceExpirationImminentAlert = false
        private var silenceUserSetExpirationAlert = false
        private var silenceLowReservoirAlert = false
        private var silenceSuspendInProgressAlert = false
        private var silenceSuspendEndedAlert = false
        private var silencePodExpirationAlert = false

        fun setSilenceAutoOffAlert(silenceAutoOffAlert: Boolean): Builder {
            this.silenceAutoOffAlert = silenceAutoOffAlert
            return this
        }

        fun setSilenceMultiCommandAlert(silenceMultiCommandAlert: Boolean): Builder {
            this.silenceMultiCommandAlert = silenceMultiCommandAlert
            return this
        }

        fun setSilenceExpirationImminentAlert(silenceExpirationImminentAlert: Boolean): Builder {
            this.silenceExpirationImminentAlert = silenceExpirationImminentAlert
            return this
        }

        fun setSilenceUserSetExpirationAlert(silenceUserSetExpirationAlert: Boolean): Builder {
            this.silenceUserSetExpirationAlert = silenceUserSetExpirationAlert
            return this
        }

        fun setSilenceLowReservoirAlert(silenceLowReservoirAlert: Boolean): Builder {
            this.silenceLowReservoirAlert = silenceLowReservoirAlert
            return this
        }

        fun setSilenceSuspendInProgressAlert(silenceSuspendInProgressAlert: Boolean): Builder {
            this.silenceSuspendInProgressAlert = silenceSuspendInProgressAlert
            return this
        }

        fun setSilenceSuspendEndedAlert(silenceSuspendEndedAlert: Boolean): Builder {
            this.silenceSuspendEndedAlert = silenceSuspendEndedAlert
            return this
        }

        fun setSilencePodExpirationAlert(silencePodExpirationAlert: Boolean): Builder {
            this.silencePodExpirationAlert = silencePodExpirationAlert
            return this
        }

        override fun buildCommand(): SilenceAlertsCommand {
            return SilenceAlertsCommand(uniqueId!!, sequenceNumber!!, multiCommandFlag, SilenceAlertCommandParameters(silenceAutoOffAlert, silenceMultiCommandAlert, silenceExpirationImminentAlert, silenceUserSetExpirationAlert, silenceLowReservoirAlert, silenceSuspendInProgressAlert, silenceSuspendEndedAlert, silencePodExpirationAlert), nonce!!)
        }
    }

    companion object {

        private const val LENGTH = 7.toShort()
        private const val BODY_LENGTH = 5.toByte()
    }
}