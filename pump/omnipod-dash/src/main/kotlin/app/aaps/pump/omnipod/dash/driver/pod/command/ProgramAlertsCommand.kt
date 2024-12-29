package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertConfiguration
import java.nio.ByteBuffer
import java.util.*

class ProgramAlertsCommand private constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    alertConfigurations: List<AlertConfiguration>,
    nonce: Int
) : NonceEnabledCommand(CommandType.PROGRAM_ALERTS, uniqueId, sequenceNumber, multiCommandFlag, nonce) {

    private val alertConfigurations: List<AlertConfiguration>

    private fun getLength(): Short {
        return (alertConfigurations.size * 6 + 6).toShort()
    }

    private fun getBodyLength(): Byte {
        return (alertConfigurations.size * 6 + 4).toByte()
    }

    override val encoded: ByteArray
        get() {
            val byteBuffer: ByteBuffer = ByteBuffer.allocate(getLength() + HEADER_LENGTH)
                .put(encodeHeader(uniqueId, sequenceNumber, getLength(), multiCommandFlag))
                .put(commandType.value)
                .put(getBodyLength())
                .putInt(nonce)
            for (configuration in alertConfigurations) {
                byteBuffer.put(configuration.encoded)
            }
            return appendCrc(byteBuffer.array())
        }

    val encodedWithoutHeaderAndCRC32: ByteArray
        get() {
            val byteBuffer: ByteBuffer = ByteBuffer.allocate(getLength().toInt())
                .put(commandType.value)
                .put(getBodyLength())
                .putInt(nonce)
            for (configuration in alertConfigurations) {
                byteBuffer.put(configuration.encoded)
            }
            return byteBuffer.array()
        }

    override fun toString(): String {
        return "ProgramAlertsCommand{" +
            "alertConfigurations=" + alertConfigurations +
            ", nonce=" + nonce +
            ", commandType=" + commandType +
            ", uniqueId=" + uniqueId +
            ", sequenceNumber=" + sequenceNumber +
            ", multiCommandFlag=" + multiCommandFlag +
            '}'
    }

    class Builder : NonceEnabledCommandBuilder<Builder, ProgramAlertsCommand>() {

        private var alertConfigurations: List<AlertConfiguration>? = null

        fun setAlertConfigurations(alertConfigurations: List<AlertConfiguration>): Builder {
            this.alertConfigurations = alertConfigurations
            return this
        }

        override fun buildCommand(): ProgramAlertsCommand {
            requireNotNull(alertConfigurations) { "alertConfigurations can not be null" }
            return ProgramAlertsCommand(uniqueId!!, sequenceNumber!!, multiCommandFlag, alertConfigurations!!, nonce!!)
        }
    }

    init {
        this.alertConfigurations = ArrayList(alertConfigurations)
    }
}
