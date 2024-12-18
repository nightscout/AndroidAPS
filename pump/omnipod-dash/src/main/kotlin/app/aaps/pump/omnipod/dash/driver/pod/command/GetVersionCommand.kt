package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.HeaderEnabledCommandBuilder
import java.nio.ByteBuffer

class GetVersionCommand private constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean
) : HeaderEnabledCommand(CommandType.GET_VERSION, uniqueId, sequenceNumber, multiCommandFlag) {

    override val encoded: ByteArray
        get() = appendCrc(
            ByteBuffer.allocate(LENGTH + HEADER_LENGTH)
                .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag))
                .put(commandType.value)
                .put(BODY_LENGTH)
                .putInt(uniqueId)
                .array()
        )

    override fun toString(): String {
        return "GetVersionCommand{" +
            "commandType=" + commandType +
            ", uniqueId=" + uniqueId +
            ", sequenceNumber=" + sequenceNumber +
            ", multiCommandFlag=" + multiCommandFlag +
            '}'
    }

    class Builder : HeaderEnabledCommandBuilder<Builder, GetVersionCommand>() {

        override fun buildCommand(): GetVersionCommand {
            return GetVersionCommand(uniqueId!!, sequenceNumber!!, multiCommandFlag)
        }
    }

    companion object {

        const val DEFAULT_UNIQUE_ID = -1 // FIXME move
        private const val LENGTH: Short = 6
        private const val BODY_LENGTH: Byte = 4
    }
}
