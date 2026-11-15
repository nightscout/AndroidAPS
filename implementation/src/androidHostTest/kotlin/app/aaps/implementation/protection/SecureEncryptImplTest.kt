package app.aaps.implementation.protection

import app.aaps.core.objects.crypto.CryptoUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SecureEncryptImplTest : TestBase() {

    private val cryptoUtil = CryptoUtil(aapsLogger)
    private val sut = SecureEncryptImpl(aapsLogger, cryptoUtil)

    private val testData = "My secret data"
    private val secretData = "6072f90e56e607f8e54fa5f434d149b5e84b2813ff13be1691f9819f5d04a7f9:UnattendedExportAlias:17fafca82f1c8e58b4b78eb6:335573ff96c60dc7b1766b49c0c0fd57276d2f"

    /***
    Test general interface only (as the Android KeyStore is not available or mocked)
     */

    @Test
    fun encrypt() {
        // Partial test: KeyStore is not available on test platform
        // Expect secret wil contain empty password
        val testSecret = sut.encrypt(plaintextSecret = testData, keystoreAlias = "TestAlias")
        assertThat(testSecret).isNotEmpty()
        assertThat(sut.isValidDataString(testSecret)).isTrue()
    }

    @Test
    fun decrypt() {
        // Partial test: KeyStore is not available on test platform
        // Expect secret is not empty and contains empty password
        val testSecret = sut.encrypt(plaintextSecret = testData, keystoreAlias = "TestAlias")
        assertThat(testSecret).isNotEmpty()
        assertThat(sut.isValidDataString(testSecret)).isTrue()
        val result = sut.decrypt(encryptedSecret = testSecret)
        assertThat(result).isEqualTo("")
    }

    //@org.junit.jupiter.api.Test
    @Test fun isValidDataString() {
        // Expect test/sample string has valid data
        assertThat(sut.isValidDataString(secretData)).isTrue()
    }
}