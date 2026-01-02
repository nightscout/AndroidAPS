package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.HeaderEnabledCommandBuilder
import app.aaps.pump.omnipod.dash.driver.pod.definition.BeepType
import app.aaps.pump.omnipod.dash.driver.pod.definition.ProgramReminder
import java.nio.ByteBuffer

class ProgramBeepsCommand private constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    private val immediateBeepType: BeepType,
    private val basalReminder: ProgramReminder,
    private val tempBasalReminder: ProgramReminder,
    private val bolusReminder: ProgramReminder
) : HeaderEnabledCommand(CommandType.PROGRAM_BEEPS, uniqueId, sequenceNumber, multiCommandFlag) {

    override val encoded: ByteArray
        get() = appendCrc(
            ByteBuffer.allocate(LENGTH + HEADER_LENGTH)
                .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag))
                .put(commandType.value)
                .put(BODY_LENGTH)
                .put(immediateBeepType.value)
                .put(basalReminder.encoded)
                .put(tempBasalReminder.encoded)
                .put(bolusReminder.encoded)
                .array()
        )

    class Builder : HeaderEnabledCommandBuilder<Builder, ProgramBeepsCommand>() {

        private var immediateBeepType: BeepType? = null
        private var basalReminder: ProgramReminder? = null
        private var tempBasalReminder: ProgramReminder? = null
        private var bolusReminder: ProgramReminder? = null

        fun setImmediateBeepType(beepType: BeepType): Builder {
            this.immediateBeepType = beepType
            return this
        }

        fun setBasalReminder(programReminder: ProgramReminder): Builder {
            this.basalReminder = programReminder
            return this
        }

        fun setTempBasalReminder(programReminder: ProgramReminder): Builder {
            this.tempBasalReminder = programReminder
            return this
        }

        fun setBolusReminder(programReminder: ProgramReminder): Builder {
            this.bolusReminder = programReminder
            return this
        }

        override fun buildCommand(): ProgramBeepsCommand {
            requireNotNull(immediateBeepType) { "immediateBeepType can not be null" }
            requireNotNull(basalReminder) { "basalReminder can not be null" }
            requireNotNull(tempBasalReminder) { "tempBasalReminder can not be null" }
            requireNotNull(bolusReminder) { "bolusReminder can not be null" }

            return ProgramBeepsCommand(
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                immediateBeepType!!,
                basalReminder!!,
                tempBasalReminder!!,
                bolusReminder!!
            )
        }
    }

    companion object {

        private const val LENGTH: Short = 6
        private const val BODY_LENGTH: Byte = 4
    }
}
