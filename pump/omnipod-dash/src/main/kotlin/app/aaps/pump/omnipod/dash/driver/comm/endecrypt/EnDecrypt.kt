package app.aaps.pump.omnipod.dash.driver.comm.endecrypt

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.message.MessagePacket
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.modes.CCMBlockCipher
import org.spongycastle.crypto.params.AEADParameters
import org.spongycastle.crypto.params.KeyParameter

class EnDecrypt(private val aapsLogger: AAPSLogger, private val nonce: Nonce, private val ck: ByteArray) {

    val engine = AESEngine()
    val cipher = CCMBlockCipher(engine)

    fun decrypt(msg: MessagePacket): MessagePacket {
        val payload = msg.payload
        val header = msg.asByteArray().copyOfRange(0, 16)

        val n = nonce.increment(false)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Decrypt header ${header.toHex()} payload: ${payload.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Decrypt NONCE ${n.toHex()}")
        cipher.init(
            false,
            AEADParameters(
                KeyParameter(ck),
                MAC_SIZE * 8, // in bits
                n,
                header
            )
        )
        val decryptedPayload = ByteArray(payload.size - MAC_SIZE)
        cipher.processPacket(payload, 0, payload.size, decryptedPayload, 0)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Decrypted payload ${decryptedPayload.toHex()}")
        return msg.copy(payload = decryptedPayload)
    }

    fun encrypt(headerMessage: MessagePacket): MessagePacket {
        val payload = headerMessage.payload
        val header = headerMessage.asByteArray(true).copyOfRange(0, 16)

        val n = nonce.increment(true)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Encrypt header ${header.toHex()} payload: ${payload.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Encrypt NONCE ${n.toHex()}")
        val encryptedPayload = ByteArray(payload.size + MAC_SIZE)

        cipher.init(
            true,
            AEADParameters(
                KeyParameter(ck),
                MAC_SIZE * 8, // in bits
                n,
                header
            )
        )
        cipher.processPacket(payload, 0, payload.size, encryptedPayload, 0)

        return headerMessage.copy(payload = encryptedPayload)
    }

    companion object {

        private const val MAC_SIZE = 8
    }
}
