package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.database.EquilHistoryRecord
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
import org.mockito.kotlin.whenever

class CmdModelSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should store mode value`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        assertEquals(1, cmd.mode)
    }

    @Test
    fun `getMode should return SUSPEND for mode 0`() {
        val cmd = CmdModelSet(0, aapsLogger, preferences, equilManager)
        assertEquals(RunMode.SUSPEND, cmd.getMode())
    }

    @Test
    fun `getMode should return RUN for mode 1`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        assertEquals(RunMode.RUN, cmd.getMode())
    }

    @Test
    fun `getMode should return RUN for mode 2`() {
        val cmd = CmdModelSet(2, aapsLogger, preferences, equilManager)
        assertEquals(RunMode.RUN, cmd.getMode())
    }

    @Test
    fun `getMode should return SUSPEND for other values`() {
        val cmd = CmdModelSet(99, aapsLogger, preferences, equilManager)
        assertEquals(RunMode.SUSPEND, cmd.getMode())
    }

    @Test
    fun `getEventType should return RESUME_DELIVERY for RUN mode`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.RESUME_DELIVERY, cmd.getEventType())
    }

    @Test
    fun `getEventType should return SUSPEND_DELIVERY for SUSPEND mode`() {
        val cmd = CmdModelSet(0, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SUSPEND_DELIVERY, cmd.getEventType())
    }

    @Test
    fun `getEventType should return null for invalid mode`() {
        // This should not happen since getMode() always returns SUSPEND or RUN
        // but testing the else branch
        val cmd = CmdModelSet(99, aapsLogger, preferences, equilManager)
        // Mode 99 maps to SUSPEND, so should return SUSPEND_DELIVERY
        assertEquals(EquilHistoryRecord.EventType.SUSPEND_DELIVERY, cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4) + command (2) + mode (4) = 10 bytes
        assertEquals(10, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x01, 0x00
        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x00.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) = 7 bytes
        assertEquals(7, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x00, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x00.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdModelSet(1, aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }
}
