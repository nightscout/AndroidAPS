package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.endecrypt

import info.nightscout.androidaps.logging.AAPSLoggerTest
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.message.MessagePacket
import info.nightscout.androidaps.utils.extensions.toHex
import org.junit.Assert
import org.junit.Test
import org.spongycastle.util.encoders.Hex

class EnDecryptTest {

    @Test
    fun decrypt() {
        val aapsLogger = AAPSLoggerTest()
        val enDecrypt = EnDecrypt(
            aapsLogger,
            Nonce(
                Hex.decode("dda23c090a0a0a0a"),
                0
            ),
            Hex.decode("ba1283744b6de9fab6d9b77d95a71d6e"),
        )
        val encryptedMessage = Hex.decode(
            "54571101070003400242000002420001" +
                "e09158bcb0285a81bf30635f3a17ee73f0afbb3286bc524a8a66" +
                "fb1bc5b001e56543"
        )
        val decrypted = Hex.decode("53302e303d000effffffff00060704ffffffff82b22c47302e30")
        val msg = MessagePacket.parse(encryptedMessage)
        val decryptedMsg = enDecrypt.decrypt(msg)

        Assert.assertEquals(decrypted.toHex(), decryptedMsg.payload.toHex())
    }

    @Test
    fun encrypt() {
        val aapsLogger = AAPSLoggerTest()
        val enDecrypt = EnDecrypt(
            aapsLogger,
            Nonce(
                Hex.decode("dda23c090a0a0a0a"),
                0
            ),
            Hex.decode("ba1283744b6de9fab6d9b77d95a71d6e"),
        )
        val encryptedMessage = Hex.decode(
            "54571101070003400242000002420001" +
                "e09158bcb0285a81bf30635f3a17ee73f0afbb3286bc524a8a66" +
                "fb1bc5b001e56543"
        )
        val command = Hex.decode("53302e303d000effffffff00060704ffffffff82b22c47302e30")
        val msg = MessagePacket.parse(encryptedMessage).copy(payload = command) // copy for the headers

        val encrypted = enDecrypt.encrypt(msg)

        Assert.assertEquals(encryptedMessage.toHex(), encrypted.asByteArray().toHex())
    }
}
