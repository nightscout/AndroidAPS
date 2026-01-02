package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.HeaderEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.HeaderEnabledCommandBuilder
import app.aaps.pump.omnipod.dash.driver.pod.response.ResponseType
import java.nio.ByteBuffer

class GetStatusCommand private constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    private val statusResponseType: ResponseType.StatusResponseType
) : HeaderEnabledCommand(CommandType.GET_STATUS, uniqueId, sequenceNumber, multiCommandFlag) {

    override val encoded: ByteArray
        get() = appendCrc(
            ByteBuffer.allocate(LENGTH + HEADER_LENGTH)
                .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag))
                .put(commandType.value)
                .put(BODY_LENGTH)
                .put(statusResponseType.value)
                .array()
        )

    class Builder : HeaderEnabledCommandBuilder<Builder, GetStatusCommand>() {

        private var statusResponseType: ResponseType.StatusResponseType? = null

        fun setStatusResponseType(statusResponseType: ResponseType.StatusResponseType): Builder {
            this.statusResponseType = statusResponseType
            return this
        }

        override fun buildCommand(): GetStatusCommand {
            requireNotNull(statusResponseType) { "immediateBeepType can not be null" }

            return GetStatusCommand(uniqueId!!, sequenceNumber!!, multiCommandFlag, statusResponseType!!)
        }
    }

    companion object {

        private const val LENGTH: Short = 3
        private const val BODY_LENGTH: Byte = 1
    }
}
