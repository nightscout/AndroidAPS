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

class CmdExtendedBolusSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should calculate step from insulin`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        // step = (1.0 / 0.05 * 8) = 160
        assertEquals(160, cmd.step)
    }

    @Test
    fun `constructor should calculate pumpTime from duration`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        // pumpTime = 30 * 60 = 1800 seconds
        assertEquals(1800, cmd.pumpTime)
    }

    @Test
    fun `constructor should handle zero insulin`() {
        val cmd = CmdExtendedBolusSet(0.0, 30, false, aapsLogger, preferences, equilManager)
        assertEquals(0, cmd.step)
        assertEquals(0, cmd.pumpTime)
    }

    @Test
    fun `constructor should store insulin value`() {
        val cmd = CmdExtendedBolusSet(2.5, 60, false, aapsLogger, preferences, equilManager)
        assertEquals(2.5, cmd.insulin, 0.001)
    }

    @Test
    fun `constructor should store duration value`() {
        val cmd = CmdExtendedBolusSet(1.0, 45, false, aapsLogger, preferences, equilManager)
        assertEquals(45, cmd.durationInMinutes)
    }

    @Test
    fun `constructor should store cancel flag`() {
        val cmd1 = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        assertFalse(cmd1.cancel)

        val cmd2 = CmdExtendedBolusSet(1.0, 30, true, aapsLogger, preferences, equilManager)
        assertTrue(cmd2.cancel)
    }

    @Test
    fun `getEventType should return SET_EXTENDED_BOLUS when not cancelled`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SET_EXTENDED_BOLUS, cmd.getEventType())
    }

    @Test
    fun `getEventType should return CANCEL_EXTENDED_BOLUS when cancelled`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, true, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.CANCEL_EXTENDED_BOLUS, cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4) + command (2) + 2x zero (4+4) + step (4) + time (4) = 22 bytes
        assertEquals(22, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x01, 0x03
        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x03.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) + value (4) = 11 bytes
        assertEquals(11, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x03, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x03.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdExtendedBolusSet(1.0, 30, false, aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `step calculation should handle different insulin values`() {
        val cmd1 = CmdExtendedBolusSet(0.5, 30, false, aapsLogger, preferences, equilManager)
        assertEquals(80, cmd1.step)

        val cmd2 = CmdExtendedBolusSet(2.0, 30, false, aapsLogger, preferences, equilManager)
        assertEquals(320, cmd2.step)
    }

    @Test
    fun `pumpTime calculation should handle different durations`() {
        val cmd1 = CmdExtendedBolusSet(1.0, 15, false, aapsLogger, preferences, equilManager)
        assertEquals(900, cmd1.pumpTime)

        val cmd2 = CmdExtendedBolusSet(1.0, 60, false, aapsLogger, preferences, equilManager)
        assertEquals(3600, cmd2.pumpTime)
    }
}
