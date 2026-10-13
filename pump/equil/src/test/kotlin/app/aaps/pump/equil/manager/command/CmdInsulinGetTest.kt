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

class CmdInsulinGetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 0505`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        assertEquals("0505", cmd.port)
    }

    @Test
    fun `getEventType should return null`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4 bytes) + command (2 bytes) = 6 bytes
        assertEquals(6, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x02, 0x07
        assertEquals(0x02.toByte(), data[4])
        assertEquals(0x07.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) + value (4) = 11 bytes
        assertEquals(11, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x07, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x07.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should parse insulin and set to equilManager`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)
        testData[6] = 50 // Insulin level = 50

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        verify(equilManager).setStartInsulin(50)
        verify(equilManager).setCurrentInsulin(50)
        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)
        testData[6] = 0

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `decodeConfirmData should handle different insulin values`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)
        testData[6] = 0xFF.toByte() // Insulin = 255

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        verify(equilManager).setStartInsulin(255)
        verify(equilManager).setCurrentInsulin(255)
        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `multiple getFirstData calls should increment index each time`() {
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex

        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)

        cmd.getFirstData()
        assertEquals(initialIndex + 2, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdInsulinGet(aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }
}
