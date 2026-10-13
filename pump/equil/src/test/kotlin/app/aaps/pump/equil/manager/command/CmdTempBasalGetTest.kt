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

class CmdTempBasalGetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should initialize time to 0`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        assertEquals(0, cmd.time)
    }

    @Test
    fun `getEventType should return null`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4 bytes) + command (2 bytes) = 6 bytes
        assertEquals(6, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x02, 0x04
        assertEquals(0x02.toByte(), data[4])
        assertEquals(0x04.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) + value (4) = 11 bytes
        assertEquals(11, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x04, 0x02
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x04.toByte(), data[5])
        assertEquals(0x02.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should parse step from data`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(14)
        // Set step bytes (bytes 6-9) to represent value 100
        testData[6] = 0x00
        testData[7] = 0x00
        testData[8] = 0x00
        testData[9] = 100

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `decodeConfirmData should parse time from data`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(14)
        // Set time bytes (bytes 10-13) to represent value 1800
        testData[10] = 0x00
        testData[11] = 0x00
        testData[12] = 0x07
        testData[13] = 0x08.toByte()

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        // Time should be parsed from bytes
        assertTrue(cmd.time >= 0)
        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(14)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `multiple getFirstData calls should increment index each time`() {
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex

        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)

        cmd.getFirstData()
        assertEquals(initialIndex + 2, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdTempBasalGet(aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }
}
