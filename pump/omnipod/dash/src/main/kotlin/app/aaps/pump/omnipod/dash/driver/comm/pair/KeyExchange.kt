package app.aaps.pump.omnipod.dash.driver.comm.pair

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.dash.driver.pod.util.RandomByteGenerator
import app.aaps.pump.omnipod.dash.driver.pod.util.X25519KeyGenerator
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.macs.CMac
import org.spongycastle.crypto.params.KeyParameter

class KeyExchange(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val x25519: X25519KeyGenerator,
    randomByteGenerator: RandomByteGenerator
) {

    val pdmNonce: ByteArray = randomByteGenerator.nextBytes(NONCE_SIZE)
    val pdmPrivate: ByteArray = x25519.generatePrivateKey()
    val pdmPublic = x25519.publicFromPrivate(pdmPrivate)

    var podPublic = ByteArray(PUBLIC_KEY_SIZE)
        private set
    var podNonce: ByteArray = ByteArray(NONCE_SIZE)

    val podConf = ByteArray(CMAC_SIZE)
    val pdmConf = ByteArray(CMAC_SIZE)

    var ltk = ByteArray(CMAC_SIZE)

    fun updatePodPublicData(payload: ByteArray) {
        if (payload.size != PUBLIC_KEY_SIZE + NONCE_SIZE) {
            throw MessageIOException("Invalid payload size")
        }
        podPublic = payload.copyOfRange(0, PUBLIC_KEY_SIZE)
        podNonce = payload.copyOfRange(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE + NONCE_SIZE)
        generateKeys()
    }

    fun validatePodConf(payload: ByteArray) {
        if (!podConf.contentEquals(payload)) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM,
                "Received invalid podConf. Expected: ${podConf.toHex()}. Got: ${payload.toHex()}"
            )
            throw MessageIOException("Invalid podConf value received")
        }
    }

    private fun generateKeys() {
        val curveLTK = x25519.computeSharedSecret(pdmPrivate, podPublic)

        val firstKey = podPublic.copyOfRange(podPublic.size - 4, podPublic.size) +
            pdmPublic.copyOfRange(pdmPublic.size - 4, pdmPublic.size) +
            podNonce.copyOfRange(podNonce.size - 4, podNonce.size) +
            pdmNonce.copyOfRange(pdmNonce.size - 4, pdmNonce.size)
        aapsLogger.debug(LTag.PUMPBTCOMM, "First key for LTK: ${firstKey.toHex()}")

        val intermediateKey = ByteArray(CMAC_SIZE)
        aesCmac(firstKey, curveLTK, intermediateKey)

        val ltkData = byteArrayOf(2.toByte()) +
            INTERMEDIARY_KEY_MAGIC_STRING +
            podNonce +
            pdmNonce +
            byteArrayOf(0.toByte(), 1.toByte())
        aesCmac(intermediateKey, ltkData, ltk)

        val confData = byteArrayOf(1.toByte()) +
            INTERMEDIARY_KEY_MAGIC_STRING +
            podNonce +
            pdmNonce +
            byteArrayOf(0.toByte(), 1.toByte())
        val confKey = ByteArray(CMAC_SIZE)
        aesCmac(intermediateKey, confData, confKey)

        val pdmConfData = PDM_CONF_MAGIC_PREFIX +
            pdmNonce +
            podNonce
        aesCmac(confKey, pdmConfData, pdmConf)

        val podConfData = POD_CONF_MAGIC_PREFIX +
            podNonce +
            pdmNonce
        aesCmac(confKey, podConfData, podConf)

        if (config.DEBUG) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "pdmPrivate: ${pdmPrivate.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "pdmPublic: ${pdmPublic.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "podPublic: ${podPublic.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "pdmNonce: ${pdmNonce.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "podNonce: ${podNonce.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "podConf: ${podConf.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "pdmConf: ${pdmConf.toHex()}")

            aapsLogger.debug(LTag.PUMPBTCOMM, "LTK, donna key: ${curveLTK.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "Intermediate key: ${intermediateKey.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "LTK: ${ltk.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "Conf KEY: ${confKey.toHex()}")
        }
    }

    companion object {

        private const val PUBLIC_KEY_SIZE = 32
        private const val NONCE_SIZE = 16

        const val CMAC_SIZE = 16

        private val INTERMEDIARY_KEY_MAGIC_STRING = "TWIt".toByteArray()
        private val PDM_CONF_MAGIC_PREFIX = "KC_2_U".toByteArray()
        private val POD_CONF_MAGIC_PREFIX = "KC_2_V".toByteArray()
    }
}

private fun aesCmac(key: ByteArray, data: ByteArray, result: ByteArray) {
    val aesEngine = AESEngine()
    val mac = CMac(aesEngine)
    mac.init(KeyParameter(key))
    mac.update(data, 0, data.size)
    mac.doFinal(result, 0)
}
