package com.nightscout.eversense.packets

import com.nightscout.eversense.util.EversenseLogger
import com.nightscout.eversense.enums.EversenseSecurityType
import com.nightscout.eversense.packets.e3.EversenseE3Packets
import com.nightscout.eversense.packets.e3.util.EversenseE3Writer
import com.nightscout.eversense.packets.e365.Eversense365Packets
import com.nightscout.eversense.util.EversenseCrypto365Util
import kotlin.math.min

abstract class EversenseBasePacket : Object() {
    abstract fun getRequestData(): ByteArray
    abstract fun parseResponse(): Response?

    protected var receivedData = UByteArray(0)
    @Volatile var isErrorResponse: Boolean = false
    open val skipResponseIdValidation: Boolean = false

    fun getAnnotation(): EversensePacket? {
        return this.javaClass.annotations.find { it.annotationClass == EversensePacket::class } as? EversensePacket
    }

    protected fun getStartIndex(): Int {
        val annotation = getAnnotation() ?:run {
            EversenseLogger.error("EversenseBasePacket", this.javaClass.name + " does not have the EversensePacket annotation...")
            return 0
        }

        return when(annotation.responseId) {
            EversenseE3Packets.ReadSingleByteSerialFlashRegisterResponseId,
            EversenseE3Packets.ReadTwoByteSerialFlashRegisterResponseId,
            EversenseE3Packets.ReadFourByteSerialFlashRegisterResponseId -> 4

            else -> 1
        }
    }

    fun appendData(data: UByteArray) {
        receivedData += data
    }

    fun buildRequest(cryptoUtil: EversenseCrypto365Util, payloadSize: Int): ByteArray? {
        val annotation = getAnnotation() ?:run {
            EversenseLogger.error("EversenseBasePacket", this.javaClass.name + " does not have the EversensePacket annotation...")
            return null
        }

        when(annotation.securityType) {
            EversenseSecurityType.None -> {
                var requestData = byteArrayOf(annotation.requestId)
                requestData += this.getRequestData()
                requestData += EversenseE3Writer.generateChecksumCRC16(requestData)

                return requestData
            }

            EversenseSecurityType.SecureV2 -> {
                var requestData = byteArrayOf(annotation.requestId, annotation.typeId)
                requestData += this.getRequestData()

                if (annotation.requestId != Eversense365Packets.AuthenticateCommandId) {
                    requestData = cryptoUtil.encrypt(requestData)
                }

                return encodeMessage(requestData, payloadSize)
            }
        }
    }

    private fun encodeMessage(data: ByteArray = getRequestData(), chunkSize: Int = 20): ByteArray {
        val adjustedChunkSize = chunkSize - 2
        val totalChunks = (data.size + adjustedChunkSize - 1) / adjustedChunkSize

        // Calculate total size needed for the result array
        val totalHeaderSize = 3 + 2 * (totalChunks - 1)
        val totalSize = totalHeaderSize + data.size

        val result = ByteArray(totalSize)
        var currentIndex = 0
        var currentPos = 0

        for (chunkIndex in 1..totalChunks) {
            val header: ByteArray = if (chunkIndex == 1) {
                byteArrayOf(1.toByte(), totalChunks.toByte(), 1.toByte())
            } else {
                byteArrayOf(chunkIndex.toByte(), totalChunks.toByte())
            }

            // Copy the header into the result array
            header.copyInto(result, currentPos)
            currentPos += header.size

            // Determine the end index of the current chunk
            val endIndex = min(currentIndex + adjustedChunkSize, data.size)
            val chunk = data.copyOfRange(currentIndex, endIndex)

            chunk.copyInto(result, currentPos)
            currentPos += chunk.size
            currentIndex = endIndex
        }

        return result
    }

    abstract class Response {}
}