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

class CmdLargeBasalSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should calculate step from insulin`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        // step = (1.0 / 0.05 * 8) = 160
        assertEquals(160, cmd.step)
    }

    @Test
    fun `constructor should calculate stepTime from insulin`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        // stepTime = (1.0 / 0.05 * 2) = 40
        assertEquals(40, cmd.stepTime)
    }

    @Test
    fun `stepTime follows the chosen bolus speed (#5142)`() {
        // Seconds per unit: 40 = 1.5 U/min, 20 = 3, 10 = 6, 5 = 12 U/min. Measured on a real pump.
        assertEquals(480, CmdLargeBasalSet(12.0, aapsLogger, preferences, equilManager, 40).stepTime)
        assertEquals(20, CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager, 20).stepTime)
        assertEquals(10, CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager, 10).stepTime)
        assertEquals(5, CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager, 5).stepTime)
        // The amount does not depend on the speed.
        assertEquals(160, CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager, 5).step)
    }

    @Test
    fun `a small fast bolus gets at least 1 second`() {
        // 0.05 U at 12 U/min would be 0.25 s, sent as 0 s.
        assertEquals(1, CmdLargeBasalSet(0.05, aapsLogger, preferences, equilManager, 5).stepTime)
        assertEquals(2, CmdLargeBasalSet(0.05, aapsLogger, preferences, equilManager, 40).stepTime)
    }

    @Test
    fun `constructor should handle zero insulin`() {
        val cmd = CmdLargeBasalSet(0.0, aapsLogger, preferences, equilManager)
        assertEquals(0, cmd.step)
        assertEquals(0, cmd.stepTime)
    }

    @Test
    fun `constructor should store insulin value`() {
        val cmd = CmdLargeBasalSet(2.5, aapsLogger, preferences, equilManager)
        assertEquals(2.5, cmd.insulin, 0.001)
    }

    @Test
    fun `getEventType should return SET_BOLUS for non-zero insulin`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SET_BOLUS, cmd.getEventType())
    }

    @Test
    fun `getEventType should return CANCEL_BOLUS for zero insulin`() {
        val cmd = CmdLargeBasalSet(0.0, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.CANCEL_BOLUS, cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4) + command (2) + step (4) + stepTime (4) + 2x zero (4+4) = 22 bytes
        assertEquals(22, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x01, 0x03
        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x03.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) + value (4) = 11 bytes
        assertEquals(11, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x03, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x03.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdLargeBasalSet(1.0, aapsLogger, preferences, equilManager)
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
        val cmd1 = CmdLargeBasalSet(0.5, aapsLogger, preferences, equilManager)
        assertEquals(80, cmd1.step)
        assertEquals(20, cmd1.stepTime)

        val cmd2 = CmdLargeBasalSet(2.0, aapsLogger, preferences, equilManager)
        assertEquals(320, cmd2.step)
        assertEquals(80, cmd2.stepTime)
    }
}
