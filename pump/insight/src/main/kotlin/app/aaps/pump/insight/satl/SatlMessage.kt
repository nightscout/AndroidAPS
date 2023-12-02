package app.aaps.pump.insight.satl

import app.aaps.pump.insight.descriptors.SatlCommands
import app.aaps.pump.insight.exceptions.IncompatibleSatlVersionException
import app.aaps.pump.insight.exceptions.InvalidMacTrailerException
import app.aaps.pump.insight.exceptions.InvalidNonceException
import app.aaps.pump.insight.exceptions.InvalidPacketLengthsException
import app.aaps.pump.insight.exceptions.InvalidPreambleException
import app.aaps.pump.insight.exceptions.InvalidSatlCRCException
import app.aaps.pump.insight.exceptions.InvalidSatlCommandException
import app.aaps.pump.insight.utils.ByteBuf
import app.aaps.pump.insight.utils.Nonce
import app.aaps.pump.insight.utils.crypto.Cryptograph

abstract class SatlMessage {

    var nonce: Nonce? = null
    var commID: Long = 0
    lateinit var satlContent: ByteArray
    protected open val data: ByteBuf
        get() = ByteBuf(0)

    protected open fun parse(byteBuf: ByteBuf) = Unit

    fun serialize(key: ByteArray?): ByteBuf {
        val byteBuf: ByteBuf = if (nonce == null || key == null) serializeCRC() else serializeCTR(nonce!!.productionalBytes, key, SatlCommands.fromType(this))
        satlContent = byteBuf.getBytes(8, byteBuf.filledSize - 16)
        return byteBuf
    }

    private fun serializeCRC(): ByteBuf {
        val length = data.filledSize + 31
        val byteBuf = ByteBuf(length + 8)
        byteBuf.putUInt32LE(PREAMBLE)
        byteBuf.putUInt16LE(length)
        byteBuf.putUInt16LE(length.inv())
        byteBuf.putByte(VERSION)
        byteBuf.putByte(SatlCommands.fromType(this))
        byteBuf.putUInt16LE(data.filledSize + 2)
        byteBuf.putUInt32LE(if (this is KeyRequest) 1 else commID)
        byteBuf.putBytes(0x00.toByte(), 13)
        byteBuf.putByteBuf(data)
        byteBuf.putUInt16LE(Cryptograph.calculateCRC(byteBuf.getBytes(8, length - 10)))
        byteBuf.putBytes(0x00.toByte(), 8)
        return byteBuf
    }

    private fun serializeCTR(nonce: ByteBuf, key: ByteArray, commandId: Byte): ByteBuf {
        val data = data
        val encryptedData = ByteBuf.from(Cryptograph.encryptDataCTR(data.bytes, key, nonce.bytes))
        val length = 29 + encryptedData.filledSize
        val byteBuf = ByteBuf(length + 8)
        byteBuf.putUInt32LE(PREAMBLE)
        byteBuf.putUInt16LE(length)
        byteBuf.putUInt16LE(length.inv())
        byteBuf.putByte(VERSION)
        byteBuf.putByte(commandId)
        byteBuf.putUInt16LE(encryptedData.filledSize)
        byteBuf.putUInt32LE(commID)
        byteBuf.putByteBuf(nonce)
        byteBuf.putByteBuf(encryptedData)
        byteBuf.putBytes(Cryptograph.produceCCMTag(byteBuf.getBytes(16, 13), data.bytes, byteBuf.getBytes(8, 21), key))
        return byteBuf
    }

    companion object {

        private const val PREAMBLE = 4293840008L
        private const val VERSION: Byte = 0x20

        @JvmStatic
        @Throws(
            InvalidMacTrailerException::class,
            InvalidSatlCRCException::class,
            InvalidNonceException::class,
            InvalidPreambleException::class,
            InvalidPacketLengthsException::class,
            IncompatibleSatlVersionException::class,
            InvalidSatlCommandException::class
        )
        fun deserialize(data: ByteBuf, lastNonce: Nonce?, key: ByteArray?): SatlMessage? {
            val satlContent = data.getBytes(8, data.filledSize - 16)
            val satlMessage: SatlMessage? = if (key == null) deserializeCRC(data) else lastNonce?.let { deserializeCTR(data, it, key) }
            satlMessage?.let { it.satlContent = satlContent }
            return satlMessage
        }

        @Throws(
            InvalidMacTrailerException::class,
            InvalidNonceException::class,
            InvalidPreambleException::class,
            InvalidPacketLengthsException::class,
            IncompatibleSatlVersionException::class,
            InvalidSatlCommandException::class
        )
        private fun deserializeCTR(data: ByteBuf, lastNonce: Nonce, key: ByteArray): SatlMessage {
            val preamble = data.readUInt32LE()
            val packetLength = data.readUInt16LE()
            val packetLengthXOR = data.readUInt16LE() xor 65535
            val header = data.getBytes(21)
            val version = data.readByte()
            val commandId = data.readByte()
            val message = SatlCommands.fromId(commandId)
            val dataLength = data.readUInt16LE()
            val commId = data.readUInt32LE()
            val nonce = data.readBytes(13)
            var payload = data.readBytes(dataLength)
            val trailer = data.readBytes(8)
            val parsedNonce = Nonce.fromProductionalBytes(nonce)
            payload = Cryptograph.encryptDataCTR(payload, key, nonce)
            if (!trailer.contentEquals(Cryptograph.produceCCMTag(nonce, payload, header, key))) throw InvalidMacTrailerException()
            if (!lastNonce.isSmallerThan(parsedNonce)) throw InvalidNonceException()
            if (preamble != PREAMBLE) throw InvalidPreambleException()
            if (packetLength != packetLengthXOR) throw InvalidPacketLengthsException()
            if (version != VERSION) throw IncompatibleSatlVersionException()
            if (message == null) throw InvalidSatlCommandException()
            return message.also {
                it.parse(ByteBuf.from(payload))
                it.nonce = parsedNonce
                it.commID = commId
            }
        }

        @Throws(InvalidSatlCRCException::class, InvalidPreambleException::class, InvalidPacketLengthsException::class, IncompatibleSatlVersionException::class, InvalidSatlCommandException::class)
        private fun deserializeCRC(data: ByteBuf): SatlMessage {
            val preamble = data.readUInt32LE()
            val packetLength = data.readUInt16LE()
            val packetLengthXOR = data.readUInt16LE() xor 65535
            val crcContent = data.getBytes(packetLength - 10)
            val version = data.readByte()
            val commandId = data.readByte()
            val message = SatlCommands.fromId(commandId)
            val dataLength = data.readUInt16LE()
            val commId = data.readUInt32LE()
            val nonce = data.readBytes(13)
            val payload = data.readBytes(dataLength - 2)
            val crc = data.readUInt16LE()
            data.shift(8)
            if (crc != Cryptograph.calculateCRC(crcContent)) throw InvalidSatlCRCException()
            if (preamble != PREAMBLE) throw InvalidPreambleException()
            if (packetLength != packetLengthXOR) throw InvalidPacketLengthsException()
            if (version != VERSION) throw IncompatibleSatlVersionException()
            if (message == null) throw InvalidSatlCommandException()
            return message.also {
                it.parse(ByteBuf.from(payload))
                it.nonce = Nonce.fromProductionalBytes(nonce)
                it.commID = commId
            }
        }

        @JvmStatic fun hasCompletePacket(byteBuf: ByteBuf): Boolean {
            return if (byteBuf.filledSize < 37) false else byteBuf.filledSize >= byteBuf.getUInt16LE(4) + 8
        }
    }
}