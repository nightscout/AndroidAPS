package app.aaps.implementation.db

import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpWithConcentration
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The range version reads the database once and must answer every minute of the range exactly as the
 * per-time version does, which asks the database ("active at") every time.
 *
 * The fake database below answers like the real queries: "active at" is the latest started valid entry
 * with `timestamp <= t < timestamp + duration`, "starting from time to time" is `BETWEEN from AND to`.
 */
class ProcessedTbrEbDataImplTest {

    private val persistenceLayer = mock<PersistenceLayer>()
    private val activePlugin = mock<ActivePlugin>()
    private val profileFunction = mock<ProfileFunction>()
    private val pump = mock<PumpWithConcentration>()
    private val profile = mock<EffectiveProfile>()
    private lateinit var sut: ProcessedTbrEbDataImpl

    private var temporaryBasals = listOf<TB>()
    private var extendedBoluses = listOf<EB>()

    private fun tb(id: Long, startMinute: Long, minutes: Long, rate: Double) =
        TB(id = id, timestamp = startMinute * MINUTE, utcOffset = 0, type = TB.Type.NORMAL, isAbsolute = true, rate = rate, duration = minutes * MINUTE)

    private fun eb(id: Long, startMinute: Long, minutes: Long, amount: Double) =
        EB(id = id, timestamp = startMinute * MINUTE, utcOffset = 0, duration = minutes * MINUTE, amount = amount)

    @BeforeEach
    fun setUp() = runTest {
        whenever(activePlugin.activePump).thenReturn(pump)
        whenever(profileFunction.getProfile(any())).thenReturn(profile)
        whenever(profile.getBasal(any())).thenReturn(1.0)
        whenever(persistenceLayer.getTemporaryBasalActiveAt(any())).thenAnswer { invocation ->
            val t = invocation.getArgument<Long>(0)
            temporaryBasals.filter { it.timestamp <= t && it.timestamp + it.duration > t }.maxByOrNull { it.timestamp }
        }
        whenever(persistenceLayer.getTemporaryBasalsActiveAt(any())).thenAnswer { invocation ->
            val t = invocation.getArgument<Long>(0)
            temporaryBasals.filter { it.timestamp <= t && it.timestamp + it.duration > t }.sortedBy { it.timestamp }
        }
        whenever(persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(any(), any(), any())).thenAnswer { invocation ->
            temporaryBasals.filter { it.timestamp in invocation.getArgument<Long>(0)..invocation.getArgument<Long>(1) }.sortedBy { it.timestamp }
        }
        whenever(persistenceLayer.getExtendedBolusActiveAt(any())).thenAnswer { invocation ->
            val t = invocation.getArgument<Long>(0)
            extendedBoluses.filter { it.timestamp <= t && it.timestamp + it.duration > t }.maxByOrNull { it.timestamp }
        }
        whenever(persistenceLayer.getExtendedBolusesActiveAt(any())).thenAnswer { invocation ->
            val t = invocation.getArgument<Long>(0)
            extendedBoluses.filter { it.timestamp <= t && it.timestamp + it.duration > t }.sortedBy { it.timestamp }
        }
        whenever(persistenceLayer.getExtendedBolusesStartingFromTimeToTime(any(), any(), any())).thenAnswer { invocation ->
            extendedBoluses.filter { it.timestamp in invocation.getArgument<Long>(0)..invocation.getArgument<Long>(1) }.sortedBy { it.timestamp }
        }
        sut = ProcessedTbrEbDataImpl(persistenceLayer, activePlugin, profileFunction)
    }

    /** Every minute of the range, the range answer equals the per-time answer. */
    private suspend fun assertSameEveryMinute(fromMinute: Long, toMinute: Long) {
        val range = sut.getTempBasalsIncludingConvertedExtended(fromMinute * MINUTE, toMinute * MINUTE)
        for (minute in fromMinute..toMinute) {
            val t = minute * MINUTE
            val expected = sut.getTempBasalIncludingConvertedExtended(t)
            val actual = range.at(t)
            assertThat(actual?.rate).isEqualTo(expected?.rate)
            assertThat(actual?.timestamp).isEqualTo(expected?.timestamp)
            assertThat(actual?.type).isEqualTo(expected?.type)
        }
    }

    @Test
    fun `temporary basals - one running at the start, gaps, back to back, running past the end`() = runTest {
        whenever(pump.isFakingTempsByExtendedBoluses).thenReturn(false)
        temporaryBasals = listOf(
            tb(1, startMinute = 0, minutes = 60, rate = 0.5),     // running when the range starts at 30
            tb(2, startMinute = 70, minutes = 30, rate = 2.0),    // after a gap
            tb(3, startMinute = 100, minutes = 30, rate = 0.0),   // right after the previous one
            tb(4, startMinute = 200, minutes = 120, rate = 1.5)   // still running at the end of the range
        )
        extendedBoluses = listOf(eb(9, 140, 30, 1.0)) // ignored: the pump does not fake temps with them

        assertSameEveryMinute(30, 260)
    }

    @Test
    fun `nothing running anywhere`() = runTest {
        whenever(pump.isFakingTempsByExtendedBoluses).thenReturn(false)
        assertSameEveryMinute(0, 120)
    }

    /** A pump that fakes temporary basals with extended boluses: those count where no TBR runs. */
    @Test
    fun `extended boluses are converted where no temporary basal runs`() = runTest {
        whenever(pump.isFakingTempsByExtendedBoluses).thenReturn(true)
        temporaryBasals = listOf(tb(1, startMinute = 50, minutes = 20, rate = 0.0))
        extendedBoluses = listOf(
            eb(5, startMinute = 0, minutes = 60, amount = 1.0),   // running when the range starts, partly under the TBR
            eb(6, startMinute = 90, minutes = 30, amount = 0.5)
        )

        assertSameEveryMinute(10, 130)
    }

    /**
     * Overlapping valid entries, which pump sync normally does not leave but NS sync can: an older long
     * one, and later ones inside it that end first. The per-time query returns the last started running
     * one, so the older one again after the later ones end. The range starts while both run.
     */
    @Test
    fun `overlapping temporary basals give the same answer as the per-time query`() = runTest {
        whenever(pump.isFakingTempsByExtendedBoluses).thenReturn(false)
        temporaryBasals = listOf(
            tb(1, startMinute = 0, minutes = 120, rate = 0.5),  // older and long
            tb(2, startMinute = 20, minutes = 10, rate = 2.0),  // inside it, running when the range starts at 25
            tb(3, startMinute = 22, minutes = 30, rate = 0.0),  // also inside, ends later than 2
            tb(4, startMinute = 60, minutes = 5, rate = 1.5)    // starts inside the range, inside 1
        )

        assertSameEveryMinute(25, 130)
    }

    @Test
    fun `overlapping extended boluses give the same answer as the per-time query`() = runTest {
        whenever(pump.isFakingTempsByExtendedBoluses).thenReturn(true)
        extendedBoluses = listOf(
            eb(5, startMinute = 0, minutes = 120, amount = 2.0),
            eb(6, startMinute = 20, minutes = 10, amount = 0.5)
        )

        assertSameEveryMinute(25, 130)
    }

    private companion object {

        const val MINUTE = 60_000L
    }
}
