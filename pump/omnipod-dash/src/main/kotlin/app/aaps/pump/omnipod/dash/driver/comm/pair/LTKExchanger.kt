package app.aaps.pump.omnipod.dash.driver.comm.pair

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.hexStringToByteArray
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.Ids
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.PairingException
import app.aaps.pump.omnipod.dash.driver.comm.message.MessageIO
import app.aaps.pump.omnipod.dash.driver.comm.message.MessagePacket
import app.aaps.pump.omnipod.dash.driver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding.Companion.parseKeys
import app.aaps.pump.omnipod.dash.driver.pod.util.RandomByteGenerator
import app.aaps.pump.omnipod.dash.driver.pod.util.X25519KeyGenerator

internal class LTKExchanger(
    private val aapsLogger: AAPSLogger,
    config: Config,
    private val msgIO: MessageIO,
    private val ids: Ids,
) {

    private val podAddress = Ids.notActivated()
    private val keyExchange = KeyExchange(aapsLogger, config, X25519KeyGenerator(), RandomByteGenerator())
    private var seq: Byte = 1

    @Throws(PairingException::class)
    fun negotiateLTK(): PairResult {
        val sp1sp2 = PairMessage(
            sequenceNumber = seq,
            source = ids.myId,
            destination = podAddress,
            keys = arrayOf(SP1, SP2),
            payloads = arrayOf(ids.podId.address, sp2())
        )
        throwOnSendError(sp1sp2.messagePacket, SP1 + SP2)

        seq++
        val sps1 = PairMessage(
            sequenceNumber = seq,
            source = ids.myId,
            destination = podAddress,
            keys = arrayOf(SPS1),
            payloads = arrayOf(keyExchange.pdmPublic + keyExchange.pdmNonce)
        )
        throwOnSendError(sps1.messagePacket, SPS1)

        val podSps1 = msgIO.receiveMessage() ?: throw PairingException("Could not read SPS1")
        processSps1FromPod(podSps1)
        // now we have all the data to generate: confPod, confPdm, ltk and noncePrefix

        seq++
        val sps2 = PairMessage(
            sequenceNumber = seq,
            source = ids.myId,
            destination = podAddress,
            keys = arrayOf(SPS2),
            payloads = arrayOf(keyExchange.pdmConf)
        )
        throwOnSendError(sps2.messagePacket, SPS2)

        val podSps2 = msgIO.receiveMessage() ?: throw PairingException("Could not read SPS2")
        validatePodSps2(podSps2)
        // No exception throwing after this point. It is possible that the pod saved the LTK

        seq++
        // send SP0GP0
        val sp0gp0 = PairMessage(
            sequenceNumber = seq,
            source = ids.myId,
            destination = podAddress,
            keys = arrayOf(SP0GP0),
            payloads = arrayOf(ByteArray(0))
        )
        val result = msgIO.sendMessage(sp0gp0.messagePacket)
        if (result !is MessageSendSuccess) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Error sending SP0GP0: $result")
        }

        msgIO.receiveMessage()
            ?.let { validateP0(it) }
            ?: aapsLogger.warn(LTag.PUMPBTCOMM, "Could not read P0")

        return PairResult(
            ltk = keyExchange.ltk,
            msgSeq = seq
        )
    }

    @Throws(PairingException::class)
    private fun throwOnSendError(msg: MessagePacket, msgType: String) {
        val result = msgIO.sendMessage(msg)
        if (result !is MessageSendSuccess) {
            throw PairingException("Could not send or confirm $msgType: $result")
        }
    }

    private fun processSps1FromPod(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received SPS1 from pod: ${msg.payload.toHex()}")

        val payload = parseKeys(arrayOf(SPS1), msg.payload)[0]
        keyExchange.updatePodPublicData(payload)
    }

    private fun validatePodSps2(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received SPS2 from pod: ${msg.payload.toHex()}")

        val payload = parseKeys(arrayOf(SPS2), msg.payload)[0]
        aapsLogger.debug(LTag.PUMPBTCOMM, "SPS2 payload from pod: ${payload.toHex()}")

        if (payload.size != KeyExchange.CMAC_SIZE) {
            throw MessageIOException("Invalid payload size")
        }
        keyExchange.validatePodConf(payload)
    }

    private fun sp2(): ByteArray {
        // This is GetPodStatus command, with page 0 parameter.
        // We could replace that in the future with the serialized GetPodStatus()
        return GET_POD_STATUS_HEX_COMMAND.hexStringToByteArray()
    }

    private fun validateP0(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received P0 from pod: ${msg.payload.toHex()}")

        val payload = parseKeys(arrayOf(P0), msg.payload)[0]
        aapsLogger.debug(LTag.PUMPBTCOMM, "P0 payload from pod: ${payload.toHex()}")
        if (!payload.contentEquals(UNKNOWN_P0_PAYLOAD)) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Received invalid P0 payload: ${payload.toHex()}")
        }
    }

    companion object {

        private const val GET_POD_STATUS_HEX_COMMAND =
            "ffc32dbd08030e0100008a"
        // This is the binary representation of "GetPodStatus command"

        private const val SP1 = "SP1="
        private const val SP2 = ",SP2="
        private const val SPS1 = "SPS1="
        private const val SPS2 = "SPS2="
        private const val SP0GP0 = "SP0,GP0"
        private const val P0 = "P0="
        private val UNKNOWN_P0_PAYLOAD = byteArrayOf(0xa5.toByte())
    }
}
