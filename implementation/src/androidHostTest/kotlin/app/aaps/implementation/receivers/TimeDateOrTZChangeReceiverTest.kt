package app.aaps.implementation.receivers

import android.content.Intent
import app.aaps.core.data.pump.defs.TimeChangeType
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.rx.events.EventTimeZoneChanged
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        it.rxBus = rxBus
    }

    /** Collects every EventTimeZoneChanged sent while the test runs. */
    private fun collectTimeZoneEvents(): List<EventTimeZoneChanged> {
        val events = mutableListOf<EventTimeZoneChanged>()
        CoroutineScope(Dispatchers.Unconfined).launch { rxBus.toFlow(EventTimeZoneChanged::class).collect { events += it } }
        return events
    }

    @Test
    fun `timezone change notifies pump with TimezoneChanged`() {
        whenever(intent.action).thenReturn(Intent.ACTION_TIMEZONE_CHANGED)

        sut.processIntent(intent)

        verifyBlocking(pump) { timezoneOrDSTChanged(TimeChangeType.TimezoneChanged) }
    }

    /** The IOB calculation reads the basal profile in the current zone, so its cache must be reset. */
    @Test
    fun `timezone change tells the app with EventTimeZoneChanged`() {
        val events = collectTimeZoneEvents()
        whenever(intent.action).thenReturn(Intent.ACTION_TIMEZONE_CHANGED)

        sut.processIntent(intent)

        assertThat(events).hasSize(1)
    }

    /** A clock change keeps every past timestamp on the same time of day, so nothing to reset. */
    @Test
    fun `manual time change does not send EventTimeZoneChanged`() {
        val events = collectTimeZoneEvents()
        whenever(intent.action).thenReturn(Intent.ACTION_TIME_CHANGED)

        sut.processIntent(intent)

        assertThat(events).isEmpty()
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

    /**
     * A DST switch keeps the zone, and the zone rules give every past moment its own offset, so the
     * IOB of the past does not change and no reset is needed.
     */
    @Test
    fun `a DST switch does not send EventTimeZoneChanged`() {
        val events = collectTimeZoneEvents()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val receiver = createReceiver()
        TimeZone.setDefault(timeZoneCurrentlyInDst())
        whenever(intent.action).thenReturn(Intent.ACTION_TIME_CHANGED)

        receiver.processIntent(intent)

        verifyBlocking(pump) { timezoneOrDSTChanged(TimeChangeType.DSTStarted) }
        assertThat(events).isEmpty()
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
