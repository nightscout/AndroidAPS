package app.aaps.receivers

import android.content.Intent
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.shared.tests.TestBase
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import dagger.android.HasAndroidInjector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the receiver that rings a reminder scheduled through
 * [app.aaps.core.interfaces.alerts.ReminderScheduler].
 *
 * It lives in :app rather than in :plugins:automation because a `BroadcastReceiver` cannot carry its
 * injection annotations inside a multiplatform module.
 */
class TimerReminderReceiverTest : TestBase() {

    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var config: Config
    @Mock lateinit var context: DaggerApplication

    private lateinit var sut: TimerReminderReceiver

    @BeforeEach
    fun prepare() {
        // Dagger injects the receiver via context.applicationContext.androidInjector() in super.onReceive()
        val injector = HasAndroidInjector {
            AndroidInjector {
                if (it is TimerReminderReceiver) {
                    it.uiInteraction = uiInteraction
                    it.rh = rh
                    it.config = config
                    it.aapsLogger = aapsLogger
                }
            }
        }
        whenever(context.applicationContext).thenReturn(context)
        whenever(context.androidInjector()).thenReturn(injector.androidInjector())
        whenever(config.appName).thenReturn(1)
        whenever(rh.gs(1)).thenReturn("AAPS")
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
