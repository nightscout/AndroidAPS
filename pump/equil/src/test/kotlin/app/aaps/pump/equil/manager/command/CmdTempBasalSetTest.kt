package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.keys.EquilStringKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class CmdTempBasalSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should calculate step from insulin rate`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        // step = (1.0 / 0.05 * 8) / 2 = (20 * 8) / 2 = 80
        assertEquals(80, cmd.step)
    }

    @Test
    fun `constructor should handle zero insulin rate`() {
        val cmd = CmdTempBasalSet(0.0, 30, aapsLogger, preferences, equilManager)
        assertEquals(0, cmd.step)
    }

    @Test
    fun `constructor should convert duration to seconds`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        // 30 minutes * 60 = 1800 seconds
        assertEquals(1800, cmd.pumpTime)
    }

    @Test
    fun `constructor should handle different insulin rates`() {
        val cmd1 = CmdTempBasalSet(0.5, 30, aapsLogger, preferences, equilManager)
        assertEquals(40, cmd1.step)

        val cmd2 = CmdTempBasalSet(2.0, 30, aapsLogger, preferences, equilManager)
        assertEquals(160, cmd2.step)
    }

    @Test
    fun `cancel flag should be false by default`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        assertFalse(cmd.cancel)
    }

    @Test
    fun `getEventType should return SET_TEMPORARY_BASAL when not cancelled`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SET_TEMPORARY_BASAL, cmd.getEventType())
    }

    @Test
    fun `getEventType should return CANCEL_TEMPORARY_BASAL when cancelled`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        cmd.cancel = true
        assertEquals(EquilHistoryRecord.EventType.CANCEL_TEMPORARY_BASAL, cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4) + command (2) + step (4) + time (4) = 14 bytes
        assertEquals(14, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x01, 0x04
        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x04.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) + value (4) = 11 bytes
        assertEquals(11, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x04, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x04.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdTempBasalSet(1.0, 30, aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `insulin and duration should be stored`() {
        val cmd = CmdTempBasalSet(1.5, 45, aapsLogger, preferences, equilManager)
        assertEquals(1.5, cmd.insulin, 0.001)
        assertEquals(45, cmd.duration)
    }

    @Test
    fun `step calculation should handle fractional insulin rates`() {
        val cmd = CmdTempBasalSet(0.75, 30, aapsLogger, preferences, equilManager)
        // step = (0.75 / 0.05 * 8) / 2 = (15 * 8) / 2 = 60
        assertEquals(60, cmd.step)
    }
}
