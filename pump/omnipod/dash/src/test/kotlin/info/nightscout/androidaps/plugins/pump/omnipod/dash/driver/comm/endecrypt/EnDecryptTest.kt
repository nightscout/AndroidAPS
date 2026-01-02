package app.aaps.pump.omnipod.dash.driver.comm.endecrypt

import app.aaps.core.utils.toHex
import app.aaps.pump.omnipod.dash.driver.comm.message.MessagePacket
import app.aaps.shared.tests.AAPSLoggerTest
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.spongycastle.util.encoders.Hex

@Suppress("SpellCheckingInspection") class EnDecryptTest {

    @Test
    fun decrypt() {
        val received =
            "54,57,11,a1,0c,16,03,00,08,20,2e,a9,08,20,2e,a8,34,7c,b9,7b,38,5d,45,a3,c4,0e,40,4c,55,71,5e,f3,c3,86,50,17,36,7e,62,3c,7d,0b,46,9e,81,cd,fd,9a".replace(
                ",",
                ""
            )
        val decryptedPayload =
            "30,2e,30,3d,00,12,08,20,2e,a9,1c,0a,1d,05,00,16,b0,00,00,00,0b,ff,01,fe".replace(",", "")
        val aapsLogger = AAPSLoggerTest()
        val enDecrypt = EnDecrypt(
            aapsLogger,
            Nonce(
                Hex.decode("6c,ff,5d,18,b7,61,6c,ae".replace(",", "")),
                22
            ),
            Hex.decode("55,79,9f,d2,66,64,cb,f6,e4,76,52,5e,2d,ee,52,c6".replace(",", ""))
        )
        val encryptedMessage = Hex.decode(received)
        val decrypted = Hex.decode(decryptedPayload)
        val msg = MessagePacket.parse(encryptedMessage)
        val decryptedMsg = enDecrypt.decrypt(msg)

        assertThat(decryptedMsg.payload.toHex()).isEqualTo(decrypted.toHex())
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
            Hex.decode("ba1283744b6de9fab6d9b77d95a71d6e")
        )
        val encryptedMessage = Hex.decode(
            "54571101070003400242000002420001" +
                "e09158bcb0285a81bf30635f3a17ee73f0afbb3286bc524a8a66" +
                "fb1bc5b001e56543"
        )
        val command = Hex.decode("53302e303d000effffffff00060704ffffffff82b22c47302e30")
        val msg = MessagePacket.parse(encryptedMessage).copy(payload = command) // copy for the headers

        val encrypted = enDecrypt.encrypt(msg)

        assertThat(encrypted.asByteArray().toHex()).isEqualTo(encryptedMessage.toHex())
    }
}
