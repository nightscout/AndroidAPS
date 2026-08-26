package app.aaps.implementation.receivers

import android.content.Intent
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.shared.tests.TestBaseWithProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import java.util.Date
import java.util.TimeZone

class TimeDateOrTZChangeReceiverTest : TestBaseWithProfile() {

    private lateinit var sut: TimeDateOrTZChangeReceiver
    private lateinit var defaultTimeZone: TimeZone

    @Mock lateinit var intent: Intent
    @Mock lateinit var pump: PumpWithConcentration

    @BeforeEach
    fun setUp() {
        defaultTimeZone = TimeZone.getDefault()
        // Override the TestPumpPlugin wiring from the base with a verifiable mock pump.
        whenever(activePlugin.activePump).thenReturn(pump)
        sut = createReceiver()
    }

    @AfterEach
    fun restoreTimeZone() {
        // DST tests mutate the JVM default timezone; restore it so other tests are unaffected.
        TimeZone.setDefault(defaultTimeZone)
    }

    // Unconfined dispatcher runs the launched coroutine synchronously so the suspend pump call is
    // observable right after processIntent() returns.
    private fun createReceiver() = TimeDateOrTZChangeReceiver().also {
        it.aapsLogger = aapsLogger
        it.activePlugin = activePlugin
        it.appScope = CoroutineScope(Dispatchers.Unconfined)
    }

    @Test
    fun `timezone change notifies pump with TimezoneChanged`() {
        whenever(intent.action).thenReturn(Intent.ACTION_TIMEZONE_CHANGED)

        sut.processIntent(intent)

        verifyBlocking(pump) { timezoneOrDSTChanged(TimeChangeType.TimezoneChanged) }
    }

    @Test
    fun `manual time change (no DST transition) notifies pump with TimeChanged`() {
        // The receiver computes isDST at construction and again on ACTION_TIME_CHANGED using the
        // default timezone. Within a single test the timezone does not change, so currentDst == isDST
        // and the manual-time-change branch is taken deterministically.
        whenever(intent.action).thenReturn(Intent.ACTION_TIME_CHANGED)

        sut.processIntent(intent)

        verifyBlocking(pump) { timezoneOrDSTChanged(TimeChangeType.TimeChanged) }
    }

    @Test
    fun `time change entering DST notifies pump with DSTStarted`() {
        // Construct under a non-DST zone (isDST = false), then evaluate under a zone currently in DST.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val receiver = createReceiver()
        TimeZone.setDefault(timeZoneCurrentlyInDst())
        whenever(intent.action).thenReturn(Intent.ACTION_TIME_CHANGED)

        receiver.processIntent(intent)

        verifyBlocking(pump) { timezoneOrDSTChanged(TimeChangeType.DSTStarted) }
    }

    @Test
    fun `time change leaving DST notifies pump with DSTEnded`() {
        // Construct under a zone currently in DST (isDST = true), then evaluate under a non-DST zone.
        TimeZone.setDefault(timeZoneCurrentlyInDst())
        val receiver = createReceiver()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        whenever(intent.action).thenReturn(Intent.ACTION_TIME_CHANGED)

        receiver.processIntent(intent)

        verifyBlocking(pump) { timezoneOrDSTChanged(TimeChangeType.DSTEnded) }
    }

    @Test
    fun `null action does not notify pump`() {
        whenever(intent.action).thenReturn(null)

        sut.processIntent(intent)

        verifyBlocking(pump, never()) { timezoneOrDSTChanged(any()) }
    }

    @Test
    fun `unknown action does not notify pump`() {
        whenever(intent.action).thenReturn("some.unknown.ACTION")

        sut.processIntent(intent)

        verifyBlocking(pump, never()) { timezoneOrDSTChanged(any()) }
    }

    // Returns a timezone observing DST at the current instant. Northern- and southern-hemisphere
    // zones observe DST in opposite halves of the year, so one of these is always in DST regardless
    // of when the test runs — keeping the DST-transition cases deterministic without faking the clock.
    private fun timeZoneCurrentlyInDst(): TimeZone {
        val nowDate = Date()
        return listOf("Europe/London", "America/New_York", "Australia/Sydney", "Pacific/Auckland", "America/Santiago")
            .map { TimeZone.getTimeZone(it) }
            .first { it.useDaylightTime() && it.inDaylightTime(nowDate) }
    }
}
