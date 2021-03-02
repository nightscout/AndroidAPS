package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.utils.extensions.toHex
import org.spongycastle.util.encoders.Hex
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class Milenage(private val aapsLogger: AAPSLogger, private val k: ByteArray, val sqn: ByteArray, val _rand: ByteArray? = null) {
    init {
        require(k.size == KEY_SIZE) { "Milenage key has to be $KEY_SIZE bytes long. Received: ${k.toHex()}" }
        require(sqn.size == SEQ_SIZE) { "Milenage SEQ has to be $SEQ_SIZE long. Received: ${sqn.toHex()}" }
    }

    private val secretKeySpec = SecretKeySpec(k, "AES")
    private val cipher: Cipher = Cipher.getInstance("AES/ECB/NoPadding")

    init {
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
    }

    val rand = _rand ?: ByteArray(KEY_SIZE)

    init {
        if (_rand == null ){
            val random = SecureRandom()
            random.nextBytes(rand)
        }
    }

    private val opc = cipher.doFinal(MILENAGE_OP) xor MILENAGE_OP
    private val rand_opc_encrypted = cipher.doFinal(rand xor opc)
    private val rand_opc_encrypted_opc = rand_opc_encrypted xor opc

    init {
        rand_opc_encrypted_opc[15] = (rand_opc_encrypted_opc[15].toInt() xor 1).toByte()
    }

    private val res_ak = cipher.doFinal(rand_opc_encrypted_opc) xor opc

    val res = res_ak.copyOfRange(8, 16)
    val ak = res_ak.copyOfRange(0, 6)

    private val ck_input = ByteArray(KEY_SIZE)

    init {
        for (i in 0..15) {
            ck_input[(i + 12) % 16] = (rand_opc_encrypted[i].toInt() xor opc[i].toInt()).toByte()
        }
        ck_input[15] = (ck_input[15].toInt() xor 2).toByte()
    }

    val ck = cipher.doFinal(ck_input) xor opc

    private val macAInput = ByteArray(KEY_SIZE)

    init {
        for (i in 0..15) {
            macAInput[(i + 8) % 16] = (rand_opc_encrypted[i].toInt() xor opc[i].toInt()).toByte()
        }
    }

    private val macAFull = cipher.doFinal(macAInput xor opc)
    private val macA = macAFull.copyOfRange(0, 8)
    val autn = (ak xor sqn) + MILENAGE_AMF + macA

    init {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage K: ${k.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage RAND: ${rand.toHex()}")
        aapsLogger.debug(LTag.PUMPBTCOMM, "Milenage SEQ: ${sqn.toHex()}")
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
        private const val SEQ_SIZE = 6
    }
}

private infix fun ByteArray.xor(other: ByteArray): ByteArray {
    val out = ByteArray(size)
    for (i in indices) out[i] = (this[i].toInt() xor other[i].toInt()).toByte()
    return out
}
