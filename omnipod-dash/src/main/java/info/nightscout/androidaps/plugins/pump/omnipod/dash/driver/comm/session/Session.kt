package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt.EnDecrypt
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.*
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding.Companion.parseKeys
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.command.base.Command
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.NakResponse
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.Response
import info.nightscout.androidaps.utils.extensions.toHex

sealed class CommandSendResult
object CommandSendSuccess : CommandSendResult()
data class CommandSendErrorSending(val msg: String) : CommandSendResult()

// This error marks the undefined state
data class CommandSendErrorConfirming(val msg: String) : CommandSendResult()

sealed class CommandReceiveResult
data class CommandReceiveSuccess(val result: Response) : CommandReceiveResult()
data class CommandReceiveError(val msg: String) : CommandReceiveResult()
data class CommandAckError(val result: Response, val msg: String) : CommandReceiveResult()

class Session(
    private val aapsLogger: AAPSLogger,
    private val msgIO: MessageIO,
    private val myId: Id,
    private val podId: Id,
    val sessionKeys: SessionKeys,
    val enDecrypt: EnDecrypt
) {

    fun sendCommand(cmd: Command): CommandSendResult {
        sessionKeys.msgSequenceNumber++
        aapsLogger.debug(LTag.PUMPBTCOMM, "Sending command: ${cmd.encoded.toHex()} in packet $cmd")
        var tries = 0
        val msg = getCmdMessage(cmd)
        var possiblyUnconfirmedCommand = false
        for (i in 0..MAX_TRIES) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Sending command(wrapped): ${msg.payload.toHex()}")

            when (val sendResult = msgIO.sendMessage(msg)) {
                is MessageSendSuccess ->
                    return CommandSendSuccess

                is MessageSendErrorConfirming -> {
                    possiblyUnconfirmedCommand = true
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Error confirming command: $sendResult")
                }

                is MessageSendErrorSending ->
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Error sending command: $sendResult")
            }
        }

        val errMsg = "Maximum number of tries reached. Could not send command\""
        return if (possiblyUnconfirmedCommand)
            CommandSendErrorConfirming(errMsg)
        else
            CommandSendErrorSending(errMsg)
    }

    fun readAndAckCommandResponse(): CommandReceiveResult {
        var responseMsgPacket: MessagePacket? = null
        for (i in 0..MAX_TRIES) {
            val responseMsg = msgIO.receiveMessage()
            if (responseMsg !is MessageReceiveSuccess) {
                aapsLogger.debug(LTag.PUMPBTCOMM, "Error receiving response: $responseMsg")
                continue
            }
            responseMsgPacket = responseMsg.msg
        }
        if (responseMsgPacket == null) {
            return CommandReceiveError("Could not read response")
        }

        val decrypted = enDecrypt.decrypt(responseMsgPacket)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Received response: $decrypted")
        val response = parseResponse(decrypted)

        sessionKeys.msgSequenceNumber++
        val ack = getAck(responseMsgPacket)
        aapsLogger.debug(LTag.PUMPBTCOMM, "Sending ACK: ${ack.payload.toHex()} in packet $ack")
        val sendResult = msgIO.sendMessage(ack)
        if (sendResult !is MessageSendSuccess) {
            return CommandAckError(response, "Could not ACK the response: $sendResult")
        }
        return CommandReceiveSuccess(response)
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
