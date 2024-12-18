package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.BasalInsulinProgramElement
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.util.ProgramBasalUtil
import app.aaps.pump.omnipod.dash.driver.pod.command.insulin.program.util.ProgramTempBasalUtil
import app.aaps.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import java.nio.ByteBuffer
import java.util.*

// NOT SUPPORTED: percentage temp basal
class ProgramTempBasalCommand private constructor(
    private val interlockCommand: ProgramInsulinCommand,
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    private val programReminder: ProgramReminder,
    insulinProgramElements: List<BasalInsulinProgramElement>
) : HeaderEnabledCommand(CommandType.PROGRAM_TEMP_BASAL, uniqueId, sequenceNumber, multiCommandFlag) {

    private val insulinProgramElements: List<BasalInsulinProgramElement> = ArrayList(insulinProgramElements)

    private fun getBodyLength(): Byte = (insulinProgramElements.size * 6 + 8).toByte()

    private fun getLength(): Short = (getBodyLength() + 2).toShort()

    class Builder : NonceEnabledCommandBuilder<Builder, ProgramTempBasalCommand>() {

        private var programReminder: ProgramReminder? = null
        private var rateInUnitsPerHour: Double? = null
        private var durationInMinutes: Short? = null

        fun setProgramReminder(programReminder: ProgramReminder): Builder {
            this.programReminder = programReminder
            return this
        }

        fun setRateInUnitsPerHour(rateInUnitsPerHour: Double): Builder {
            this.rateInUnitsPerHour = rateInUnitsPerHour
            return this
        }

        fun setDurationInMinutes(durationInMinutes: Short): Builder {
            require(durationInMinutes % 30 == 0) { "durationInMinutes must be dividable by 30" }

            this.durationInMinutes = durationInMinutes
            return this
        }

        override fun buildCommand(): ProgramTempBasalCommand {
            requireNotNull(programReminder) { "programReminder can not be null" }
            requireNotNull(rateInUnitsPerHour) { "rateInUnitsPerHour can not be null" }
            requireNotNull(durationInMinutes) { "durationInMinutes can not be null" }

            val durationInSlots = (durationInMinutes!! / 30).toByte()
            val pulsesPerSlot = ProgramTempBasalUtil.mapTempBasalToPulsesPerSlot(durationInSlots, rateInUnitsPerHour!!)
            val tenthPulsesPerSlot = ProgramTempBasalUtil.mapTempBasalToTenthPulsesPerSlot(
                durationInSlots.toInt(),
                rateInUnitsPerHour!!
            )
            val shortInsulinProgramElements = ProgramTempBasalUtil.mapPulsesPerSlotToShortInsulinProgramElements(
                pulsesPerSlot
            )
            val insulinProgramElements = ProgramTempBasalUtil.mapTenthPulsesPerSlotToLongInsulinProgramElements(
                tenthPulsesPerSlot
            )
            val interlockCommand = ProgramInsulinCommand(
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                nonce!!,
                shortInsulinProgramElements,
                ProgramTempBasalUtil.calculateChecksum(durationInSlots, pulsesPerSlot[0], pulsesPerSlot),
                durationInSlots,
                0x3840.toShort(),
                pulsesPerSlot[0],
                ProgramInsulinCommand.DeliveryType.TEMP_BASAL
            )
            return ProgramTempBasalCommand(
                interlockCommand,
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                programReminder!!,
                insulinProgramElements
            )
        }
    }

    override val encoded: ByteArray
        get() {
            val firstProgramElement = insulinProgramElements[0]
            val remainingTenthPulsesInFirstElement: Short
            val delayUntilNextTenthPulseInUsec: Int
            if (firstProgramElement.totalTenthPulses.toInt() == 0) {
                remainingTenthPulsesInFirstElement = firstProgramElement.numberOfSlots.toShort()
                delayUntilNextTenthPulseInUsec =
                    ProgramBasalUtil.MAX_DELAY_BETWEEN_TENTH_PULSES_IN_USEC_AND_USECS_IN_BASAL_SLOT
            } else {
                remainingTenthPulsesInFirstElement = firstProgramElement.totalTenthPulses
                delayUntilNextTenthPulseInUsec =
                    (firstProgramElement.numberOfSlots.toLong() * 1800.0 / remainingTenthPulsesInFirstElement * 1000000).toInt()
            }
            val buffer = ByteBuffer.allocate(getLength().toInt())
                .put(commandType.value)
                .put(getBodyLength())
                .put(programReminder.encoded)
                .put(0x00.toByte()) // Current slot index
                .putShort(remainingTenthPulsesInFirstElement)
                .putInt(delayUntilNextTenthPulseInUsec)
            for (element in insulinProgramElements) {
                buffer.put(element.encoded)
            }
            val tempBasalCommand = buffer.array()
            val interlockCommand = interlockCommand.encoded
            val header: ByteArray = encodeHeader(
                uniqueId,
                sequenceNumber,
                (tempBasalCommand.size + interlockCommand.size).toShort(),
                multiCommandFlag
            )
            return appendCrc(
                ByteBuffer.allocate(header.size + interlockCommand.size + tempBasalCommand.size)
                    .put(header)
                    .put(interlockCommand)
                    .put(tempBasalCommand)
                    .array()
            )
        }
}
