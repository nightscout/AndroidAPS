package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
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

class CmdDevicesOldGetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 0E0E`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        assertEquals("0E0E", cmd.port)
    }

    @Test
    fun `constructor should store address`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        assertEquals("00:11:22:33:44:55", cmd.address)
    }

    @Test
    fun `getEventType should return null`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `getEquilResponse should return EquilResponse`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val response = cmd.getEquilResponse()
        assertNotNull(response)
        assertFalse(cmd.config)
        assertFalse(cmd.isEnd)
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        assertEquals(6, data.size)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertEquals(0x02.toByte(), data[4])
        assertEquals(0x00.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        assertEquals(7, data.size)
    }

    @Test
    fun `decodeConfirmData should parse firmware version`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val testData = ByteArray(20)
        testData[18] = 1
        testData[19] = 5

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `isSupport should check firmware version`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        // Initially firmware version is 0.0
        assertFalse(cmd.isSupport())
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x00.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `getEquilResponse should initialize response and return non-null`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val response = cmd.getEquilResponse()

        assertNotNull(response)
        assertNotNull(cmd.response)
        assertFalse(cmd.config)
        assertFalse(cmd.isEnd)
    }

    @Test
    fun `getEquilResponse should return 14 byte buffer`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val response = cmd.getEquilResponse()

        assertNotNull(response)
        assertEquals(1, response.send.size)
        assertEquals(14, response.send[0].array().size)
    }

    @Test
    fun `decodeConfirmData should parse firmware version correctly`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val testData = ByteArray(20)
        testData[18] = 5
        testData[19] = 5

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
        assertTrue(cmd.isSupport()) // 5.5 should be supported (>= 5.3)
    }

    @Test
    fun `should handle different address formats`() {
        val cmd1 = CmdDevicesOldGet("AA:BB:CC:DD:EE:FF", aapsLogger, preferences, equilManager)
        assertEquals("AA:BB:CC:DD:EE:FF", cmd1.address)

        val cmd2 = CmdDevicesOldGet("00-11-22-33-44-55", aapsLogger, preferences, equilManager)
        assertEquals("00-11-22-33-44-55", cmd2.address)
    }

    @Test
    fun `multiple getFirstData calls should increment index each time`() {
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex

        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)

        cmd.getFirstData()
        assertEquals(initialIndex + 2, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdDevicesOldGet("00:11:22:33:44:55", aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }
}
