package app.aaps.pump.omnipod.dash.driver.pod.command.base

import app.aaps.pump.omnipod.dash.driver.pod.util.MessageUtil
import java.nio.ByteBuffer

abstract class HeaderEnabledCommand protected constructor(
    override val commandType: CommandType,
    protected val uniqueId: Int,
    override val sequenceNumber: Short,
    protected val multiCommandFlag: Boolean
) : Command {

    companion object {

        internal fun appendCrc(command: ByteArray): ByteArray =
            ByteBuffer.allocate(command.size + 2)
                .put(command)
                .putShort(MessageUtil.createCrc(command))
                .array()

        internal fun encodeHeader(
            uniqueId: Int,
            sequenceNumber: Short,
            length: Short,
            multiCommandFlag: Boolean
        ): ByteArray =
            ByteBuffer.allocate(6)
                .putInt(uniqueId)
                .putShort((sequenceNumber.toInt() and 0x0f shl 10 or length.toInt() or ((if (multiCommandFlag) 1 else 0) shl 15)).toShort())
                .array()

        internal const val HEADER_LENGTH: Short = 6
    }
}
