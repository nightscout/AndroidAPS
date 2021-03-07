package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.extensions.toHex
import org.spongycastle.util.encoders.Hex
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class Milenage(
    private val aapsLogger: AAPSLogger,
    private val k: ByteArray,
    val sqn: ByteArray,
    private val randParam: ByteArray? = null
) {

    init {
        require(k.size == KEY_SIZE) { "Milenage key has to be $KEY_SIZE bytes long. Received: ${k.toHex()}" }
        require(sqn.size == SQN) { "Milenage SQN has to be $SQN long. Received: ${sqn.toHex()}" }
    }

    private val secretKeySpec = SecretKeySpec(k, "AES")
    private val cipher: Cipher = Cipher.getInstance("AES/ECB/NoPadding")

    init {
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
    }

    val rand = randParam ?: ByteArray(KEY_SIZE)

    init {
        if (randParam == null) {
            val random = SecureRandom()
            random.nextBytes(rand)
        }
    }

    private val opc = cipher.doFinal(MILENAGE_OP) xor MILENAGE_OP
    private val randOpcEncrypted = cipher.doFinal(rand xor opc)
    private val randOpcEncryptedXorOpc = randOpcEncrypted xor opc
    private val resAkInput = randOpcEncryptedXorOpc.copyOfRange(0, KEY_SIZE)

    init {
        resAkInput[15] = (resAkInput[15].toInt() xor 1).toByte()
    }

    private val resAk = cipher.doFinal(resAkInput) xor opc

    val res = resAk.copyOfRange(8, 16)
    private val ak = resAk.copyOfRange(0, 6)

    private val ckInput = ByteArray(KEY_SIZE)

    init {
        for (i in 0..15) {
            ckInput[(i + 12) % 16] = randOpcEncryptedXorOpc[i]
        }
        ckInput[15] = (ckInput[15].toInt() xor 2).toByte()
    }

    val ck = cipher.doFinal(ckInput) xor opc

    private val sqnAmf = sqn + MILENAGE_AMF + sqn + MILENAGE_AMF
    private val sqnAmfXorOpc = sqnAmf xor opc
    private val macAInput = ByteArray(KEY_SIZE)

    init {
        for (i in 0..15) {
            macAInput[(i + 8) % 16] = sqnAmfXorOpc[i]
        }
    }

    private val macAFull = cipher.doFinal(macAInput xor randOpcEncrypted) xor opc
    private val macA = macAFull.copyOfRange(0, 8)
    val autn = (ak xor sqn) + MILENAGE_AMF + macA

    init {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage K: ${k.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage RAND: ${rand.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage SQN: ${sqn.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage CK: ${ck.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage AUTN: ${autn.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage RES: ${res.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage AK: ${ak.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage OPC: ${opc.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage MacA: ${macA.toHex()}")
    }

    companion object {

        private val MILENAGE_OP = Hex.decode("cdc202d5123e20f62b6d676ac72cb318")
        private val MILENAGE_AMF = Hex.decode("b9b9")
        private const val KEY_SIZE = 16
        private const val SQN = 6
    }
}

private infix fun ByteArray.xor(other: ByteArray): ByteArray {
    val out = ByteArray(size)
    for (i in indices) out[i] = (this[i].toInt() xor other[i].toInt()).toByte()
    return out
}
