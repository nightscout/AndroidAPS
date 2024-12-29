package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.HeaderEnabledCommandBuilder
import java.nio.ByteBuffer
import java.util.*

class SetUniqueIdCommand private constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    private val lotNumber: Int,
    private val podSequenceNumber: Int,
    private val initializationTime: Date
) : HeaderEnabledCommand(CommandType.SET_UNIQUE_ID, uniqueId, sequenceNumber, multiCommandFlag) {

    override val encoded: ByteArray
        get() = appendCrc(
            ByteBuffer.allocate(LENGTH + HEADER_LENGTH)
                .put(encodeHeader(DEFAULT_UNIQUE_ID, sequenceNumber, LENGTH, multiCommandFlag))
                .put(commandType.value)
                .put(BODY_LENGTH)
                .putInt(uniqueId)
                .put(0x14.toByte()) // FIXME ??
                .put(0x04.toByte()) // FIXME ??
                .put(encodeInitializationTime(initializationTime))
                .putInt(lotNumber)
                .putInt(podSequenceNumber)
                .array()
        )

    override fun toString(): String {
        return "SetUniqueIdCommand{" +
            "lotNumber=" + lotNumber +
            ", podSequenceNumber=" + podSequenceNumber +
            ", initializationTime=" + initializationTime +
            ", commandType=" + commandType +
            ", uniqueId=" + uniqueId +
            ", sequenceNumber=" + sequenceNumber +
            ", multiCommandFlag=" + multiCommandFlag +
            '}'
    }

    class Builder : HeaderEnabledCommandBuilder<Builder, SetUniqueIdCommand>() {

        private var lotNumber: Int? = null
        private var podSequenceNumber: Int? = null
        private var initializationTime: Date? = null

        fun setLotNumber(lotNumber: Int): Builder {
            this.lotNumber = lotNumber
            return this
        }

        fun setPodSequenceNumber(podSequenceNumber: Int): Builder {
            this.podSequenceNumber = podSequenceNumber
            return this
        }

        fun setInitializationTime(initializationTime: Date): Builder {
            this.initializationTime = initializationTime
            return this
        }

        override fun buildCommand(): SetUniqueIdCommand {
            requireNotNull(lotNumber) { "lotNumber can not be null" }
            requireNotNull(podSequenceNumber) { "podSequenceNumber can not be null" }
            requireNotNull(initializationTime) { "initializationTime can not be null" }
            return SetUniqueIdCommand(
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                lotNumber!!,
                podSequenceNumber!!,
                initializationTime!!
            )
        }
    }

    companion object {

        private const val DEFAULT_UNIQUE_ID = -1
        private const val LENGTH: Short = 21
        private const val BODY_LENGTH: Byte = 19
        private fun encodeInitializationTime(date: Date): ByteArray {
            val instance = Calendar.getInstance()
            instance.time = date
            return byteArrayOf(
                (instance[Calendar.MONTH] + 1).toByte(),
                instance[Calendar.DATE].toByte(),
                (instance[Calendar.YEAR] % 100).toByte(),
                instance[Calendar.HOUR_OF_DAY].toByte(),
                instance[Calendar.MINUTE].toByte()
            )
        }
    }
}
