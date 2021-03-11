package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt.Nonce
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.SessionEstablishmentException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageType
import info.nightscout.androidaps.utils.extensions.toHex
import java.security.SecureRandom

class SessionEstablisher(
    private val aapsLogger: AAPSLogger,
    private val msgIO: MessageIO,
    ltk: ByteArray,
    eapSqn: ByteArray,
    private val myId: Id,
    private val podId: Id,
    private var msgSeq: Byte
) {

    private val controllerIV = ByteArray(IV_SIZE)
    private var nodeIV = ByteArray(IV_SIZE)

    private val milenage = Milenage(aapsLogger, ltk, eapSqn)

    init {
        require(eapSqn.size == 6) { "EAP-SQN has to be 6 bytes long" }
        require(ltk.size == 16) { "LTK has to be 16 bytes long" }

        aapsLogger.debug(LTag.PUMPBTCOMM, "Starting EAP-AKA")
        val random = SecureRandom()
        random.nextBytes(controllerIV)
    }

    fun negotiateSessionKeys(): SessionKeys {
        // send EAP-AKA challenge
        msgSeq++ // TODO: get from pod state. This only works for activating a new pod
        var challenge = eapAkaChallenge()
        msgIO.sendMessage(challenge)

        val challengeResponse = msgIO.receiveMessage()
        processChallengeResponse(challengeResponse) // TODO: what do we have to answer if challenge response does not validate?

        msgSeq++
        var success = eapSuccess()
        msgIO.sendMessage(success)

        return SessionKeys(
            ck = milenage.ck,
            nonce = Nonce(
                prefix = controllerIV + nodeIV,
                sqn = 0
            ),
            msgSequenceNumber = msgSeq
        )
    }

    private fun eapAkaChallenge(): MessagePacket {
        val attributes = arrayOf(
            EapAkaAttributeAutn(milenage.autn),
            EapAkaAttributeRand(milenage.rand),
            EapAkaAttributeCustomIV(controllerIV)
        )

        val eapMsg = EapMessage(
            code = EapCode.REQUEST,
            identifier = 189.toByte(), // TODO: find what value we need here, it's probably random
            attributes = attributes
        )
        return MessagePacket(
            type = MessageType.SESSION_ESTABLISHMENT,
            sequenceNumber = msgSeq,
            source = myId,
            destination = podId,
            payload = eapMsg.toByteArray()
        )
    }

    private fun processChallengeResponse(challengeResponse: MessagePacket) {
        // TODO verify that identifier matches identifer from the Challenge
        val eapMsg = EapMessage.parse(aapsLogger, challengeResponse.payload)
        if (eapMsg.attributes.size != 2) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "EAP-AKA: got message: $eapMsg")
            if (eapMsg.attributes.size == 1 && eapMsg.attributes[0] is EapAkaAttributeClientErrorCode) {
                // TODO: special exception for this
                throw SessionEstablishmentException("Received CLIENT_ERROR_CODE for EAP-AKA challenge: ${eapMsg.attributes[0].toByteArray().toHex()}")
            }
            throw SessionEstablishmentException("Expecting two attributes, got: ${eapMsg.attributes.size}")
        }
        for (attr in eapMsg.attributes) {
            when (attr) {
                is EapAkaAttributeRes ->
                    if (!milenage.res.contentEquals(attr.payload)) {
                        throw SessionEstablishmentException("RES missmatch. Expected: ${milenage.res.toHex()} Actual: ${attr.payload.toHex()} ")
                    }
                is EapAkaAttributeCustomIV ->
                    nodeIV = attr.payload.copyOfRange(0, IV_SIZE)
                else ->
                    throw SessionEstablishmentException("Unknown attribute received: $attr")
            }
        }
    }

    private fun eapSuccess(): MessagePacket {
        val eapMsg = EapMessage(
            code = EapCode.SUCCESS,
            attributes = arrayOf(),
            identifier = 189.toByte() // TODO: find what value we need here
        )

        return MessagePacket(
            type = MessageType.SESSION_ESTABLISHMENT,
            sequenceNumber = msgSeq,
            source = myId,
            destination = podId,
            payload = eapMsg.toByteArray()
        )
    }

    companion object {

        private const val IV_SIZE = 4
    }
}
