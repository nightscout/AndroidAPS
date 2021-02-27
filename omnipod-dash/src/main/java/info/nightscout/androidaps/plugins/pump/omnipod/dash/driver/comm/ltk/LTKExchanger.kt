package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ltk

import com.google.crypto.tink.subtle.X25519
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding
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
    private val confPdm = ByteArray(CONF_SIZE)
    private val confPod = ByteArray(CONF_SIZE)

    private val controllerId = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID)
    val nodeId = controllerId.increment()
    private var seq: Byte = 1
    private var ltk = ByteArray(CMAC_SIZE)
    private var noncePrefix = ByteArray(0)

    init {
        val random = SecureRandom()
        random.nextBytes(pdmNonce)
    }

    fun negotiateLTKAndNonce(): LTK? {
        // send SP1, SP2
        var sp1sp2 = sp1sp2(nodeId.address, sp2())
        msgIO.sendMesssage(sp1sp2.messagePacket)

        seq++
        var sps1 = sps1()
        msgIO.sendMesssage(sps1.messagePacket)
        // send SPS1

        // read SPS1
        val podSps1 = msgIO.receiveMessage()
        aapsLogger.info(LTag.PUMPBTCOMM, "Received message: %s", podSps1)
        processSps1FromPod(podSps1)
        // now we have all the data to generate: confPod, confPdm, ltk and noncePrefix
        generateKeys()
        seq++
        // send SPS2
        val sps2 = sps2()
        msgIO.sendMesssage(sps2.messagePacket)
        // read SPS2

        val podSps2 = msgIO.receiveMessage()
        validatePodSps2(podSps2)

        seq++
        // send SP0GP0
        msgIO.sendMesssage(sp0gp0().messagePacket)
        // read P0

        //TODO: if we fail to read or validate p0 will lead to undefined state
        // it could be that:
        // - the pod answered with p0 and we did not receive/could not process the answer
        // - the pod answered with some sort of error
        val p0 = msgIO.receiveMessage()
        validateP0(p0)

        return LTK(
            ltk = ltk,
            noncePrefix = noncePrefix,
        )
    }

    private fun sp1sp2(sp1: ByteArray, sp2: ByteArray): PairMessage {
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf("SP1=", ",SP2="),
            arrayOf(sp1, sp2),
        )
        return PairMessage(
            sequenceNumber = seq,
            source = controllerId,
            destination = nodeId,
            payload = payload,
        )
    }

    private fun sps1(): PairMessage {
        val publicKey = X25519.publicFromPrivate(pdmPrivate)
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf("SPS1="),
            arrayOf(publicKey + pdmNonce),
        )
        return PairMessage(
            sequenceNumber = seq,
            source = controllerId,
            destination = nodeId,
            payload = payload,
        )
    }

    private fun processSps1FromPod(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received SPS1 from pod: ${msg.payload.toHex()}")
        if (msg.payload.size != 48) {
            throw MessageIOException("Invalid payload size")
        }
        podPublic = msg.payload.copyOfRange(0, PUBLIC_KEY_SIZE)
        podNonce = msg.payload.copyOfRange(PUBLIC_KEY_SIZE, PUBLIC_KEY_SIZE + NONCE_SIZE)
    }

    private fun sps2(): PairMessage {
        TODO("implement")
    }

    private fun validatePodSps2(podSps2: MessagePacket) {
        TODO("implement")

    }

    private fun sp2(): ByteArray {
        // This is GetPodStatus command, with page 0 parameter.
        // We could replace that in the future with the serialized GetPodStatus()
        return GET_POD_STATUS_HEX_COMMAND.hexStringToByteArray()
    }

    private fun sp0gp0(): PairMessage {
        TODO("implement")
    }

    private fun validateP0(p0: MessagePacket) {
        TODO("implement")

    }

    fun generateKeys() {
        val curveLTK = X25519.computeSharedSecret(pdmPrivate, podPublic)
        aapsLogger.debug(LTag.PUMPBTCOMM, "LTK, donna key: ${curveLTK.toHex()}")

        //first_key = data.pod_public[-4:] + data.pdm_public[-4:] + data.pod_nonce[-4:] + data.pdm_nonce[-4:]
        val firstKey = podPublic.copyOfRange(podPublic.size - 4, podPublic.size) +
            pdmPublic.copyOfRange(pdmPublic.size - 4, pdmPublic.size)+
            podNonce.copyOfRange(podNonce.size - 4, podNonce.size)+
            pdmNonce.copyOfRange(pdmNonce.size - 4, pdmNonce.size)
        aapsLogger.debug(LTag.PUMPBTCOMM, "LTK, first key: ${firstKey.toHex()}")

        val aesEngine = AESEngine()
        val intermediateMac = CMac(aesEngine)
        intermediateMac.init(KeyParameter(firstKey))
        intermediateMac.update(curveLTK, 0, curveLTK.size)
        val intermediateKey = ByteArray(CMAC_SIZE)
        intermediateMac.doFinal(intermediateKey, 0)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Intermediate key: ${intermediateKey.toHex()}")

        val ltkMac = CMac(aesEngine)
        ltkMac.init(KeyParameter(firstKey))
        ltkMac.update(curveLTK, 0, curveLTK.size)
        intermediateMac.doFinal(ltk, 0)
        aapsLogger.debug(LTag.PUMPBTCOMM, "LTK: ${ltk.toHex()}")

    }

    companion object {

        private val PUBLIC_KEY_SIZE = 32
        private val NONCE_SIZE = 16
        private val CONF_SIZE = 16
        private val CMAC_SIZE = 16
        private val GET_POD_STATUS_HEX_COMMAND = "ffc32dbd08030e0100008a" // TODO for now we are assuming this command is build out of constant parameters, use a proper command builder for that.
    }
}