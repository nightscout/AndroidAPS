package app.aaps.pump.equil.manager.command

import app.aaps.pump.equil.data.AlarmMode
import app.aaps.pump.equil.database.EquilHistoryRecord
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

class CmdAlarmSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @BeforeEach
    fun setUp() {
        whenever(preferences.get(EquilStringKey.Device)).thenReturn("0123456789ABCDEF")
        whenever(preferences.get(EquilStringKey.Password)).thenReturn("0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF")
    }

    @Test
    fun `constructor should store mode`() {
        val cmd = CmdAlarmSet(AlarmMode.TONE.command, aapsLogger, preferences, equilManager)
        assertEquals(AlarmMode.TONE.command, cmd.mode)
    }

    @Test
    fun `getEventType should return SET_ALARM_MUTE for MUTE mode`() {
        val cmd = CmdAlarmSet(AlarmMode.MUTE.command, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SET_ALARM_MUTE, cmd.getEventType())
    }

    @Test
    fun `getEventType should return SET_ALARM_TONE for TONE mode`() {
        val cmd = CmdAlarmSet(AlarmMode.TONE.command, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SET_ALARM_TONE, cmd.getEventType())
    }

    @Test
    fun `getEventType should return SET_ALARM_SHAKE for SHAKE mode`() {
        val cmd = CmdAlarmSet(AlarmMode.SHAKE.command, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SET_ALARM_SHAKE, cmd.getEventType())
    }

    @Test
    fun `getEventType should return SET_ALARM_TONE_AND_SHAK for TONE_AND_SHAKE mode`() {
        val cmd = CmdAlarmSet(AlarmMode.TONE_AND_SHAKE.command, aapsLogger, preferences, equilManager)
        assertEquals(EquilHistoryRecord.EventType.SET_ALARM_TONE_AND_SHAK, cmd.getEventType())
    }

    @Test
    fun `getEventType should return null for unknown mode`() {
        val cmd = CmdAlarmSet(99, aapsLogger, preferences, equilManager)
        assertNull(cmd.getEventType())
    }

    @Test
    fun `getFirstData should return byte array with correct structure`() {
        val cmd = CmdAlarmSet(AlarmMode.TONE.command, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertNotNull(data)
        assertEquals(10, data.size)
    }

    @Test
    fun `getFirstData should have correct command bytes`() {
        val cmd = CmdAlarmSet(AlarmMode.TONE.command, aapsLogger, preferences, equilManager)
        val data = cmd.getFirstData()

        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x0b.toByte(), data[5])
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val cmd = CmdAlarmSet(AlarmMode.TONE.command, aapsLogger, preferences, equilManager)
        val data = cmd.getNextData()

        assertNotNull(data)
        assertEquals(7, data.size)
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val cmd = CmdAlarmSet(AlarmMode.TONE.command, aapsLogger, preferences, equilManager)
        val testData = ByteArray(10)

        val thread = Thread {
            cmd.decodeConfirmData(testData)
        }
        thread.start()
        thread.join(1000)

        assertTrue(cmd.cmdSuccess)
    }
}
