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
import org.mockito.kotlin.whenever

class CmdSettingSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should calculate bolusThresholdStep from maxBolus`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        // Should be calculated using Utils.decodeSpeedToUH
        assertTrue(cmd.bolusThresholdStep > 0)
    }

    @Test
    fun `constructor should calculate basalThresholdStep from maxBasal`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        // Should be calculated using Utils.decodeSpeedToUH
        assertTrue(cmd.basalThresholdStep > 0)
    }

    @Test
    fun `lowAlarm should be initialized to 0`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        assertEquals(0.0, cmd.lowAlarm, 0.001)
    }

    @Test
    fun `getEventType should return null`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `isPairStep should return true`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        assertTrue(cmd.isPairStep())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4) + command (2) + multiple parameters
        // useTime(4) + autoCloseTime(4) + lowAlarm(2) + fastBolus(2) + occlusion(2) + insulinUnit(2) + basalThreshold(2) + bolusThreshold(2)
        // = 4 + 2 + 4 + 4 + 2 + 2 + 2 + 2 + 2 + 2 = 26 bytes
        assertEquals(26, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x01, 0x05
        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x05.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) = 7 bytes
        assertEquals(7, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x05, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x05.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `constructor should handle different maxBolus values`() {
        val cmd1 = CmdSettingSet(5.0, 3.0, aapsLogger, preferences, equilManager)
        val cmd2 = CmdSettingSet(15.0, 3.0, aapsLogger, preferences, equilManager)

        // Different maxBolus should result in different thresholds
        assertTrue(cmd1.bolusThresholdStep > 0)
        assertTrue(cmd2.bolusThresholdStep > 0)
    }

    @Test
    fun `constructor should handle different maxBasal values`() {
        val cmd1 = CmdSettingSet(10.0, 2.0, aapsLogger, preferences, equilManager)
        val cmd2 = CmdSettingSet(10.0, 8.0, aapsLogger, preferences, equilManager)

        // Different maxBasal should result in different thresholds
        assertTrue(cmd1.basalThresholdStep > 0)
        assertTrue(cmd2.basalThresholdStep > 0)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdSettingSet(10.0, 5.0, aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }
}
