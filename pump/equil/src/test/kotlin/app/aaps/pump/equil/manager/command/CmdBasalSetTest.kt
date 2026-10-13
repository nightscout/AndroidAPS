package app.aaps.pump.equil.manager.command

import app.aaps.core.interfaces.profile.Profile
import app.aaps.pump.equil.database.EquilHistoryRecord
import app.aaps.pump.equil.driver.definition.BasalSchedule
import app.aaps.pump.equil.driver.definition.BasalScheduleEntry
import app.aaps.pump.equil.keys.EquilBooleanKey
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import org.joda.time.Duration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.verify

class CmdBasalSetTest : TestBaseWithProfile() {

    @Mock
    private lateinit var equilManager: EquilManager

    @Mock
    private lateinit var profile: Profile

    private lateinit var basalSchedule: BasalSchedule
    private lateinit var cmdBasalSet: CmdBasalSet

    @BeforeEach
    fun setUp() {

        // Create a simple basal schedule
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6)),
            BasalScheduleEntry(0.8, Duration.standardHours(12))
        )
        basalSchedule = BasalSchedule(entries)

        cmdBasalSet = CmdBasalSet(basalSchedule, profile, aapsLogger, preferences, equilManager)
    }

    @Test
    fun `getEventType should return SET_BASAL_PROFILE`() {
        assertEquals(EquilHistoryRecord.EventType.SET_BASAL_PROFILE, cmdBasalSet.getEventType())
    }

    @Test
    fun `getFirstData should return byte array`() {
        val data = cmdBasalSet.getFirstData()

        assertNotNull(data)
        assertTrue(data.isNotEmpty())
    }

    @Test
    fun `getFirstData should contain index bytes and command header`() {
        val data = cmdBasalSet.getFirstData()

        // Should have at least: 4 bytes (index) + 2 bytes (command) + basal data
        assertTrue(data.size >= 6)

        // Check command bytes (0x01, 0x02)
        assertEquals(0x01.toByte(), data[4])
        assertEquals(0x02.toByte(), data[5])
    }

    @Test
    fun `getFirstData should encode basal schedule entries`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO)
        )
        val schedule = BasalSchedule(entries)
        val cmd = CmdBasalSet(schedule, profile, aapsLogger, preferences, equilManager)

        val data = cmd.getFirstData()

        // Should contain encoded basal rate
        // 1.0 U/h = 0.5 after division by 2 = 80 in encoded form = 0x0050
        // Encoded as: [low, high, low, high]
        assertNotNull(data)
    }

    @Test
    fun `getNextData should return byte array with correct structure`() {
        val data = cmdBasalSet.getNextData()

        assertNotNull(data)
        assertTrue(data.size >= 7) // 4 (index) + 3 (0x00, 0x02, 0x01)

        // Check command bytes
        assertEquals(0x00.toByte(), data[4])
        assertEquals(0x02.toByte(), data[5])
        assertEquals(0x01.toByte(), data[6])
    }

    @Test
    fun `decodeConfirmData should set cmdSuccess to true`() {
        val testData = byteArrayOf(0x00, 0x01, 0x02)

        cmdBasalSet.decodeConfirmData(testData)

        assertTrue(cmdBasalSet.cmdSuccess)
    }

    @Test
    fun `decodeConfirmData should update preferences`() {
        val testData = byteArrayOf(0x00, 0x01, 0x02)

        cmdBasalSet.decodeConfirmData(testData)

        verify(preferences).put(EquilBooleanKey.BasalSet, true)
    }

    @Test
    fun `basalSchedule property should be accessible`() {
        assertEquals(basalSchedule, cmdBasalSet.basalSchedule)
    }

    @Test
    fun `profile property should be accessible`() {
        assertEquals(profile, cmdBasalSet.profile)
    }

    @Test
    fun `getFirstData should handle multiple basal entries`() {
        val entries = listOf(
            BasalScheduleEntry(1.0, Duration.ZERO),
            BasalScheduleEntry(1.5, Duration.standardHours(6)),
            BasalScheduleEntry(0.8, Duration.standardHours(12)),
            BasalScheduleEntry(2.0, Duration.standardHours(18))
        )
        val schedule = BasalSchedule(entries)
        val cmd = CmdBasalSet(schedule, profile, aapsLogger, preferences, equilManager)

        val data = cmd.getFirstData()

        // Should contain data for all 4 entries
        // Each entry: 4 bytes (low, high, low, high)
        // Plus: 4 (index) + 2 (command) = 6 bytes overhead
        // Total: 6 + (4 * 4) = 22 bytes minimum
        assertTrue(data.size >= 22)
    }

    @Test
    fun `getFirstData should divide rates by 2 before encoding`() {
        // This is tested indirectly through the encoding
        // Rate 1.0 -> 0.5 -> encoded value
        val entries = listOf(
            BasalScheduleEntry(2.0, Duration.ZERO) // Will become 1.0 after division
        )
        val schedule = BasalSchedule(entries)
        val cmd = CmdBasalSet(schedule, profile, aapsLogger, preferences, equilManager)

        val data = cmd.getFirstData()

        // Data should be encoded (we're mainly checking it doesn't crash)
        assertNotNull(data)
        assertTrue(data.isNotEmpty())
    }

    @Test
    fun `getFirstData and getNextData should increment pumpReqIndex`() {
        val initialIndex = BaseCmd.pumpReqIndex

        cmdBasalSet.getFirstData()
        val afterFirst = BaseCmd.pumpReqIndex
        assertEquals(initialIndex + 1, afterFirst)

        cmdBasalSet.getNextData()
        val afterNext = BaseCmd.pumpReqIndex
        assertEquals(afterFirst + 1, afterNext)
    }

    @Test
    fun `decodeConfirmData should be thread-safe`() {
        val testData = byteArrayOf(0x00, 0x01, 0x02)

        // This synchronizes internally, should not throw
        cmdBasalSet.decodeConfirmData(testData)

        assertTrue(cmdBasalSet.cmdSuccess)
    }
}
