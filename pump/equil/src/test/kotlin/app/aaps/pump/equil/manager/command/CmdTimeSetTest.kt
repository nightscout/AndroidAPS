package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class CmdTimeSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 0505`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        assertEquals("0505", cmd.port)
    }

    @Test
    fun `getEventType should return SET_TIME`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SET_TIME, cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val initialIndex = BaseCmd.pumpReqIndex
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4 bytes) + command (2 bytes) + time data (6 bytes) = 12 bytes
        assertEquals(12, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should include time bytes`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Data should be: 4 bytes index + 2 bytes (0x01, 0x00) + 6 bytes time
        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x00.toByte(), data[5])
        // Time bytes should be present (bytes 6-11)
        assertTrue(data.size >= 12)
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4 bytes) + data (3 bytes) = 7 bytes
        assertEquals(7, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct byte pattern`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Should be: 4 bytes index + 0x00, 0x00, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x00.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)

        // Create a thread to call decodeConfirmData since it uses synchronized/notify
        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000) // Wait up to 1 second

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `multiple getFirstData calls should increment index each time`() {
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex

        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)

        cmd.getFirstData()
        assertEquals(initialIndex + 2, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdTimeSet(aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }
}
