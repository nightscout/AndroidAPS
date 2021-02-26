package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.ltk

import com.google.crypto.tink.subtle.X25519
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.OmnipodDashBleManagerImpl
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.Id
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessageIO
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.StringLengthPrefixEncoding
import info.nightscout.androidaps.utils.extensions.hexStringToByteArray
import java.security.SecureRandom

internal class LTKExchanger(private val aapsLogger: AAPSLogger, private val msgIO: MessageIO) {
    private val privateKey = X25519.generatePrivateKey()
    private val nonce = ByteArray(16)
    private val controllerId = Id.fromInt(OmnipodDashBleManagerImpl.CONTROLLER_ID)
    val nodeId = controllerId.increment()
    private var seq: Byte = 1

    init{
        val random = SecureRandom()
        random.nextBytes(nonce)
    }

    fun negotiateLTKAndNonce(): LTK? {
        // send SP1, SP2
        var sp1sp2 = sp1sp2(nodeId.address, sp2())
        msgIO.sendMesssage(sp1sp2.messagePacket)

        seq++
        var sps1 = sps1()
        msgIO.sendMesssage(sps1.messagePacket)
        // send SPS1

        // read SPS1
        val podSps1 = msgIO.receiveMessage()
        aapsLogger.info(LTag.PUMPBTCOMM, "Received message: %s", podSps1)
/*
        // send SPS2
        var sps2 = PairMessage()
        msgIO.sendMesssage(sps2.messagePacket)
        // read SPS2
        val podSps2 = msgIO.receiveMessage()

        // send SP0GP0
        msgIO.sendMesssage(sps2.messagePacket)
        // read P0
        val p0 = msgIO.receiveMessage()
*/
        return null
    }

    private fun sp2(): ByteArray {
        // This is GetPodStatus command, with page 0 parameter.
        // We could replace that in the future with the serialized GetPodStatus()
        return GET_POD_STATUS_HEX_COMMAND.hexStringToByteArray()
    }

    fun sp1sp2(sp1: ByteArray, sp2: ByteArray): PairMessage {
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf("SP1=", ",SP2="),
            arrayOf(sp1, sp2),
        )
        return PairMessage(
            sequenceNumber = seq,
            source = controllerId,
            destination = nodeId,
            payload = payload,
        )
    }

    fun sps1(): PairMessage {
        val publicKey = X25519.publicFromPrivate(privateKey)
        val payload = StringLengthPrefixEncoding.formatKeys(
            arrayOf("SPS1="),
            arrayOf(publicKey+nonce),
        )
        return PairMessage(
            sequenceNumber = seq,
            source = controllerId,
            destination = nodeId,
            payload = payload,
        )
    }

    companion object {

        private val GET_POD_STATUS_HEX_COMMAND = "ffc32dbd08030e0100008a" // TODO for now we are assuming this command is build out of constant parameters, use a proper command builder for that.
    }
}