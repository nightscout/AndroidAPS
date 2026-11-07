package app.aaps.pump.omnipod.common.bledriver.pod.command

import app.aaps.pump.omnipod.common.bledriver.pod.command.base.CommandType
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.HeaderEnabledCommand
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.builder.NonceEnabledCommandBuilder
import app.aaps.pump.omnipod.common.bledriver.pod.command.insulin.program.BolusShortInsulinProgramElement
import app.aaps.pump.omnipod.common.bledriver.pod.definition.ProgramReminder
import app.aaps.pump.omnipod.common.bledriver.pod.util.MessageUtil
import java.nio.ByteBuffer

// NOT SUPPORTED: extended bolus
class ProgramBolusCommand private constructor(
    private val interlockCommand: ProgramInsulinCommand,
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    private val programReminder: ProgramReminder,
    private val numberOfTenthPulses: Short,
    private val delayUntilFirstTenthPulseInUsec: Int,
    private val o5BolusInfo: O5BolusInfo?
) : HeaderEnabledCommand(CommandType.PROGRAM_BOLUS, uniqueId, sequenceNumber, multiCommandFlag) {

    /** The O5-only trailing fields - see this class's doc comment. [bolusSource] is
     *  "typically 1" per OmnipodKit's own comment; [mealUnitsTenthPulses]/
     *  [correctionUnitsTenthPulses] are 0 for prime/cannula-insertion boluses. */
    class O5BolusInfo(
        val bolusSource: Byte,
        val mealUnitsTenthPulses: Short,
        val correctionUnitsTenthPulses: Short
    )

    override val encoded: ByteArray
        get() {
            val length = if (o5BolusInfo != null) O5_LENGTH else LENGTH
            val bodyLength = if (o5BolusInfo != null) O5_BODY_LENGTH else BODY_LENGTH
            val bolusCommandBuffer = ByteBuffer.allocate(length.toInt())
                .put(commandType.value)
                .put(bodyLength)
                .put(programReminder.encoded)
                .putShort(numberOfTenthPulses)
                .putInt(delayUntilFirstTenthPulseInUsec)
                .putShort(0.toShort()) // Extended bolus pulses
                .putInt(0) // Delay between tenth extended pulses in usec
            if (o5BolusInfo != null) {
                bolusCommandBuffer
                    .put(o5BolusInfo.bolusSource)
                    .putShort(o5BolusInfo.mealUnitsTenthPulses)
                    .putShort(o5BolusInfo.correctionUnitsTenthPulses)
            }
            val bolusCommand = bolusCommandBuffer.array()
            val interlockCommand = interlockCommand.encoded
            val header: ByteArray = encodeHeader(
                uniqueId,
                sequenceNumber,
                (bolusCommand.size + interlockCommand.size).toShort(),
                multiCommandFlag
            )
            return appendCrc(
                ByteBuffer.allocate(header.size + interlockCommand.size + bolusCommand.size)
                    .put(header)
                    .put(interlockCommand)
                    .put(bolusCommand)
                    .array()
            )
        }

    override fun toString(): String {
        return "ProgramBolusCommand{" +
            "interlockCommand=" + interlockCommand +
            ", programReminder=" + programReminder +
            ", numberOfTenthPulses=" + numberOfTenthPulses +
            ", delayUntilFirstTenthPulseInUsec=" + delayUntilFirstTenthPulseInUsec +
            ", o5BolusInfo=" + (o5BolusInfo != null) +
            ", commandType=" + commandType +
            ", uniqueId=" + uniqueId +
            ", sequenceNumber=" + sequenceNumber +
            ", multiCommandFlag=" + multiCommandFlag +
            '}'
    }

    class Builder : NonceEnabledCommandBuilder<Builder, ProgramBolusCommand>() {

        private var numberOfUnits: Double? = null
        private var delayBetweenPulsesInEighthSeconds: Byte? = null
        private var programReminder: ProgramReminder? = null
        private var mealUnits: Double? = null
        private var correctionUnits: Double? = null

        fun setNumberOfUnits(numberOfUnits: Double): Builder {
            require(numberOfUnits > 0.0) { "Number of units should be greater than zero" }
            require((numberOfUnits * 1000).toInt() % 50 == 0) { "Number of units must be dividable by 0.05" }
            this.numberOfUnits = (numberOfUnits * 100).toInt() / 100.0
            return this
        }

        fun setDelayBetweenPulsesInEighthSeconds(delayBetweenPulsesInEighthSeconds: Byte): Builder {
            this.delayBetweenPulsesInEighthSeconds = delayBetweenPulsesInEighthSeconds
            return this
        }

        fun setProgramReminder(programReminder: ProgramReminder): Builder {
            this.programReminder = programReminder
            return this
        }

        /** O5 only - selects the O5 wire format (see this class's doc comment). Must NOT be
         *  called for Dash/Eros, which use the original, shorter format instead. */
        fun setO5BolusInfo(mealUnits: Double, correctionUnits: Double): Builder {
            this.mealUnits = mealUnits
            this.correctionUnits = correctionUnits
            return this
        }

        override fun buildCommand(): ProgramBolusCommand {
            requireNotNull(numberOfUnits) { "numberOfUnits can not be null" }
            requireNotNull(delayBetweenPulsesInEighthSeconds) { "delayBetweenPulsesInEighthSeconds can not be null" }
            requireNotNull(programReminder) { "programReminder can not be null" }

            val numberOfPulses = Math.round(numberOfUnits!! * 20).toShort()
            val byte10And11 = (numberOfPulses * delayBetweenPulsesInEighthSeconds!!).toShort()
            val interlockCommand = ProgramInsulinCommand(
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                nonce!!,
                listOf(BolusShortInsulinProgramElement(numberOfPulses)),
                calculateChecksum(0x01.toByte(), byte10And11, numberOfPulses),
                0x01.toByte(),
                byte10And11,
                numberOfPulses,
                ProgramInsulinCommand.DeliveryType.BOLUS
            )
            val delayUntilFirstTenthPulseInUsec = delayBetweenPulsesInEighthSeconds!! / 8 * 100000
            val o5BolusInfo = mealUnits?.let { meal ->
                O5BolusInfo(
                    bolusSource = O5_BOLUS_SOURCE,
                    mealUnitsTenthPulses = Math.round(meal * 200).toShort(),
                    correctionUnitsTenthPulses = Math.round((correctionUnits ?: 0.0) * 200).toShort()
                )
            }
            return ProgramBolusCommand(
                interlockCommand,
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                programReminder!!,
                (numberOfPulses * 10).toShort(),
                delayUntilFirstTenthPulseInUsec,
                o5BolusInfo
            )
        }
    }

    companion object {

        private const val LENGTH: Short = 15
        private const val BODY_LENGTH: Byte = 13
        private const val O5_LENGTH: Short = 20
        private const val O5_BODY_LENGTH: Byte = 18
        private const val O5_BOLUS_SOURCE: Byte = 1

        private fun calculateChecksum(numberOfSlots: Byte, byte10And11: Short, numberOfPulses: Short): Short {
            return MessageUtil.calculateChecksum(
                ByteBuffer.allocate(7)
                    .put(numberOfSlots)
                    .putShort(byte10And11)
                    .putShort(numberOfPulses)
                    .putShort(numberOfPulses)
                    .array()
            )
        }
    }
}
