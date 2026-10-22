package app.aaps.pump.omnipod.common.bledriver.comm.session

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.common.bledriver.comm.Ids
import app.aaps.pump.omnipod.common.bledriver.comm.endecrypt.EnDecrypt
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.CouldNotParseResponseException
import app.aaps.pump.omnipod.common.bledriver.comm.exceptions.MessageIOException
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageIO
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessagePacket
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendErrorConfirming
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendErrorSending
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendResult
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.common.bledriver.comm.message.MessageType
import app.aaps.pump.omnipod.common.bledriver.comm.message.StringLengthPrefixEncoding
import app.aaps.pump.omnipod.common.bledriver.comm.message.StringLengthPrefixEncoding.Companion.parseKeys
import app.aaps.pump.omnipod.common.bledriver.comm.pair.CommandSigner
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.Command
import app.aaps.pump.omnipod.common.bledriver.pod.command.base.CommandType
import app.aaps.pump.omnipod.common.bledriver.pod.response.AlarmStatusResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.NakResponse
import app.aaps.pump.omnipod.common.bledriver.pod.response.Response
import app.aaps.pump.omnipod.common.bledriver.pod.util.MessageUtil
import java.nio.ByteBuffer

sealed class CommandSendResult
object CommandSendSuccess : CommandSendResult()
data class CommandSendErrorSending(val msg: String) : CommandSendResult()

// This error marks the undefined state
data class CommandSendErrorConfirming(val msg: String) : CommandSendResult()

sealed class CommandReceiveResult
data class CommandReceiveSuccess(val result: Response) : CommandReceiveResult()

/**
 * The command failed, but [result] is non-null when the pod still sent a well-formed reply
 * saying so - a NAK, or an alarm-status response reporting a fault.
 *
 * Carrying it matters: a fault arrives *as* a failed command, so discarding the response
 * discards the only notice that the pod has faulted. Callers should still report the command
 * as failed, but record the pod state the reply carries first (see
 * `O5BleManagerImpl.sendCommand`).
 */
data class CommandReceiveError(val msg: String, val result: Response? = null) : CommandReceiveResult()
data class CommandAckError(val result: Response, val msg: String) : CommandReceiveResult()

