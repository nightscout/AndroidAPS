package info.nightscout.core.utils

import com.google.common.truth.TruthJUnit.assume
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

// https://stackoverflow.com/questions/52344522/joseexception-couldnt-create-aes-gcm-nopadding-cipher-illegal-key-size
// https://stackoverflow.com/questions/47708951/can-aes-256-work-on-android-devices-with-api-level-26
// Java prior to Oracle Java 8u161 does not have policy for 256 bit AES - but Android support it
// when test is run in Vanilla JVM without policy - Invalid key size exception is thrown
private fun assumeAES256isSupported(cryptoUtil: CryptoUtil) {
    cryptoUtil.lastException?.message?.let { exceptionMessage ->
        assume().withMessage("Upgrade your testing environment Java (OpenJDK or Java 8u161) and JAVA_HOME - AES 256 is supported by Android so this exception should not happen!")
            .that(exceptionMessage).doesNotContain("key size")
    }
}

@Suppress("SpellCheckingInspection")
class CryptoUtilTest : TestBase() {

    private var cryptoUtil: CryptoUtil = CryptoUtil(aapsLogger)

    @Test
    fun testFixedSaltCrypto() {
        val salt = byteArrayOf(
            -33, -29, 16, -19, 99, -111, -3, 2, 116, 106, 47, 38, -54, 11, -77, 28,
            111, -15, -65, -110, 4, -32, -29, -70, -95, -88, -53, 19, 87, -103, 123, -15
        )

        val password = "thisIsFixedPassword"
        val payload = "FIXED-PAYLOAD"

        val encrypted = cryptoUtil.encrypt(password, salt, payload)
        assumeAES256isSupported(cryptoUtil)
        Assertions.assertNotNull(encrypted)

        val decrypted = cryptoUtil.decrypt(password, salt, encrypted!!)
        assumeAES256isSupported(cryptoUtil)
        Assertions.assertEquals(decrypted, payload)
    }

    @Test
    fun testStandardCrypto() {
        val salt = cryptoUtil.mineSalt()

        val password = "topSikret"
        val payload = "{what:payloadYouWantToProtect}"

        val encrypted = cryptoUtil.encrypt(password, salt, payload)
        assumeAES256isSupported(cryptoUtil)
        Assertions.assertNotNull(encrypted)

        val decrypted = cryptoUtil.decrypt(password, salt, encrypted!!)
        assumeAES256isSupported(cryptoUtil)
        Assertions.assertEquals(decrypted, payload)
    }

    @Test
    fun testHashVector() {
        val payload = "{what:payloadYouWantToProtect}"
        val hash = cryptoUtil.sha256(payload)
        Assertions.assertEquals(hash, "a1aafe3ed6cc127e6d102ddbc40a205147230e9cfd178daf108c83543bbdcd13")
    }

    @Test
    fun testHmac() {
        val payload = "{what:payloadYouWantToProtect}"
        val password = "topSikret"
        val expectedHmac = "ea2213953d0f2e55047cae2d23fb4f0de1b805d55e6271efa70d6b85fb692bea" // generated using other HMAC tool
        val hash = cryptoUtil.hmac256(payload, password)
        Assertions.assertEquals(hash, expectedHmac)
    }

    @Test
    fun testPlainPasswordCheck() {
        Assertions.assertTrue(cryptoUtil.checkPassword("same", "same"))
        Assertions.assertFalse(cryptoUtil.checkPassword("same", "other"))
    }

    @Test
    fun testHashedPasswordCheck() {
        Assertions.assertTrue(cryptoUtil.checkPassword("givenSecret", cryptoUtil.hashPassword("givenSecret")))
        Assertions.assertFalse(cryptoUtil.checkPassword("givenSecret", cryptoUtil.hashPassword("otherSecret")))

        Assertions.assertTrue(
            cryptoUtil.checkPassword(
                "givenHashToCheck",
                "hmac:7fe5f9c7b4b97c5d32d5cfad9d07473543a9938dc07af48a46dbbb49f4f68c12:a0c7cee14312bbe31b51359a67f0d2dfdf46813f319180269796f1f617a64be1"
            )
        )
        Assertions.assertFalse(
            cryptoUtil.checkPassword(
                "givenMashToCheck",
                "hmac:7fe5f9c7b4b97c5d32d5cfad9d07473543a9938dc07af48a46dbbb49f4f68c12:a0c7cee14312bbe31b51359a67f0d2dfdf46813f319180269796f1f617a64be1"
            )
        )
        Assertions.assertFalse(
            cryptoUtil.checkPassword(
                "givenHashToCheck",
                "hmac:0fe5f9c7b4b97c5d32d5cfad9d07473543a9938dc07af48a46dbbb49f4f68c12:a0c7cee14312bbe31b51359a67f0d2dfdf46813f319180269796f1f617a64be1"
            )
        )
        Assertions.assertFalse(
            cryptoUtil.checkPassword(
                "givenHashToCheck",
                "hmac:7fe5f9c7b4b97c5d32d5cfad9d07473543a9938dc07af48a46dbbb49f4f68c12:b0c7cee14312bbe31b51359a67f0d2dfdf46813f319180269796f1f617a64be1"
            )
        )
    }

}

