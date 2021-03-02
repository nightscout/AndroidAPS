package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions.SessionEstablishmentException
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageType
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.PairResult
import info.nightscout.androidaps.utils.extensions.toHex
import org.spongycastle.util.encoders.Hex
import java.security.SecureRandom

class EapAkaExchanger(private val aapsLogger: AAPSLogger, private val msgIO: MessageIO, private val ltk: PairResult) {

    var seq = ltk.seq

    private val controllerIV = ByteArray(IV_SIZE)
    private var nodeIV = ByteArray(IV_SIZE)

    private val controllerId = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID)
    private val sqn = byteArrayOf(0, 0, 0, 0, 0, 2)
    private val milenage = Milenage(aapsLogger, ltk.ltk, sqn)

    init {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Starting EAP-AKA")
        val random = SecureRandom()
        random.nextBytes(controllerIV)
    }

    fun negotiateSessionKeys(): SessionKeys {
        // send EAP-AKA challenge
        seq++ // TODO: get from pod state. This only works for activating a new pod
        var challenge = eapAkaChallenge()
        msgIO.sendMesssage(challenge)

        val challengeResponse = msgIO.receiveMessage()
        processChallengeResponse(challengeResponse)
        // TODO: what do we have to answer if challenge response does not validate?

        seq++
        var success = eapSuccess()
        msgIO.sendMesssage(success)

        return SessionKeys(
            milenage.ck,
            controllerIV + nodeIV,
            sqn
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
            identifier = 42, // TODO: find what value we need here, it's probably random
            attributes = attributes
        )
        return MessagePacket(
            type = MessageType.SESSION_ESTABLISHMENT,
            sequenceNumber = seq,
            source = controllerId,
            destination = ltk.podId,
            payload = eapMsg.toByteArray()
        )
    }

    private fun processChallengeResponse(challengeResponse: MessagePacket) {
        // TODO verify that identifier matches identifer from the Challenge
        val eapMsg = EapMessage.parse(aapsLogger, challengeResponse.payload)
        if (eapMsg.attributes.size != 2) {
            aapsLogger.debug(LTag.PUMPBTCOMM, "EAP-AKA: got RES message: $eapMsg")
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
            identifier = 44 // TODO: find what value we need here
        )

        return MessagePacket(
            type = MessageType.SESSION_ESTABLISHMENT,
            sequenceNumber = seq,
            source = controllerId,
            destination = ltk.podId,
            payload = eapMsg.toByteArray()
        )
    }

    companion object {

        private val MILENAGE_OP = Hex.decode("cdc202d5123e20f62b6d676ac72cb318")
        private val MILENAGE_AMF = Hex.decode("b9b9")
        private const val KEY_SIZE = 16 // 128 bits
        private const val IV_SIZE = 4
    }
}
