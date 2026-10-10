package app.aaps.implementation.androidNotification

import android.app.NotificationManager
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers [AlarmNotificationManager.cancelAlarm].
 *
 * The alarm the user hears is not the notification. `postFullScreenAlarm` starts a looping sound
 * through [AlarmSoundPlayer] under [AlarmSoundPlayer.OWNER_FULLSCREEN], and that playback carries on
 * after the notification is cancelled unless it is stopped on purpose.
 *
 * `cancelAlarm` is the single path behind Mute on the notification, the Wear snooze gesture and
 * `onTerminate`. None of them silenced the loop, so the notification vanished and the alarm kept
 * sounding with nothing left on screen to stop it - issue #5133.
 */
class AlarmNotificationManagerCancelTest {

    private val notificationManager: NotificationManager = mock()
    private val alarmSoundPlayer: AlarmSoundPlayer = mock()

    private fun sut(): AlarmNotificationManager {
        val context: Context = mock()
        whenever(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
        return AlarmNotificationManager(
            context = context,
            aapsLogger = mock<AAPSLogger>(),
            preferences = mock<Preferences>(),
            iconsProvider = mock<IconsProvider>(),
            uiInteractionProvider = { mock<UiInteraction>() },
            alarmSoundPlayer = alarmSoundPlayer,
            rh = mock<ResourceHelper>()
        )
    }

    /** The point of the fix: the sound stops, not just the notification. */
    @Test
    fun cancellingTheAlarmStopsTheFullScreenAudio() {
        sut().cancelAlarm()

        verify(alarmSoundPlayer).stop(AlarmSoundPlayer.OWNER_FULLSCREEN)
    }

    @Test
    fun cancellingTheAlarmStillRemovesTheFullScreenNotification() {
        sut().cancelAlarm()

        verify(notificationManager).cancel(AlarmNotificationManager.NOTIFICATION_ID_FULL_SCREEN)
    }
}
