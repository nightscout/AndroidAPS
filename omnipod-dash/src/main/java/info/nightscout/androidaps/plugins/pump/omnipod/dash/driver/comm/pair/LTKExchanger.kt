package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.MessageIOException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.PairingException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageSendErrorSending
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageSendSuccess
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding.Companion.parseKeys
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.RandomByteGenerator
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util.X25519KeyGenerator
import info.nightscout.androidaps.utils.extensions.hexStringToByteArray
import info.nightscout.androidaps.utils.extensions.toHex

internal class LTKExchanger(
    private val aapsLogger: AAPSLogger,
    private val msgIO: MessageIO,
    val myId: Id,
    val podId: Id,
    val podAddress: Id
) {

    private val keyExchange = KeyExchange(aapsLogger, X25519KeyGenerator(), RandomByteGenerator())
    private var seq: Byte = 1

    fun negotiateLTK(): PairResult {
        val sp1sp2 = sp1sp2(podId.address, sp2())
        val sendSp1Sp2Result = msgIO.sendMessage(sp1sp2.messagePacket)
        if (sendSp1Sp2Result !is MessageSendSuccess) {
            throw PairingException("Could not send SP1SP2: $sendSp1Sp2Result")
        }

        seq++
        val sps1 = sps1()
        val sp1Result = msgIO.sendMessage(sps1.messagePacket)
        if (sp1Result !is MessageSendSuccess) {
            throw PairingException("Could not send SP1: $sp1Result")
        }

        val podSps1 = msgIO.receiveMessage() ?: throw PairingException("Could not read SPS1")
        processSps1FromPod(podSps1)
        // now we have all the data to generate: confPod, confPdm, ltk and noncePrefix

        seq++
        val sps2 = sps2()
        val sp2Result = msgIO.sendMessage(sps2.messagePacket)
        if (sp2Result !is MessageSendSuccess) {
            throw PairingException("Could not send sps2: $sp2Result")
        }

        val podSps2 = msgIO.receiveMessage() ?: throw PairingException("Could not read SPS2")
        validatePodSps2(podSps2)

        seq++
        // send SP0GP0
        val sp0gp0Result = msgIO.sendMessage(sp0gp0().messagePacket)
        if (sp0gp0Result is MessageSendErrorSending) {
            throw PairingException("Could not send SP0GP0: $sp0gp0Result")
        }

        // No exception throwing after this point. It is possible that the pod saved the LTK
        msgIO.receiveMessage()
            ?.let { validateP0(it) }
            ?: aapsLogger.warn(LTag.PUMPBTCOMM, "Could not read P0")

        return PairResult(
            ltk = keyExchange.ltk,
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
            source = myId,
            destination = podAddress,
            payload = payload
        )
    }

    private fun sps1(): PairMessage {
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf("SPS1="),
            arrayOf(keyExchange.pdmPublic + keyExchange.pdmNonce)
        )
        return PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            payload = payload
        )
    }

    private fun processSps1FromPod(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received SPS1 from pod: ${msg.payload.toHex()}")

        val payload = parseKeys(arrayOf(SPS1), msg.payload)[0]
        keyExchange.updatePodPublicData(payload)
    }

    private fun sps2(): PairMessage {
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf(SPS2),
            arrayOf(keyExchange.pdmConf)
        )
        return PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            payload = payload
        )
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

    private fun sp0gp0(): PairMessage {
        val payload = SP0GP0.toByteArray()
        return PairMessage(
            sequenceNumber = seq,
            source = myId,
            destination = podAddress,
            payload = payload
        )
    }

    private fun validateP0(msg: MessagePacket) {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received P0 from pod: ${msg.payload.toHex()}")

        val payload = parseKeys(arrayOf(P0), msg.payload)[0]
        aapsLogger.debug(LTag.PUMPBTCOMM, "P0 payload from pod: ${payload.toHex()}")
        if (!payload.contentEquals(UNKNOWN_P0_PAYLOAD)) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Reveived invalid P0 payload: ${payload.toHex()}")
        }
    }

    companion object {

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
