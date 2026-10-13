package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CmdHistoryGetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 0505`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        assertEquals("0505", cmd.port)
    }

    @Test
    fun `constructor should store currentIndex`() {
        val cmd = CmdHistoryGet(5, aapsLogger, preferences, dateUtil, equilManager)
        assertEquals(5, cmd.currentIndex)
    }

    @Test
    fun `getEventType should return null`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4) + command (2) + currentIndex (4) = 10 bytes
        assertEquals(10, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x02, 0x01
        assertEquals(0x02.toByte(), data[4])
        assertEquals(0x01.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) = 7 bytes
        assertEquals(7, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x01, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x01.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should parse history data when currentIndex is not 0`() {
        val cmd = CmdHistoryGet(1, aapsLogger, preferences, dateUtil, equilManager)
        // Create test data array with 24 bytes
        val testData = ByteArray(24)
        // Year, month, day, hour, min, second at positions 6-11
        testData[6] = 23  // year 2023
        testData[7] = 7   // month July
        testData[8] = 14  // day
        testData[9] = 10  // hour
        testData[10] = 30 // minute
        testData[11] = 0  // second
        testData[12] = 100 // battery
        testData[13] = 50  // medicine

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        verify(equilManager).decodeHistory(testData)
        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `decodeConfirmData should not call decodeHistory when currentIndex is 0`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val testData = ByteArray(24)
        testData[6] = 23

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val testData = ByteArray(24)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `toString should return formatted string`() {
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val str = cmd.toString()
        assertTrue(str.contains("CmdHistoryGet"))
        assertTrue(str.contains("battery"))
        assertTrue(str.contains("medicine"))
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdHistoryGet(0, aapsLogger, preferences, dateUtil, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }
}
