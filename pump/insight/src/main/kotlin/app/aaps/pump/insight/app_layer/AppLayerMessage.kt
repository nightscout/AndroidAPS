package app.aaps.pump.insight.app_layer

import app.aaps.pump.insight.descriptors.AppCommands
import app.aaps.pump.insight.descriptors.AppErrors
import app.aaps.pump.insight.descriptors.MessagePriority
import app.aaps.pump.insight.satl.DataMessage
import app.aaps.pump.insight.utils.ByteBuf
import app.aaps.pump.insight.utils.crypto.Cryptograph

open class AppLayerMessage(private val messagePriority: MessagePriority, private val inCRC: Boolean, private val outCRC: Boolean, open var service: Service?) : Comparable<AppLayerMessage> {

    protected open val data: ByteBuf
        get() = ByteBuf(0)

    @Throws(Exception::class)
    protected open fun parse(byteBuf: ByteBuf) = Unit

    fun serialize(): ByteBuf {
        val data = data.bytes
        val byteBuf = ByteBuf(4 + data.size + if (outCRC) 2 else 0)
        byteBuf.putByte(VERSION)
        byteBuf.putByte(service!!.id)
        byteBuf.putUInt16LE(AppCommands.fromType(this))
        byteBuf.putBytes(data)
        if (outCRC) byteBuf.putUInt16LE(Cryptograph.calculateCRC(data))
        return byteBuf
    }

    override fun compareTo(other: AppLayerMessage): Int {
        return messagePriority.compareTo(other.messagePriority)
    }

    companion object {

        private const val VERSION: Byte = 0x20
        fun deserialize(byteBuf: ByteBuf): AppLayerMessage {
            val version = byteBuf.readByte()
            val service = byteBuf.readByte()
            val command = byteBuf.readUInt16LE()
            val error = byteBuf.readUInt16LE()
            val message = AppCommands.fromId(command)
            if (version != VERSION) throw app.aaps.pump.insight.exceptions.IncompatibleAppVersionException()
            if (Service.fromId(service) == null) throw app.aaps.pump.insight.exceptions.UnknownServiceException()
            if (error != 0 || message == null) {
                val exceptionClass = AppErrors.fromId(error)
                exceptionClass?.let { throw it.getConstructor(Int::class.javaPrimitiveType).newInstance(error)!! }
                    ?: throw app.aaps.pump.insight.exceptions.app_layer_errors.UnknownAppLayerErrorCodeException(
                        error
                    )
            }
            val data = byteBuf.readBytes(byteBuf.filledSize - if (message.inCRC) 2 else 0)
            if (message.inCRC && Cryptograph.calculateCRC(data) != byteBuf.readUInt16LE()) throw app.aaps.pump.insight.exceptions.InvalidAppCRCException()
            message.parse(ByteBuf.from(data))
            return message
        }

        @JvmStatic fun wrap(message: AppLayerMessage): DataMessage {
            val dataMessage = DataMessage()
            dataMessage.data = message.serialize()
            return dataMessage
        }

        @JvmStatic fun unwrap(dataMessage: DataMessage): AppLayerMessage {
            return deserialize(dataMessage.data)
        }
    }
}