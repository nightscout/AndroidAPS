package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.PairResult
import info.nightscout.androidaps.utils.extensions.toHex
import org.spongycastle.util.encoders.Hex
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class EapAkaExchanger(private val aapsLogger: AAPSLogger, private val msgIO: MessageIO, private val ltk: PairResult) {

    var seq = ltk.seq

    private val controllerIV = ByteArray(IV_SIZE)
    private var nodeIV = ByteArray(IV_SIZE)

    private val controllerId = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID)
    private val sqn = byteArrayOf(0, 0, 0, 0, 0, 1)
    private val milenage = Milenage(aapsLogger, ltk.ltk, sqn)

    init {
        aapsLogger.debug(LTag.PUMPBTCOMM, "Starting EAP-AKA")
        val random = SecureRandom()
        random.nextBytes(controllerIV)
    }

    fun negotiateSessionKeys(): SessionKeys {
        // send EAP-AKA challenge
        seq++
        var challenge = eapAkaChallenge()
        msgIO.sendMesssage(challenge.messagePacket)

        // read SPS1
        val challengeResponse = msgIO.receiveMessage()
        processChallengeResponse(challengeResponse)
        // now we have all the data to generate: confPod, confPdm, ltk and noncePrefix
        // TODO: what do we have to answer if challenge response does not validate?

        seq++
        var success = eapSuccess()
        msgIO.sendMesssage(success.messagePacket)

        return SessionKeys()
    }

    private fun eapAkaChallenge(): EapAkaMessage {
        val payload = ByteArray(0)
        val attributes =
            arrayOf(
                // TODO: verify the order or attributes
                EapAkaAttributeRand(milenage.rand),
                EapAkaAttributeAutn(milenage.autn),
                EapAkaAttributeCustomIV(controllerIV),
            )

        return EapAkaMessage(
            code = EapCode.REQUEST,
            identifier = 42, // TODO: find what value we need here
            attributes = attributes,
            sequenceNumber = seq,
            source = controllerId,
            destination = ltk.podId,
            payload = payload
        )
    }

    private fun processChallengeResponse(challengeResponse: MessagePacket) {

    }

    private fun eapSuccess(): EapAkaMessage {
        val payload = ByteArray(0)
        return EapAkaMessage(
            code = EapCode.SUCCESS,
            attributes = null,
            identifier = 42, // TODO: find what value we need here
            sequenceNumber = seq,
            source = controllerId,
            destination = ltk.podId,
            payload = payload
        )
    }


    companion object {

        private val MILENAGE_OP = Hex.decode("cdc202d5123e20f62b6d676ac72cb318")
        private val MILENAGE_AMF = Hex.decode("b9b9")
        private const val KEY_SIZE = 16 // 128 bits
        private const val IV_SIZE = 4
    }
}

