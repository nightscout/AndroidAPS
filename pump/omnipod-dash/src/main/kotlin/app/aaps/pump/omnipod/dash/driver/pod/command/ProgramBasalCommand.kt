package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.BasalInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.util.ProgramBasalUtil
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.util.ProgramTempBasalUtil.mapTenthPulsesPerSlotToLongInsulinProgramElements
import app.aaps.pump.omnipod.dash.driver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import java.nio.ByteBuffer
import java.util.*

// Always preceded by 0x1a ProgramInsulinCommand
class ProgramBasalCommand private constructor(
    private val interlockCommand: ProgramInsulinCommand,
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    insulinProgramElements: List<BasalInsulinProgramElement>,
    programReminder: ProgramReminder,
    currentInsulinProgramElementIndex: Byte,
    remainingTenthPulsesInCurrentInsulinProgramElement: Short,
    delayUntilNextTenthPulseInUsec: Int
) : HeaderEnabledCommand(CommandType.PROGRAM_BASAL, uniqueId, sequenceNumber, multiCommandFlag) {

    private val insulinProgramElements: List<BasalInsulinProgramElement> = ArrayList(insulinProgramElements)
    private val programReminder: ProgramReminder
    private val currentInsulinProgramElementIndex: Byte
    private val remainingTenthPulsesInCurrentInsulinProgramElement: Short
    private val delayUntilNextTenthPulseInUsec: Int
    val length: Short
        get() = (insulinProgramElements.size * 6 + 10).toShort()
    private val bodyLength: Byte
        get() = (insulinProgramElements.size * 6 + 8).toByte()

    override val encoded: ByteArray
        get() {
            val buffer = ByteBuffer.allocate(length.toInt())
                .put(commandType.value)
                .put(bodyLength)
                .put(programReminder.encoded)
                .put(currentInsulinProgramElementIndex)
                .putShort(remainingTenthPulsesInCurrentInsulinProgramElement)
                .putInt(delayUntilNextTenthPulseInUsec)
            for (insulinProgramElement in insulinProgramElements) {
                buffer.put(insulinProgramElement.encoded)
            }
            val basalCommand = buffer.array()
            val interlockCommand = interlockCommand.encoded
            val header: ByteArray = encodeHeader(
                uniqueId,
                sequenceNumber,
                (basalCommand.size + interlockCommand.size).toShort(),
                multiCommandFlag
            )
            return appendCrc(
                ByteBuffer.allocate(basalCommand.size + interlockCommand.size + header.size)
                    .put(header)
                    .put(interlockCommand)
                    .put(basalCommand)
                    .array()
            )
        }

    override fun toString(): String {
        return "ProgramBasalCommand{" +
            "interlockCommand=" + interlockCommand +
            ", insulinProgramElements=" + insulinProgramElements +
            ", programReminder=" + programReminder +
            ", currentInsulinProgramElementIndex=" + currentInsulinProgramElementIndex +
            ", remainingTenthPulsesInCurrentInsulinProgramElement=" + remainingTenthPulsesInCurrentInsulinProgramElement +
            ", delayUntilNextTenthPulseInUsec=" + delayUntilNextTenthPulseInUsec +
            ", commandType=" + commandType +
            ", uniqueId=" + uniqueId +
            ", sequenceNumber=" + sequenceNumber +
            ", multiCommandFlag=" + multiCommandFlag +
            '}'
    }

    class Builder : NonceEnabledCommandBuilder<Builder, ProgramBasalCommand>() {

        private var basalProgram: BasalProgram? = null
        private var programReminder: ProgramReminder? = null
        private var currentTime: Date? = null

        fun setBasalProgram(basalProgram: BasalProgram): Builder {
            this.basalProgram = basalProgram
            return this
        }

        fun setProgramReminder(programReminder: ProgramReminder): Builder {
            this.programReminder = programReminder
            return this
        }

        fun setCurrentTime(currentTime: Date): Builder {
            this.currentTime = currentTime
            return this
        }

        override fun buildCommand(): ProgramBasalCommand {
            val program = requireNotNull(basalProgram) { "basalProgram can not be null" }
            val reminder = requireNotNull(programReminder) { "programReminder can not be null" }
            val time = requireNotNull(currentTime) { "currentTime can not be null" }

            val pulsesPerSlot = ProgramBasalUtil.mapBasalProgramToPulsesPerSlot(program)
            val currentSlot = ProgramBasalUtil.calculateCurrentSlot(pulsesPerSlot, time)
            val checksum = ProgramBasalUtil.calculateChecksum(pulsesPerSlot, currentSlot)
            val longInsulinProgramElements: List<BasalInsulinProgramElement> =
                mapTenthPulsesPerSlotToLongInsulinProgramElements(
                    ProgramBasalUtil.mapBasalProgramToTenthPulsesPerSlot(program)
                )
            val shortInsulinProgramElements = ProgramBasalUtil.mapPulsesPerSlotToShortInsulinProgramElements(
                pulsesPerSlot
            )
            val currentBasalInsulinProgramElement = ProgramBasalUtil.calculateCurrentLongInsulinProgramElement(
                longInsulinProgramElements,
                time
            )
            val interlockCommand = ProgramInsulinCommand(
                uniqueId!!, sequenceNumber!!, multiCommandFlag, nonce!!,
                shortInsulinProgramElements, checksum, currentSlot.index, currentSlot.eighthSecondsRemaining,
                currentSlot.pulsesRemaining, ProgramInsulinCommand.DeliveryType.BASAL
            )
            return ProgramBasalCommand(
                interlockCommand,
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                longInsulinProgramElements,
                reminder,
                currentBasalInsulinProgramElement.index,
                currentBasalInsulinProgramElement.remainingTenthPulses,
                currentBasalInsulinProgramElement.delayUntilNextTenthPulseInUsec
            )
        }
    }

    init {
        this.programReminder = programReminder
        this.currentInsulinProgramElementIndex = currentInsulinProgramElementIndex
        this.remainingTenthPulsesInCurrentInsulinProgramElement = remainingTenthPulsesInCurrentInsulinProgramElement
        this.delayUntilNextTenthPulseInUsec = delayUntilNextTenthPulseInUsec
    }
}
