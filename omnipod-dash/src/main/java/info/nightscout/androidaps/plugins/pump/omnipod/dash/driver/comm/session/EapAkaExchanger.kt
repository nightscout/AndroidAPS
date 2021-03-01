package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.session

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.pair.PairResult
import okio.ByteString.Companion.decodeHex

class EapAkaExchanger(private val msgIO: MessageIO, private val ltk: PairResult) {

    var seq = ltk.seq
    private val controllerId = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID)

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
        return EapAkaMessage(
            code = EapCode.REQUEST,
            identifier = 42, // TODO: find what value we need here
            attributes =  null,
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
            attributes =  null,
            identifier = 42, // TODO: find what value we need here
            sequenceNumber = seq,
            source = controllerId,
            destination = ltk.podId,
            payload = payload
        )
    }

    companion object {

        private val MILENAGE_OP = "cdc202d5123e20f62b6d676ac72cb318".decodeHex()
        private val MILENAGE_AMF = "b9b9".decodeHex()

    }
}