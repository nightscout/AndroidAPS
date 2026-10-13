package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.profile.Profile
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

class CmdBasalGetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @Mock
    private lateinit var mockProfile: Profile

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")

        // Mock profile basal rates
        for (i in 0..23) {
            whenever(mockProfile.getBasalTimeFromMidnight(i * 60 * 60)).thenReturn(1.0)
        }
    }

    @Test
    fun `constructor should store profile`() {
        val cmd = CmdBasalGet(mockProfile, aapsLogger, preferences, equilManager)
        assertEquals(mockProfile, cmd.profile)
    }

    @Test
    fun `getEventType should return null`() {
        val cmd = CmdBasalGet(mockProfile, aapsLogger, preferences, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdBasalGet(mockProfile, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        assertEquals(6, data.size)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdBasalGet(mockProfile, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertEquals(0x02.toByte(), data[4])
        assertEquals(0x02.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdBasalGet(mockProfile, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        assertEquals(7, data.size)
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdBasalGet(mockProfile, aapsLogger, preferences, equilManager)
        val testData = ByteArray(100)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }
}
