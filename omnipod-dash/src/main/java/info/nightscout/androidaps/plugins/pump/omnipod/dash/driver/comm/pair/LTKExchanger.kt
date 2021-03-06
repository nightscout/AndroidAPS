package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

import com.google.crypto.tink.subtle.X25519
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.BuildConfig
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding.Companion.parseKeys
import info.nightscout.androidaps.utils.extensions.hexStringToByteArray
import info.nightscout.androidaps.utils.extensions.toHex
import org.spongycastle.crypto.engines.AESEngine
import org.spongycastle.crypto.macs.CMac
import org.spongycastle.crypto.params.KeyParameter
import java.security.SecureRandom

internal class LTKExchanger(private val aapsLogger: AAPSLogger, private val msgIO: MessageIO) {

    private val pdmPrivate = X25519.generatePrivateKey()
    private val pdmPublic = X25519.publicFromPrivate(pdmPrivate)
    private var podPublic = ByteArray(PUBLIC_KEY_SIZE)
    private var podNonce = ByteArray(NONCE_SIZE)
    private val pdmNonce = ByteArray(NONCE_SIZE)
    private val pdmConf = ByteArray(CMAC_SIZE)
    private val podConf = ByteArray(CMAC_SIZE)
    private val controllerId = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID)
    val nodeId = controllerId.increment()
    private var seq: Byte = 1
    private var ltk = ByteArray(CMAC_SIZE)

    init {
        val random = SecureRandom()
        random.nextBytes(pdmNonce)
    }

    fun negotiateLTK(): PairResult {
        // send SP1, SP2
        val sp1sp2 = sp1sp2(nodeId.address, sp2())
        msgIO.sendMessage(sp1sp2.messagePacket)

        seq++
        val sps1 = sps1()
        msgIO.sendMessage(sps1.messagePacket)
        // send SPS1

        // read SPS1
        val podSps1 = msgIO.receiveMessage()
        processSps1FromPod(podSps1)
        // now we have all the data to generate: confPod, confPdm, ltk and noncePrefix
        generateKeys()
        seq++
        // send SPS2
        val sps2 = sps2()
        msgIO.sendMessage(sps2.messagePacket)
        // read SPS2

        val podSps2 = msgIO.receiveMessage()
        validatePodSps2(podSps2)

        seq++
        // send SP0GP0
        msgIO.sendMessage(sp0gp0().messagePacket)
        // read P0

        // TODO: failing to read or validate p0 will lead to undefined state
        // It could be that:
        // - the pod answered with p0 and we did not receive/could not process the answer
        // - the pod answered with some sort of error
        // But if sps2 conf value is incorrect, then we would probablysee this when receiving the pod podSps2(to test)
        val p0 = msgIO.receiveMessage()
        validateP0(p0)

        return PairResult(
            ltk = ltk,
            podId = nodeId,
            msgSeq = seq
        )
    }

    private fun sp1sp2(sp1: ByteArray, sp2: ByteArray): PairMessage {
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf(SP1, SP2),
            arrayOf(sp1, sp2)
        )
        return PairMessage(
            sequenceNumber = seq,
            source = controllerId,
            destination = nodeId,
            payload = payload
        )
    }

    private fun sps1(): PairMessage {
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf("SPS1="),
            arrayOf(pdmPublic + pdmNonce)
        )
        return PairMessage(
            sequenceNumber = seq,
            source = controllerId,
            destination = nodeId,
            payload = payload
        )
    }

    private fun processSps1FromPod(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received SPS1 from pod: ${msg.payload.toHex()}")

        val payload = parseKeys(arrayOf(SPS1), msg.payload)[0]
        if (payload.size != 48) {
            throw MessageIOException("Invalid payload size")
        }
        podPublic = payload.copyOfRange(0, PUBLIC_KEY_SIZE)
        podNonce = payload.copyOfRange(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE + NONCE_SIZE)
    }

    private fun sps2(): PairMessage {
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf(SPS2),
            arrayOf(pdmConf)
        )
        return PairMessage(
            sequenceNumber = seq,
            source = controllerId,
            destination = nodeId,
            payload = payload
        )
    }

    private fun validatePodSps2(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received SPS2 from pod: ${msg.payload.toHex()}")

        val payload = parseKeys(arrayOf(SPS2), msg.payload)[0]
        aapsLogger.debug(LTag.PUMPBTCOMM, "SPS2 payload from pod: ${payload.toHex()}")

        if (payload.size != CMAC_SIZE) {
            throw MessageIOException("Invalid payload size")
        }
        if (!podConf.contentEquals(payload)) {
            aapsLogger.warn(
                LTag.PUMPBTCOMM,
                "Received invalid podConf. Expected: ${podConf.toHex()}. Got: ${payload.toHex()}"
            )
            throw MessageIOException("Invalid podConf value received")
        }
    }

    private fun sp2(): ByteArray {
        // This is GetPodStatus command, with page 0 parameter.
        // We could replace that in the future with the serialized GetPodStatus()
        return GET_POD_STATUS_HEX_COMMAND.hexStringToByteArray()
    }

    private fun sp0gp0(): PairMessage {
        val payload = SP0GP0.toByteArray()
        return PairMessage(
            sequenceNumber = seq,
            source = controllerId,
            destination = nodeId,
            payload = payload
        )
    }

    private fun validateP0(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received P0 from pod: ${msg.payload.toHex()}")

        val payload = parseKeys(arrayOf(P0), msg.payload)[0]
        aapsLogger.debug(LTag.PUMPBTCOMM, "P0 payload from pod: ${payload.toHex()}")
        if (!payload.contentEquals(UNKNOWN_P0_PAYLOAD)) {
            throw MessageIOException("Invalid P0 payload received")
        }
    }

    fun generateKeys() {
        val curveLTK = X25519.computeSharedSecret(pdmPrivate, podPublic)

        val firstKey = podPublic.copyOfRange(podPublic.size - 4, podPublic.size) +
            pdmPublic.copyOfRange(pdmPublic.size - 4, pdmPublic.size) +
            podNonce.copyOfRange(podNonce.size - 4, podNonce.size) +
            pdmNonce.copyOfRange(pdmNonce.size - 4, pdmNonce.size)
        aapsLogger.debug(LTag.PUMPBTCOMM, "First key for LTK: ${firstKey.toHex()}")

        val intermediateKey = ByteArray(CMAC_SIZE)
        aesCmac(firstKey, curveLTK, intermediateKey)

        val ltkData = byteArrayOf(2.toByte()) +
            INTERMEDIAR_KEY_MAGIC_STRING +
            podNonce +
            pdmNonce +
            byteArrayOf(0.toByte(), 1.toByte())
        aesCmac(intermediateKey, ltkData, ltk)

        val confData = byteArrayOf(1.toByte()) +
            INTERMEDIAR_KEY_MAGIC_STRING +
            podNonce +
            pdmNonce +
            byteArrayOf(0.toByte(), 1.toByte())
        val confKey = ByteArray(CMAC_SIZE)
        aesCmac(intermediateKey, confData, confKey)

        val pdmConfData = PDM_CONF_MAGIC_PREFIX +
            pdmNonce +
            podNonce
        aesCmac(confKey, pdmConfData, pdmConf)
        aapsLogger.debug(LTag.PUMPBTCOMM, "pdmConf: ${pdmConf.toHex()}")

        val podConfData = POD_CONF_MAGIC_PREFIX +
            podNonce +
            pdmNonce
        aesCmac(confKey, podConfData, podConf)
        aapsLogger.debug(LTag.PUMPBTCOMM, "podConf: ${podConf.toHex()}")

        if (BuildConfig.DEBUG) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "pdmPrivate: ${pdmPrivate.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "pdmPublic: ${pdmPublic.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "podPublic: ${podPublic.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "pdmNonce: ${pdmNonce.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "podNonce: ${podNonce.toHex()}")

            aapsLogger.debug(LTag.PUMPBTCOMM, "LTK, donna key: ${curveLTK.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "Intermediate key: ${intermediateKey.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "LTK: ${ltk.toHex()}")
            aapsLogger.debug(LTag.PUMPBTCOMM, "Conf KEY: ${confKey.toHex()}")
        }
    }

    companion object {

        private const val PUBLIC_KEY_SIZE = 32
        private const val NONCE_SIZE = 16
        private const val CONF_SIZE = 16

        private const val CMAC_SIZE = 16

        private val INTERMEDIAR_KEY_MAGIC_STRING = "TWIt".toByteArray()
        private val PDM_CONF_MAGIC_PREFIX = "KC_2_U".toByteArray()
        private val POD_CONF_MAGIC_PREFIX = "KC_2_V".toByteArray()

        private const val GET_POD_STATUS_HEX_COMMAND =
            "ffc32dbd08030e0100008a" // TODO for now we are assuming this command is build out of constant parameters, use a proper command builder for that.

        private const val SP1 = "SP1="
        private const val SP2 = ",SP2="
        private const val SPS1 = "SPS1="
        private const val SPS2 = "SPS2="
        private const val SP0GP0 = "SP0,GP0"
        private const val P0 = "P0="
        private val UNKNOWN_P0_PAYLOAD = byteArrayOf(0xa5.toByte())
    }
}

private fun aesCmac(key: ByteArray, data: ByteArray, result: ByteArray) {
    val aesEngine = AESEngine()
    val mac = CMac(aesEngine)
    mac.init(KeyParameter(key))
    mac.update(data, 0, data.size)
    mac.doFinal(result, 0)
}
