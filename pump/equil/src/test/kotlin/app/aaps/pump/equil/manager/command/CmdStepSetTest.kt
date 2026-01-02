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

class CmdStepSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should store sendConfig flag`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        assertTrue(cmd.sendConfig)

        val cmd2 = CmdStepSet(false, 100, aapsLogger, preferences, equilManager)
        assertFalse(cmd2.sendConfig)
    }

    @Test
    fun `constructor should store step value`() {
        val cmd = CmdStepSet(true, 150, aapsLogger, preferences, equilManager)
        assertEquals(150, cmd.step)
    }

    @Test
    fun `getEventType should return null`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4) + command (2) + step (4) = 10 bytes
        assertEquals(10, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x01, 0x07
        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x07.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) + value (4) = 11 bytes
        assertEquals(11, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x07, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x07.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `step can be zero`() {
        val cmd = CmdStepSet(true, 0, aapsLogger, preferences, equilManager)
        assertEquals(0, cmd.step)
    }

    @Test
    fun `step can be large value`() {
        val cmd = CmdStepSet(true, 10000, aapsLogger, preferences, equilManager)
        assertEquals(10000, cmd.step)
    }

    @Test
    fun `multiple getFirstData calls should increment index each time`() {
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex

        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)

        cmd.getFirstData()
        assertEquals(initialIndex + 2, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdStepSet(true, 100, aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }
}
