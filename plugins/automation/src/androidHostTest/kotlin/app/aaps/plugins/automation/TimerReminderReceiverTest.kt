package app.aaps.plugins.automation

import app.aaps.core.keys.interfaces.TextRef
import android.app.Application
import android.content.Intent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.di.MetroMemberInjector
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.shared.tests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the receiver that rings a reminder scheduled through
 * [app.aaps.core.interfaces.alerts.ReminderScheduler].
 *
 * The receiver now lives in this module, beside the automation code that schedules it. It used to be
 * in `:app`, because Dagger answers `@Inject lateinit` with generated Java and a multiplatform module
 * has no Java compile step. Metro generates no Java, so that restriction is gone.
 */
class TimerReminderReceiverTest : TestBase() {

    /** A `Context` that is also the injector, which is what `MetroBroadcastReceiver` looks for. */
    abstract class InjectingApplication : Application(), MetroMemberInjector

    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var config: Config
    @Mock lateinit var context: InjectingApplication

    private lateinit var sut: TimerReminderReceiver

    @BeforeEach
    fun prepare() {
        // MetroBroadcastReceiver injects via context.applicationContext in super.onReceive().
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.injectMembers(any())).thenAnswer { invocation ->
            (invocation.arguments[0] as? TimerReminderReceiver)?.let {
                it.uiInteraction = uiInteraction
                it.rh = rh
                it.config = config
                it.aapsLogger = aapsLogger
            }
            true
        }
        whenever(config.appName).thenReturn(TextRef.Literal("AAPS"))
        whenever(rh.gs(TextRef.Literal("AAPS"))).thenReturn("AAPS")
        sut = TimerReminderReceiver()
    }

    @Test
    fun ringsWithTheScheduledText() {
        val intent = mock<Intent>()
        whenever(intent.getStringExtra(eq(TimerReminderReceiver.EXTRA_TEXT))).thenReturn("Time to eat")

        sut.onReceive(context, intent)

        verify(uiInteraction).runAlarm(status = "Time to eat", title = "AAPS", sound = AlarmSound.ALARM)
    }

    @Test
    fun fallsBackToAppNameWhenTextMissing() {
        val intent = mock<Intent>()
        whenever(intent.getStringExtra(eq(TimerReminderReceiver.EXTRA_TEXT))).thenReturn(null)

        sut.onReceive(context, intent)

        verify(uiInteraction).runAlarm(status = "AAPS", title = "AAPS", sound = AlarmSound.ALARM)
    }

    @Test
    fun fallsBackToAppNameWhenTextIsBlank() {
        // A blank alarm would ring with an empty body, which reads as a bug to the user.
        val intent = mock<Intent>()
        whenever(intent.getStringExtra(eq(TimerReminderReceiver.EXTRA_TEXT))).thenReturn("   ")

        sut.onReceive(context, intent)

        verify(uiInteraction).runAlarm(status = "AAPS", title = "AAPS", sound = AlarmSound.ALARM)
    }
}
