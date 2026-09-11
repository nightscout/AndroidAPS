package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.AESUtil
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.EquilPacketCodec
import app.aaps.pump.equil.manager.Utils
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class BaseSettingTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    private lateinit var testSetting: TestBaseSetting

    // Concrete implementation for testing
    private inner class TestBaseSetting(createTime: Long = System.currentTimeMillis()) :
        BaseSetting(createTime, aapsLogger, preferences, equilManager) {

        var firstDataCalled = false
        var nextDataCalled = false
        var decodeConfirmDataCalled = false

        override fun getFirstData(): ByteArray {
            firstDataCalled = true
            return byteArrayOf(0x01, 0x02, 0x03, 0x04)
        }

        override fun getNextData(): ByteArray {
            nextDataCalled = true
            return byteArrayOf(0x05, 0x06, 0x07, 0x08)
        }

        override fun decodeConfirmData(data: ByteArray) {
            decodeConfirmDataCalled = true
        }

        override fun getEventType(): EquilHistoryRecord.EventType {
            return EquilHistoryRecord.EventType.SET_BASAL_PROFILE
        }
    }

    @BeforeEach
    fun setUp() {
        // Mock preferences to return valid hex strings for device and password
        // Device needs to be a valid hex string, Password needs to be 64 chars (32 bytes)
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")

        testSetting = TestBaseSetting()
    }

    @Test
    fun `getReqData should increment pumpReqIndex`() {
        val initialIndex = BaseCmd.pumpReqIndex
        testSetting.getReqData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getReqData should return byte array with correct structure`() {
        val result = testSetting.getReqData()

        assertNotNull(result)
        // Should contain index bytes (4 bytes) + device bytes
        assertTrue(result.size >= 4)
    }

    @Test
    fun `getFirstData should be called by subclass`() {
        assertFalse(testSetting.firstDataCalled)
        testSetting.getFirstData()
        assertTrue(testSetting.firstDataCalled)
    }

    @Test
    fun `getNextData should be called by subclass`() {
        assertFalse(testSetting.nextDataCalled)
        testSetting.getNextData()
        assertTrue(testSetting.nextDataCalled)
    }

    @Test
    fun `decodeConfirmData should be called by subclass`() {
        assertFalse(testSetting.decodeConfirmDataCalled)
        testSetting.decodeConfirmData(byteArrayOf(0x00))
        assertTrue(testSetting.decodeConfirmDataCalled)
    }

    @Test
    fun `getEventType should return correct event type`() {
        assertEquals(EquilHistoryRecord.EventType.SET_BASAL_PROFILE, testSetting.getEventType())
    }

    @Test
    fun `config flag should be false initially`() {
        assertFalse(testSetting.config)
    }

    @Test
    fun `isEnd flag should be false initially`() {
        assertFalse(testSetting.isEnd)
    }

    @Test
    fun `cmdSuccess should be true initially (enacted default)`() {
        // enacted defaults to true in BaseCmd
        assertTrue(testSetting.enacted)
    }

    @Test
    fun `createTime should be set from constructor`() {
        val testTime = 123456789L
        val setting = TestBaseSetting(testTime)
        assertEquals(testTime, setting.createTime)
    }

    @Test
    fun `port should have default value`() {
        assertEquals("0404", testSetting.port)
    }

    @Test
    fun `response should be null initially`() {
        assertNull(testSetting.response)
    }

    @Test
    fun `runPwd should be null initially`() {
        assertNull(testSetting.runPwd)
    }

    @Test
    fun `runCode should be null initially`() {
        assertNull(testSetting.runCode)
    }

    @Test
    fun `timeout values should be set`() {
        assertEquals(22000, testSetting.timeOut)
        assertEquals(15000, testSetting.connectTimeOut)
    }

    @Test
    fun `multiple getReqData calls should increment index each time`() {
        val initialIndex = BaseCmd.pumpReqIndex
        testSetting.getReqData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)

        testSetting.getReqData()
        assertEquals(initialIndex + 2, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getReqData should not call getFirstData`() {
        val result = testSetting.getReqData()

        assertFalse(testSetting.firstDataCalled)
        assertNotNull(result)
    }

    @Test
    fun `createTime with custom value should be stored`() {
        val customTime = 987654321L
        val setting = TestBaseSetting(customTime)
        assertEquals(customTime, setting.createTime)
    }

    @Test
    fun `getNextData should be callable`() {
        val result = testSetting.getNextData()

        assertTrue(testSetting.nextDataCalled)
        assertNotNull(result)
        assertEquals(4, result.size) // Should return the mock data from TestBaseSetting
    }

    @Test
    fun `decodeConfirmData should be callable`() {
        val testData = ByteArray(10)
        testSetting.decodeConfirmData(testData)

        assertTrue(testSetting.decodeConfirmDataCalled)
    }

    @Test
    fun `getEventType should return correct type`() {
        val eventType = testSetting.getEventType()

        assertNotNull(eventType)
        assertEquals(EquilHistoryRecord.EventType.SET_BASAL_PROFILE, eventType)
    }

    @Test
    fun `port should have default value from BaseCmd`() {
        assertEquals("0404", testSetting.port)
    }

    @Test
    fun `enacted should be true by default`() {
        assertTrue(testSetting.enacted)
    }

    @Test
    fun `cmdSuccess should be false initially`() {
        assertFalse(testSetting.cmdSuccess)
    }

    @Test
    fun `statusDescription should be class name`() {
        assertTrue(testSetting.statusDescription.contains("TestBaseSetting"))
    }

    @Test
    fun `getEquilResponse should produce valid framed packets`() {
        val response = testSetting.getEquilResponse()
        assertNotNull(response)
        assertTrue(response!!.send.size > 0)

        // Every packet must have valid CRC8
        for (buf in response.send) {
            val bytes = buf.array()
            val expectedCrc = app.aaps.pump.equil.manager.Crc.crc8Maxim(bytes.copyOfRange(0, 5))
            assertEquals(expectedCrc.toByte(), bytes[5], "CRC8 mismatch on framed packet")
        }

        // Last packet must have end bit set
        val lastBytes = response.send.last.array()
        assertTrue(EquilPacketCodec.isEnd(lastBytes[4]), "Last packet should have end bit set")
    }

    @Test
    fun `responseCmd and decodeModel should round-trip an EquilCmdModel`() {
        // Encrypt some test data
        val pwd = Utils.hexStringToBytes(testSetting.getEquilPassWord())
        val testData = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08)
        val encrypted = AESUtil.aesEncrypt(pwd, testData)

        // Build packets via responseCmd (which delegates to EquilPacketCodec.buildPackets)
        val port = "0F0F0000"
        testSetting.response = app.aaps.pump.equil.manager.EquilResponse(testSetting.createTime)
        val builtResponse = testSetting.responseCmd(encrypted, port)

        // Simulate receiving: store in response field, then parse
        testSetting.response = builtResponse
        val parsed = testSetting.decodeModel()

        // Verify round-trip preserves the encrypted model
        assertEquals(encrypted.tag?.lowercase(), parsed.tag)
        assertEquals(encrypted.iv?.lowercase(), parsed.iv)
        assertEquals(encrypted.ciphertext?.lowercase(), parsed.ciphertext)

        // Verify we can decrypt back to original data
        val decrypted = AESUtil.decrypt(parsed, pwd)
        val decryptedBytes = Utils.hexStringToBytes(decrypted)
        assertNotNull(decryptedBytes)
        assertEquals(testData.size, decryptedBytes.size)
        for (i in testData.indices) {
            assertEquals(testData[i], decryptedBytes[i], "Decrypted byte $i mismatch")
        }
    }
}
