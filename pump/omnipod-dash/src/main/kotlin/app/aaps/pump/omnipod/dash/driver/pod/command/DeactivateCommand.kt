package app.aaps.pump.omnipod.dash.driver.pod.command

import app.aaps.pump.omnipod.dash.driver.pod.command.base.CommandType
import app.aaps.pump.omnipod.dash.driver.pod.command.base.NonceEnabledCommand
import app.aaps.pump.omnipod.dash.driver.pod.command.base.builder.NonceEnabledCommandBuilder
import java.nio.ByteBuffer

class DeactivateCommand private constructor(
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    nonce: Int
) : NonceEnabledCommand(CommandType.DEACTIVATE, uniqueId, sequenceNumber, multiCommandFlag, nonce) {

    override val encoded: ByteArray
        get() = appendCrc(
            ByteBuffer.allocate(LENGTH + HEADER_LENGTH)
                .put(encodeHeader(uniqueId, sequenceNumber, LENGTH, multiCommandFlag))
                .put(commandType.value)
                .put(BODY_LENGTH)
                .putInt(nonce)
                .array()
        )

    override fun toString(): String = "DeactivateCommand{" +
        "nonce=" + nonce +
        ", commandType=" + commandType +
        ", uniqueId=" + uniqueId +
        ", sequenceNumber=" + sequenceNumber +
        ", multiCommandFlag=" + multiCommandFlag +
        '}'

    class Builder : NonceEnabledCommandBuilder<Builder, DeactivateCommand>() {

        override fun buildCommand(): DeactivateCommand =
            DeactivateCommand(
                uniqueId!!,
                sequenceNumber!!,
                multiCommandFlag,
                nonce!!
            ) // TODO this might crash if not all are set
    }

    companion object {

        private const val LENGTH: Short = 6
        private const val BODY_LENGTH: Byte = 4
    }
}
