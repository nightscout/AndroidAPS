package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt.EnDecrypt
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding.Companion.parseKeys
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.NakResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response
import info.nightscout.androidaps.utils.extensions.toHex
import java.util.concurrent.TimeoutException

class Session(
    private val aapsLogger: AAPSLogger,
    private val msgIO: MessageIO,
    private val myId: Id,
    private val podId: Id,
    val sessionKeys: SessionKeys,
    val enDecrypt: EnDecrypt
) {

    /**
     * Used for commands:
     *  -> command with retries
     *  <- response, ACK TODO: retries?
     *  -> ACK
     */
    fun sendCommand(cmd: Command): Response {
        sessionKeys.msgSequenceNumber++
        aapsLogger.debug(LTag.PUMPBTCOMM, "Sending command: ${cmd.encoded.toHex()} in packet $cmd")
        var tries = 0
        var certainFailure = true
        for (i in 0..MAX_TRIES) {
            try {
                val msg = getCmdMessage(cmd)
                aapsLogger.debug(LTag.PUMPBTCOMM, "Sending command(wrapped): ${msg.payload.toHex()}")
                msgIO.sendMessage(msg)
            } catch (e: TimeoutException) {
                aapsLogger.info(LTag.PUMPBTCOMM,"Exception trying to send command: $e. Try: $i/$MAX_TRIES")
            } // TODO filter out certain vs uncertain errors
        }
        certainFailure = false
        var response: Response?= null
        for (i in 0..MAX_TRIES) {
            try {
                val responseMsg = msgIO.receiveMessage()
                val decrypted = enDecrypt.decrypt(responseMsg)
                aapsLogger.debug(LTag.PUMPBTCOMM, "Received response: $decrypted")
                response = parseResponse(decrypted)
                sessionKeys.msgSequenceNumber++
                val ack = getAck(responseMsg)
                aapsLogger.debug(LTag.PUMPBTCOMM, "Sending ACK: ${ack.payload.toHex()} in packet $ack")
                msgIO.sendMessage(ack)
            } catch (e: TimeoutException) {
                aapsLogger.info(LTag.PUMPBTCOMM,"Exception trying to send command: $e. Try: $i/$MAX_TRIES")
            }
        }
        response?.let{
            return it
        }
        if (certainFailure) {
            throw CertainFailureException("Could not send command")
        }
        throw UncertainFailureException("Possible failure to send commnd")
    }

    private fun parseResponse(decrypted: MessagePacket): Response {

        val payload = parseKeys(arrayOf(RESPONSE_PREFIX), decrypted.payload)[0]
        aapsLogger.info(LTag.PUMPBTCOMM, "Received decrypted response: ${payload.toHex()} in packet: $decrypted")
        return NakResponse(payload)
    }

    private fun getAck(response: MessagePacket): MessagePacket {
        val msg = MessagePacket(
            type = MessageType.ENCRYPTED,
            sequenceNumber = sessionKeys.msgSequenceNumber,
            source = myId,
            destination = podId,
            payload = ByteArray(0),
            eqos = 0,
            ack = true,
            ackNumber = response.sequenceNumber.inc()
        )
        return enDecrypt.encrypt((msg))
    }

    private fun getCmdMessage(cmd: Command): MessagePacket {
        val wrapped = StringLengthPrefixEncoding.formatKeys(
            arrayOf(COMMAND_PREFIX, COMMAND_SUFFIX),
            arrayOf(cmd.encoded, ByteArray(0))
        )

        aapsLogger.debug(LTag.PUMPBTCOMM, "Sending command: ${wrapped.toHex()}")

        val msg = MessagePacket(
            type = MessageType.ENCRYPTED,
            sequenceNumber = sessionKeys.msgSequenceNumber,
            source = myId,
            destination = podId,
            payload = wrapped,
            eqos = 1
        )

        return enDecrypt.encrypt(msg)
    }

    companion object {

        private const val COMMAND_PREFIX = "S0.0="
        private const val COMMAND_SUFFIX = ",G0.0"
        private const val RESPONSE_PREFIX = "0.0="

        private const val MAX_TRIES = 4
    }
}
