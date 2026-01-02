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

class CmdResistanceGetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 1515`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        assertEquals("1515", cmd.port)
    }

    @Test
    fun `getEventType should return null`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        // Should contain index (4 bytes) + command (2 bytes) = 6 bytes
        assertEquals(6, data.size)
    }

    @Test
    fun `getFirstData should increment pumpReqIndex`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        // Bytes at positions 4 and 5 should be 0x02, 0x02
        assertEquals(0x02.toByte(), data[4])
        assertEquals(0x02.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        // Should contain index (4) + command (3) = 7 bytes
        assertEquals(7, data.size)
    }

    @Test
    fun `getNextData should increment pumpReqIndex`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex
        cmd.getNextData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `getNextData should have correct command bytes`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        // Bytes at positions 4, 5, 6 should be 0x00, 0x02, 0x01
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x02.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set enacted to true when resistance is 500 or more`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)
        // Set bytes 6 and 7 to represent value >= 500
        // Utils.bytesToInt(data[7], data[6]) treats data[7] as high byte
        testData[6] = 0xF4.toByte() // Low byte = 244
        testData[7] = 0x01 // High byte = 1, result = 500

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
        assertTrue(cmd.enacted)
    }

    @Test
    fun `decodeConfirmData should set enacted to false when resistance is less than 500`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)
        // Set bytes 6 and 7 to represent value < 500
        // Utils.bytesToInt(data[7], data[6]) treats data[7] as high byte
        testData[6] = 0x64 // Low byte = 100
        testData[7] = 0x00 // High byte = 0, result = 100

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
        assertFalse(cmd.enacted)
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }

    @Test
    fun `decodeConfirmData should handle boundary value of 500`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)
        // Set bytes 6 and 7 to exactly 500
        // Utils.bytesToInt(data[7], data[6]) treats data[7] as high byte
        testData[6] = 0xF4.toByte() // Low byte = 244
        testData[7] = 0x01 // High byte = 1, result = 500

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.enacted)
    }

    @Test
    fun `multiple getFirstData calls should increment index each time`() {
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val initialIndex = BaseCmd.pumpReqIndex

        cmd.getFirstData()
        assertEquals(initialIndex + 1, BaseCmd.pumpReqIndex)

        cmd.getFirstData()
        assertEquals(initialIndex + 2, BaseCmd.pumpReqIndex)
    }

    @Test
    fun `createTime should be set`() {
        val beforeTime = System.currentTimeMillis()
        val cmd = CmdResistanceGet(aapsLogger, preferences, equilManager)
        val afterTime = System.currentTimeMillis()

        assertTrue(cmd.createTime >= beforeTime)
        assertTrue(cmd.createTime <= afterTime)
    }
}
