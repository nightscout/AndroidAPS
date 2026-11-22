package app.aaps.plugins.automation

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * The Alarm action of automation as a booked alert (`_docs/ALARMS.md`, step 1e). It used to be an Android receiver of
 * its own and, on iOS, only a notification; the ringing itself is the core's, see `AlertsImplTest`.
 */
class AlertReminderSchedulerTest : TestBase() {

    private val uiInteraction: UiInteraction = mock()
    private val rh: TextResolver = mock()
    private val bus: RxBus = mock()
    private val config: Config = mock()
    private val dateUtil: DateUtil = mock()

    private lateinit var sut: AlertReminderScheduler

    @BeforeEach
    fun setUp() {
        whenever(dateUtil.now()).thenReturn(1_000_000L)
        whenever(config.appName).thenReturn(TextRef.Literal("AAPS"))
        doAnswer { (it.arguments[0] as? TextRef.Literal)?.text ?: "text" }.whenever(rh).gs(any<TextRef>())
        whenever(uiInteraction.scheduleAlarm(any(), any(), any(), any(), anyOrNull())).thenReturn(true)
        sut = AlertReminderScheduler(uiInteraction, rh, bus, config, dateUtil, aapsLogger)
    }

    @Test
    fun `a reminder is the app's automation alarm booked for its time`() {
        sut.scheduleReminder(10, "Check BG")

        verify(uiInteraction).scheduleAlarm(
            eq(1_000_000L + 10_000L), eq(UiInteraction.KEY_AUTOMATION_ALARM), eq("Check BG"), eq("AAPS"), eq(AlarmSound.ALARM)
        )
        verify(bus, never()).send(any())
    }

    /** An empty alarm shows the app's name, as it always did. */
    @Test
    fun `an empty reminder says the app's name`() {
        sut.scheduleReminder(10, " ")

        verify(uiInteraction).scheduleAlarm(any(), any(), eq("AAPS"), eq("AAPS"), eq(AlarmSound.ALARM))
    }

    /** Android without exact alarms: the user is told, as before. */
    @Test
    fun `a reminder the phone cannot book is reported`() {
        whenever(uiInteraction.scheduleAlarm(any(), any(), any(), any(), anyOrNull())).thenReturn(false)

        sut.scheduleReminder(10, "Check BG")

        verify(bus).send(any<EventShowSnackbar>())
    }
}
