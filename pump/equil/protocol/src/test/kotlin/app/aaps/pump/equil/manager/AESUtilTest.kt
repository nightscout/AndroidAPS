package app.aaps.pump.equil.manager

import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AESUtilTest : TestBase() {

    @Test
    fun `getEquilPassWord should return 32 byte key`() {
        val password = "testpassword"
        val result = AESUtil.getEquilPassWord(password)
        assertEquals(32, result.size)
    }

    @Test
    fun `getEquilPassWord should generate consistent keys for same password`() {
        val password = "mypassword123"
        val result1 = AESUtil.getEquilPassWord(password)
        val result2 = AESUtil.getEquilPassWord(password)
        assertArrayEquals(result1, result2)
    }

    @Test
    fun `getEquilPassWord should generate different keys for different passwords`() {
        val password1 = "password1"
        val password2 = "password2"
        val result1 = AESUtil.getEquilPassWord(password1)
        val result2 = AESUtil.getEquilPassWord(password2)
        // Keys should be different
        assertNotEquals(Utils.bytesToHex(result1), Utils.bytesToHex(result2))
    }

    @Test
    fun `generateRandomIV should create IV of correct length`() {
        val iv = AESUtil.generateRandomIV(12)
        assertEquals(12, iv.size)
    }

    @Test
    fun `generateRandomIV should create unique IVs`() {
        val iv1 = AESUtil.generateRandomIV(12)
        val iv2 = AESUtil.generateRandomIV(12)
        // Two random IVs should be different (extremely unlikely to be same)
        assertNotEquals(Utils.bytesToHex(iv1), Utils.bytesToHex(iv2))
    }

    @Test
    fun `aesEncrypt should encrypt data successfully`() {
        val password = AESUtil.getEquilPassWord("testpassword")
        val data = "Hello World".toByteArray()
        val result = AESUtil.aesEncrypt(password, data)

        assertNotNull(result.iv)
        assertNotNull(result.tag)
        assertNotNull(result.ciphertext)
        assertEquals(24, result.iv!!.length) // 12 bytes * 2 hex chars
        assertEquals(32, result.tag!!.length) // 16 bytes * 2 hex chars
    }

    @Test
    fun `aesEncrypt should produce different ciphertext for same data with different passwords`() {
        val password1 = AESUtil.getEquilPassWord("password1")
        val password2 = AESUtil.getEquilPassWord("password2")
        val data = "Test Data".toByteArray()

        val result1 = AESUtil.aesEncrypt(password1, data)
        val result2 = AESUtil.aesEncrypt(password2, data)

        assertNotEquals(result1.ciphertext, result2.ciphertext)
    }

    @Test
    fun `aesEncrypt should produce different ciphertext for same data due to random IV`() {
        val password = AESUtil.getEquilPassWord("password")
        val data = "Test Data".toByteArray()

        val result1 = AESUtil.aesEncrypt(password, data)
        val result2 = AESUtil.aesEncrypt(password, data)

        // IVs should be different (random)
        assertNotEquals(result1.iv, result2.iv)
        // Ciphertexts should be different due to different IVs
        assertNotEquals(result1.ciphertext, result2.ciphertext)
    }

    @Test
    fun `decrypt should decrypt data encrypted with aesEncrypt`() {
        val password = AESUtil.getEquilPassWord("testpassword")
        val originalData = "Hello World".toByteArray()
        val originalHex = Utils.bytesToHex(originalData)

        // Encrypt
        val encrypted = AESUtil.aesEncrypt(password, originalData)

        // Decrypt
        val decrypted = AESUtil.decrypt(encrypted, password)

        assertEquals(originalHex, decrypted)
    }

    @Test
    fun `decrypt should handle various data sizes`() {
        val password = AESUtil.getEquilPassWord("password")

        // Test different data sizes
        val testCases = listOf(
            "A",
            "Short",
            "This is a longer test string",
            "12345678901234567890123456789012345678901234567890"
        )

        testCases.forEach { testData ->
            val originalData = testData.toByteArray()
            val originalHex = Utils.bytesToHex(originalData)

            val encrypted = AESUtil.aesEncrypt(password, originalData)
            val decrypted = AESUtil.decrypt(encrypted, password)

            assertEquals(originalHex, decrypted, "Failed for: $testData")
        }
    }

    @Test
    fun `decrypt should handle byte array data`() {
        val password = AESUtil.getEquilPassWord("password")
        val originalData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0xFF.toByte(), 0xAB.toByte())
        val originalHex = Utils.bytesToHex(originalData)

        val encrypted = AESUtil.aesEncrypt(password, originalData)
        val decrypted = AESUtil.decrypt(encrypted, password)

        assertEquals(originalHex, decrypted)
    }

    @Test
    fun `decrypt should fail with wrong password`() {
        val password1 = AESUtil.getEquilPassWord("password1")
        val password2 = AESUtil.getEquilPassWord("password2")
        val originalData = "Secret Data".toByteArray()

        val encrypted = AESUtil.aesEncrypt(password1, originalData)

        // Attempting to decrypt with wrong password should throw exception
        assertThrows<Exception> {
            AESUtil.decrypt(encrypted, password2)
        }
    }

    @Test
    fun `decrypt should throw IllegalStateException when IV is null`() {
        val password = AESUtil.getEquilPassWord("password")
        val model = EquilCmdModel()
        model.iv = null
        model.ciphertext = "0123456789ABCDEF"
        model.tag = "FEDCBA9876543210"

        assertThrows<IllegalStateException> {
            AESUtil.decrypt(model, password)
        }
    }

    @Test
    fun `decrypt should throw IllegalStateException when ciphertext is null`() {
        val password = AESUtil.getEquilPassWord("password")
        val model = EquilCmdModel()
        model.iv = "0123456789ABCDEF"
        model.ciphertext = null
        model.tag = "FEDCBA9876543210"

        assertThrows<IllegalStateException> {
            AESUtil.decrypt(model, password)
        }
    }

    @Test
    fun `decrypt should throw IllegalStateException when tag is null`() {
        val password = AESUtil.getEquilPassWord("password")
        val model = EquilCmdModel()
        model.iv = "0123456789ABCDEF"
        model.ciphertext = "0123456789ABCDEF"
        model.tag = null

        assertThrows<IllegalStateException> {
            AESUtil.decrypt(model, password)
        }
    }

    @Test
    fun `encryption roundtrip should preserve empty data`() {
        val password = AESUtil.getEquilPassWord("password")
        val originalData = byteArrayOf()
        val originalHex = Utils.bytesToHex(originalData)

        val encrypted = AESUtil.aesEncrypt(password, originalData)
        val decrypted = AESUtil.decrypt(encrypted, password)

        assertEquals(originalHex, decrypted)
    }

    @Test
    fun `encryption roundtrip should work with special characters`() {
        val password = AESUtil.getEquilPassWord("password")
        val originalData = "Special chars: !@#\$%^&*()_+-=[]{}|;':\",./<>?`~".toByteArray()
        val originalHex = Utils.bytesToHex(originalData)

        val encrypted = AESUtil.aesEncrypt(password, originalData)
        val decrypted = AESUtil.decrypt(encrypted, password)

        assertEquals(originalHex, decrypted)
    }

    @Test
    fun `encryption roundtrip should work with unicode characters`() {
        val password = AESUtil.getEquilPassWord("password")
        val originalData = "Unicode: 你好世界 🚀 α β γ".toByteArray()
        val originalHex = Utils.bytesToHex(originalData)

        val encrypted = AESUtil.aesEncrypt(password, originalData)
        val decrypted = AESUtil.decrypt(encrypted, password)

        assertEquals(originalHex, decrypted)
    }

    @Test
    fun `aesEncrypt should handle null password gracefully`() {
        val data = "Test Data".toByteArray()

        assertThrows<Exception> {
            AESUtil.aesEncrypt(null, data)
        }
    }

    @Test
    fun `aesEncrypt should handle null data gracefully`() {
        val password = AESUtil.getEquilPassWord("password")

        assertThrows<Exception> {
            AESUtil.aesEncrypt(password, null)
        }
    }
}
