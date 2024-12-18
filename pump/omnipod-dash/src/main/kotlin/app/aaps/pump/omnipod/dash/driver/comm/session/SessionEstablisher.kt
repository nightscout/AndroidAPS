package app.aaps.pump.omnipod.dash.driver.comm.session

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.Ids
import app.aaps.pump.omnipod.dash.driver.comm.endecrypt.Nonce
import app.aaps.pump.omnipod.dash.driver.comm.exceptions.SessionEstablishmentException
import app.aaps.pump.omnipod.dash.driver.comm.message.MessageIO
import app.aaps.pump.omnipod.dash.driver.comm.message.MessagePacket
import app.aaps.pump.omnipod.dash.driver.comm.message.MessageSendSuccess
import app.aaps.pump.omnipod.dash.driver.comm.message.MessageType
import java.security.SecureRandom

class SessionEstablisher(
    private val aapsLogger: AAPSLogger,
    private val config: Config,
    private val msgIO: MessageIO,
    private val ltk: ByteArray,
    private val eapSqn: ByteArray,
    private val ids: Ids,
    private var msgSeq: Byte
) {

    private val controllerIV = ByteArray(IV_SIZE)
    private var nodeIV = ByteArray(IV_SIZE)
    private val identifier = SecureRandom().nextInt().toByte()
    private val milenage = Milenage(aapsLogger, config, ltk, eapSqn)

    init {
        require(eapSqn.size == 6) { "EAP-SQN has to be 6 bytes long" }
        require(ltk.size == 16) { "LTK has to be 16 bytes long" }

        aapsLogger.debug(LTag.PUMPBTCOMM, "Starting EAP-AKA")
        val random = SecureRandom()
        random.nextBytes(controllerIV)
    }

    fun negotiateSessionKeys(): SessionNegotiationResponse {
        msgSeq++
        val challenge = eapAkaChallenge()
        val sendResult = msgIO.sendMessage(challenge)
        if (sendResult !is MessageSendSuccess) {
            throw SessionEstablishmentException("Could not send the EAP AKA challenge: $sendResult")
        }
        val challengeResponse = msgIO.receiveMessage()
            ?: throw SessionEstablishmentException("Could not establish session")

        val newSqn = processChallengeResponse(challengeResponse)
        if (newSqn != null) {
            return SessionNegotiationResynchronization(
                synchronizedEapSqn = newSqn,
                msgSequenceNumber = msgSeq
            )
        }

        msgSeq++
        val success = eapSuccess()
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
            identifier = identifier,
            attributes = attributes
        )
        return MessagePacket(
            type = MessageType.SESSION_ESTABLISHMENT,
            sequenceNumber = msgSeq,
            source = ids.myId,
            destination = ids.podId,
            payload = eapMsg.toByteArray()
        )
    }

    private fun assertIdentifier(msg: EapMessage) {
        if (msg.identifier != identifier) {
            aapsLogger.debug(
                LTag.PUMPBTCOMM,
                "EAP-AKA: got incorrect identifier ${msg.identifier} expected: $identifier"
            )
            throw SessionEstablishmentException("Received incorrect EAP identifier: ${msg.identifier}")
        }
    }

    private fun processChallengeResponse(challengeResponse: MessagePacket): EapSqn? {
        val eapMsg = EapMessage.parse(aapsLogger, challengeResponse.payload)

        assertIdentifier(eapMsg)

        val eapSqn = isResynchronization(eapMsg)
        if (eapSqn != null) {
            return eapSqn
        }

        assertValidAkaMessage(eapMsg)

        for (attr in eapMsg.attributes) {
            when (attr) {
                is EapAkaAttributeRes      ->
                    if (!milenage.res.contentEquals(attr.payload)) {
                        throw SessionEstablishmentException(
                            "RES mismatch." +
                                "Expected: ${milenage.res.toHex()}." +
                                "Actual: ${attr.payload.toHex()}."
                        )
                    }

                is EapAkaAttributeCustomIV ->
                    nodeIV = attr.payload.copyOfRange(0, IV_SIZE)

                else                       ->
                    throw SessionEstablishmentException("Unknown attribute received: $attr")
            }
        }
        return null
    }

    private fun assertValidAkaMessage(eapMsg: EapMessage) {
        if (eapMsg.attributes.size != 2) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "EAP-AKA: got incorrect: $eapMsg")
            if (eapMsg.attributes.size == 1 && eapMsg.attributes[0] is EapAkaAttributeClientErrorCode) {
                throw SessionEstablishmentException(
                    "Received CLIENT_ERROR_CODE for EAP-AKA challenge: ${
                        eapMsg.attributes[0].toByteArray().toHex()
                    }"
                )
            }
            throw SessionEstablishmentException("Expecting two attributes, got: ${eapMsg.attributes.size}")
        }
    }

    private fun isResynchronization(eapMsg: EapMessage): EapSqn? {
        if (eapMsg.subType != EapMessage.SUBTYPE_SYNCHRONIZATION_FAILURE ||
            eapMsg.attributes.size != 1 ||
            eapMsg.attributes[0] !is EapAkaAttributeAuts
        )
            return null

        val auts = eapMsg.attributes[0] as EapAkaAttributeAuts
        val autsMilenage = Milenage(
            aapsLogger = aapsLogger,
            config = config,
            k = ltk,
            sqn = eapSqn,
            randParam = milenage.rand,
            auts = auts.payload
        )

        val newSqnMilenage = Milenage(
            aapsLogger = aapsLogger,
            config = config,
            k = ltk,
            sqn = autsMilenage.synchronizationSqn,
            randParam = milenage.rand,
            auts = auts.payload,
            amf = Milenage.RESYNC_AMF,
        )

        if (!newSqnMilenage.macS.contentEquals(newSqnMilenage.receivedMacS)) {
            throw SessionEstablishmentException(
                "MacS mismatch. " +
                    "Expected: ${newSqnMilenage.macS.toHex()}. " +
                    "Received: ${newSqnMilenage.receivedMacS.toHex()}"
            )
        }
        return EapSqn(autsMilenage.synchronizationSqn)
    }

    private fun eapSuccess(): MessagePacket {
        val eapMsg = EapMessage(
            code = EapCode.SUCCESS,
            attributes = arrayOf(),
            identifier = identifier
        )

        return MessagePacket(
            type = MessageType.SESSION_ESTABLISHMENT,
            sequenceNumber = msgSeq,
            source = ids.myId,
            destination = ids.podId,
            payload = eapMsg.toByteArray()
        )
    }

    companion object {

        private const val IV_SIZE = 4
    }
}