class Session(
    private val aapsLogger: AAPSLogger,
    private val msgIO: MessageIO,
    private val ids: Ids,
    val sessionKeys: SessionKeys,
    val enDecrypt: EnDecrypt,
    /** Non-null only for O5 connections (see [app.aaps.pump.omnipod.common.bledriver.comm
     *  .legacy.session.O5Connection.establishSession]) - Dash has no certificate/ECDSA
     *  pairing infrastructure and never signs commands, so its `Connection
     *  .establishSession` leaves this at its default null. */
    private val commandSigner: CommandSigner? = null
) {

    /** The 4-bit command-header sequence number (see [app.aaps.pump.omnipod.common
     *  .bledriver.pod.command.base.HeaderEnabledCommand.encodeHeader]) of the last command
     *  sent - the response envelope must echo this back (see [parseResponse]'s O5-only
     *  validation). Distinct from [SessionKeys.msgSequenceNumber], the outer BLE
     *  MessagePacket-level sequence number this class manages separately. */
    private var lastSentCommandSequenceNumber: Short? = null

    fun sendCommand(cmd: Command): CommandSendResult {
        sessionKeys.msgSequenceNumber++
        lastSentCommandSequenceNumber = cmd.sequenceNumber
        aapsLogger.debug(LTag.PUMPBTCOMM, "Sending command: ${cmd.encoded.toHex()} in packet $cmd")

        val msg = getCmdMessage(cmd)
        for (i in 0..MAX_TRIES) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "Sending command(wrapped): ${msg.payload.toHex()}")

            when (val sendResult = msgIO.sendMessage(msg)) {
                is MessageSendSuccess         ->
                    return CommandSendSuccess

                is MessageSendErrorConfirming -> {
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Error confirming command: $sendResult")
                    return CommandSendErrorConfirming(sendResult.msg)
                }

                is MessageSendErrorSending    ->
                    aapsLogger.debug(LTag.PUMPBTCOMM, "Error sending command: $sendResult")
            }
        }

        val errMsg = "Maximum number of tries reached. Could not send command"
        return CommandSendErrorSending(errMsg)
    }

    @Suppress("ReturnCount")
    fun readAndAckResponse(): CommandReceiveResult {
        var responseMsgPacket: MessagePacket? = null
        for (i in 0..MAX_TRIES) {
            val responseMsg = msgIO.receiveMessage()
            if (responseMsg != null) {
                responseMsgPacket = responseMsg
                break
            }
            aapsLogger.debug(LTag.PUMPBTCOMM, "Error receiving response: $responseMsg")
        }

        responseMsgPacket
            ?: return CommandReceiveError("Could not read response")

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
        if (response is NakResponse || response is AlarmStatusResponse) {
            return CommandReceiveError("Pod rejected command or reported a fault: $response", response)
        }
        return CommandReceiveSuccess(response)
    }

    @Throws(CouldNotParseResponseException::class, UnsupportedOperationException::class)
    private fun parseResponse(decrypted: MessagePacket): Response {

        val data = parseKeys(arrayOf(RESPONSE_PREFIX), decrypted.payload)[0]
        aapsLogger.info(LTag.PUMPBTCOMM, "Received decrypted response: ${data.toHex()} in packet: $decrypted")

        if (data.size < RESPONSE_ENVELOPE_MIN_SIZE) {
            aapsLogger.warn(LTag.PUMPBTCOMM, "Response envelope shorter than expected (${data.size} bytes): ${data.toHex()}")
        } else {
            val uniqueId = data.copyOfRange(0, 4)
            val lengthAndSequenceNumber = data.copyOfRange(4, 6)
            val crc = data.copyOfRange(data.size - 2, data.size)
            aapsLogger.debug(
                LTag.PUMPBTCOMM,
                "Response envelope fields: uniqueId=${uniqueId.toHex()}, lengthAndSequenceNumber=${lengthAndSequenceNumber.toHex()}, " +
                    "crc=${crc.toHex()}"
            )
            if (commandSigner != null) {
                validateCrc(data)
                lastSentCommandSequenceNumber?.let { validateSequenceNumber(lengthAndSequenceNumber, it) }
            }
        }
        val payload = data.copyOfRange(6, data.size - 2)

        return ResponseUtil.parseResponse(payload)
    }

    /**
     * Checks the response envelope's trailing CRC, which an Omnipod 5 pod computes over
     * everything preceding it with the same [MessageUtil.createCrc] this driver already uses
     * for outgoing messages.
     *
     * Rejecting a mismatch matters more than it looks: a badly mangled response would fail to
     * parse anyway (`ResponseUtil.parseResponse` throws on an unrecognized type), so the case
     * this actually catches is a *subtly* corrupted status response that still parses - wrong
     * insulin-delivered counters, reservoir level, or delivery-status bits silently entering
     * pod state and IOB. Throwing here routes the command into the existing undefined-state
     * path ([CommandSendErrorConfirming]) instead of confirming it with corrupt data.
     *
     * O5-only, and that restriction is load-bearing. Pinned against captured traffic from two
     * pods of each generation:
     * - Omnipod 5 (`002a1c6e`, from a Trio installation that hit a real transient corruption
     *   incident): 495 of 495 pod-generated responses satisfy this CRC, as do all 518 outgoing
     *   messages - 1013 of 1013 overall.
     * - Dash (`1749dbcb`, `podTypeValue: 4` in a Loop Report): outgoing messages satisfy it,
     *   pod responses never do. No initial value in the whole 16-bit space and no byte range
     *   reproduces them, which is why this must not run on a Dash connection.
     *
     * Community confirmation of the same split, from the Omnipod 5 testing discussion: "The
     * DASH pods do not check the CRC... The Eros and Omnipod 5 pods both do apply the CRC
     * check before accepting a command."
     */
    @Throws(CouldNotParseResponseException::class)
    internal fun validateCrc(data: ByteArray) {
        val actual = ByteBuffer.wrap(data, data.size - 2, 2).short.toInt() and 0xffff
        val expected = MessageUtil.createCrc(data.copyOfRange(0, data.size - 2)).toInt() and 0xffff
        if (actual != expected) {
            throw CouldNotParseResponseException(
                "Response CRC mismatch: expected %04x, got %04x".format(expected, actual)
            )
        }
    }

    /**
     * Checks that the pod echoed back the sequence number this protocol expects for a
     * response: **the request's sequence number plus one**, wrapped to 4 bits - not the
     * request's own number. OmnipodKit does the same, explicitly, in
     * BleMessageTransport.sendMessage(): it sets `messageNumber = message.sequenceNum` and
     * then calls `incrementMessageNumber()` with the comment "bump to match expected
     * Omnipod message # in response", before readAndAckResponse() compares against it.
     *
     * Confirmed against real Omnipod 5 traffic (a working Loop/Trio installation's Device
     * Communication Log): every request/response pair shows response == request + 1, e.g.
     * requests with sequence 1/2/3/14 drew responses with sequence 2/3/4/15. An earlier
     * revision of this method compared against the request's own number and so would have
     * rejected every response the pod ever sent.
     *
     * [lengthAndSequenceNumber] packs the number the same way
     * [app.aaps.pump.omnipod.common.bledriver.pod.command.base.HeaderEnabledCommand
     * .encodeHeader] does for outgoing commands - bits 13-10 of the big-endian short
     * (bit 15 = multi-command flag, bits 9-0 = body length).
     *
     * Internal (rather than private) to allow unit testing within this module against
     * captured byte arrays, without simulating a full encrypted round trip.
     */
    @Throws(CouldNotParseResponseException::class)
    internal fun validateSequenceNumber(lengthAndSequenceNumber: ByteArray, sentSequenceNumber: Short) {
        val packed = ByteBuffer.wrap(lengthAndSequenceNumber).short.toInt()
        val actual = (packed shr 10) and 0x0f
        val expected = (sentSequenceNumber.toInt() + 1) and 0x0f
        if (actual != expected) {
            throw CouldNotParseResponseException(
                "Response sequence number mismatch: expected $expected (sent ${sentSequenceNumber.toInt() and 0x0f} + 1), got $actual"
            )
        }
    }

    private fun getAck(response: MessagePacket): MessagePacket {
        val msg = MessagePacket(
            type = MessageType.ENCRYPTED,
            sequenceNumber = sessionKeys.msgSequenceNumber,
            source = ids.myId,
            destination = ids.podId,
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

        val needsSigning = commandSigner != null && cmd.commandType in SIGNED_COMMAND_TYPES
        val msg = MessagePacket(
            type = if (needsSigning) MessageType.ENCRYPTED_SIGNED else MessageType.ENCRYPTED,
            sequenceNumber = sessionKeys.msgSequenceNumber,
            source = ids.myId,
            destination = ids.podId,
            payload = wrapped,
            eqos = 1
        )

        val encrypted = enDecrypt.encrypt(msg)
        return if (needsSigning) sign(encrypted) else encrypted
    }

    /**
     * Appends a raw (r||s, 64-byte) P-256 ECDSA signature to an already AES-CCM-encrypted
     * O5 command - confirmed against OmnipodKit's BleMessageTransport.swift getCmdMessage():
     * real O5 pod firmware rejects insulin-schedule/deactivate/cancel-delivery commands
     * (this codebase's [CommandType.PROGRAM_BASAL]/[CommandType.PROGRAM_TEMP_BASAL]/
     * [CommandType.PROGRAM_BOLUS]/[CommandType.PROGRAM_INSULIN]/[CommandType.DEACTIVATE]/
     * [CommandType.STOP_DELIVERY] - the first four always embed a
     * [app.aaps.pump.omnipod.common.bledriver.pod.command.ProgramInsulinCommand] interlock
     * block, which is why the Swift original's block-list check treats them the same as a
     * standalone [CommandType.PROGRAM_INSULIN]) without this signature, even though every
     * other command type is accepted unsigned. The signed byte range is the 16-byte AAD
     * header plus the ciphertext+tag - i.e. everything [enDecrypt]'s AES-CCM authenticates,
     * signed on top for an extra pod-side check that the command really came from the
     * paired controller identity. Matches [EnDecrypt.decrypt]'s own AAD extraction
     * (`asByteArray().copyOfRange(0, 16)`), so a receiver reconstructing this input from the
     * wire bytes gets byte-identical input to what was signed here.
     */
    private fun sign(encrypted: MessagePacket): MessagePacket {
        val aad = encrypted.asByteArray(forEncryption = false).copyOfRange(0, 16)
        val signature = requireNotNull(commandSigner) { "sign() called without a commandSigner" }
            .signRaw(aad + encrypted.payload)
        return encrypted.copy(signatureData = signature)
    }

    /**
     * Sends a raw O5 "AID setup" command payload (see [app.aaps.pump.omnipod.common
     * .bledriver.pod.command.aid.O5AidSetupCommands]) and returns the decrypted response
     * with [expectedResponsePrefix] stripped. Distinct from [sendCommand]/
     * [readAndAckResponse]: AID setup commands use a plain ASCII `key=value` wire format,
     * not the `"S0.0=...,G0.0"` SLPE envelope [getCmdMessage] builds for ordinary
     * [Command]s, and their responses aren't the binary [app.aaps.pump.omnipod.common
     * .bledriver.pod.response.ResponseUtil] envelope either - just an ASCII prefix
     * followed by raw bytes. Always sent as plain [MessageType.ENCRYPTED], never signed -
     * confirmed against OmnipodKit's own sendO5AidCommand(), which never takes the
     * Type-4-signing path (only the commands [sign] handles do). Throws on any protocol
     * failure rather than returning a result type, matching the reference's
     * o5SendAidSetupCommands(): any failure here must abort pod activation outright rather
     * than silently continuing with a pod that never received this data.
     */
    @Suppress("ThrowsCount")
    fun sendAidSetupCommand(payload: ByteArray, expectedResponsePrefix: String): ByteArray {
        sessionKeys.msgSequenceNumber++
        val msg = MessagePacket(
            type = MessageType.ENCRYPTED,
            sequenceNumber = sessionKeys.msgSequenceNumber,
            source = ids.myId,
            destination = ids.podId,
            payload = payload,
            eqos = 1
        )
        val encrypted = enDecrypt.encrypt(msg)

        var sendResult: MessageSendResult = MessageSendErrorSending("AID setup command not sent")
        for (i in 0..MAX_TRIES) {
            sendResult = msgIO.sendMessage(encrypted)
            if (sendResult is MessageSendSuccess) break
        }
        if (sendResult !is MessageSendSuccess) {
            throw MessageIOException("Could not send AID setup command: $sendResult")
        }

        var responseMsgPacket: MessagePacket? = null
        for (i in 0..MAX_TRIES) {
            responseMsgPacket = msgIO.receiveMessage()
            if (responseMsgPacket != null) break
        }
        val received = responseMsgPacket ?: throw MessageIOException("Could not read AID setup response")

        val decrypted = enDecrypt.decrypt(received)
        sessionKeys.msgSequenceNumber++
        val ack = getAck(received)
        val ackResult = msgIO.sendMessage(ack)
        if (ackResult !is MessageSendSuccess) {
            throw MessageIOException("Could not ACK AID setup response: $ackResult")
        }

        val prefixBytes = expectedResponsePrefix.toByteArray(Charsets.US_ASCII)
        if (decrypted.payload.size < prefixBytes.size ||
            !decrypted.payload.copyOfRange(0, prefixBytes.size).contentEquals(prefixBytes)
        ) {
            throw MessageIOException(
                "AID setup response missing expected prefix '$expectedResponsePrefix': ${decrypted.payload.toHex()}"
            )
        }
        return decrypted.payload.copyOfRange(prefixBytes.size, decrypted.payload.size)
    }

    companion object {

        private const val COMMAND_PREFIX = "S0.0="
        private const val COMMAND_SUFFIX = ",G0.0"
        private const val RESPONSE_PREFIX = "0.0="

        /** 4-byte uniqueId + 2-byte length/sequence + 2-byte CRC surrounding the payload. */
        private const val RESPONSE_ENVELOPE_MIN_SIZE = 8

        private const val MAX_TRIES = 4

        /**
         * O5-only command types that real pod firmware rejects unless sent as
         * [MessageType.ENCRYPTED_SIGNED] - see [sign]'s doc comment for why
         * [CommandType.PROGRAM_BASAL]/[CommandType.PROGRAM_TEMP_BASAL]/
         * [CommandType.PROGRAM_BOLUS] are included alongside the standalone
         * [CommandType.PROGRAM_INSULIN]/[CommandType.DEACTIVATE]/[CommandType.STOP_DELIVERY]
         * types the Swift reference's own block-list check names directly.
         */
        private val SIGNED_COMMAND_TYPES = setOf(
            CommandType.PROGRAM_BASAL,
            CommandType.PROGRAM_TEMP_BASAL,
            CommandType.PROGRAM_BOLUS,
            CommandType.PROGRAM_INSULIN,
            CommandType.DEACTIVATE,
            CommandType.STOP_DELIVERY
        )
    }
}
