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
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class CmdDevicesGetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should set port to 0000`() {
        val cmd = CmdDevicesGet(aapsLogger, preferences, equilManager)
        assertEquals("0000", cmd.port)
    }

    @Test
    fun `getEventType should return READ_DEVICES`() {
        val cmd = CmdDevicesGet(aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.READ_DEVICES, cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdDevicesGet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        assertEquals(6, data.size)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdDevicesGet(aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertEquals(0x02.toByte(), data[4])
        assertEquals(0x00.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdDevicesGet(aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        assertEquals(7, data.size)
    }

    @Test
    fun `decodeConfirmData should parse firmware version and set to equilManager`() {
        val cmd = CmdDevicesGet(aapsLogger, preferences, equilManager)
        val testData = ByteArray(20)
        testData[18] = 1
        testData[19] = 5

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        verify(equilManager).setFirmwareVersion("1.5")
        assertTrue(cmd.cmdSuccess)
    }
}
