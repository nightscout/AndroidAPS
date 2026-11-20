package app.aaps.plugins.eversense.packets

import app.aaps.plugins.eversense.enums.EversenseSecurityType
import app.aaps.plugins.eversense.packets.e365.Eversense365Packets
import app.aaps.plugins.eversense.protocol.PacketSpec
import app.aaps.plugins.eversense.protocol.WireEncoding
import app.aaps.plugins.eversense.util.EversenseCrypto365Util
import java.io.ByteArrayOutputStream

/** Packet adapters describe only their payload and result; this engine owns wire framing. */
abstract class EversenseBasePacket(val spec: PacketSpec) {
    private val response = ByteArrayOutputStream()
    private var snapshot: UByteArray? = null
    protected val receivedData: UByteArray
        get() = snapshot ?: response.toByteArray().toUByteArray().also { snapshot = it }

    @Volatile var isErrorResponse = false
    open val skipResponseIdValidation = false
    open fun getRequestData(): ByteArray = byteArrayOf()
    abstract fun parseResponse(): Response?
    protected fun getStartIndex(): Int = spec.responseOffset

    fun appendData(data: UByteArray) {
        response.write(data.toByteArray())
        snapshot = null
    }

    fun buildRequest(cryptoUtil: EversenseCrypto365Util, payloadSize: Int): ByteArray {
        val payload = getRequestData()
        return when (spec.securityType) {
            EversenseSecurityType.None -> WireEncoding.e3(spec.requestId, payload)
            EversenseSecurityType.SecureV2 -> {
                val plain = byteArrayOf(spec.requestId, spec.typeId) + payload
                val message = if (spec.requestId == Eversense365Packets.AuthenticateCommandId) plain else cryptoUtil.encrypt(plain)
                WireEncoding.v2(message, payloadSize)
            }
        }
    }

    abstract class Response
}
