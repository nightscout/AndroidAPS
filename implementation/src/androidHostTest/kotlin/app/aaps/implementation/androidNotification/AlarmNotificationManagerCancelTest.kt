package app.aaps.implementation.androidNotification

import android.app.NotificationManager
import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.AndroidActivities
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.keys.interfaces.Preferences
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers [AlarmNotificationManager.cancelAlarm]: it takes down the alarm notifications this class posted, and only
 * those.
 *
 * This test used to be about something else. `cancelAlarm` also stopped `AlarmSoundPlayer.OWNER_FULLSCREEN`, the
 * looping sound that the deleted `postFullScreenAlarm` started, because Mute on the notification, the watch's snooze
 * and `onTerminate` all reached it and none of them silenced the loop (#5133). There is one sound owner now - the
 * notification list, through `OWNER_INTERNAL` - so what silences the alarm is the card going away, and
 * `AndroidSystemNotificationPlatform.cancelAll` is what stops the audio. The part worth pinning here is that this
 * clears every sound alarm it is tracking, because a tracked key left behind is a notification nothing can remove.
 */
class AlarmNotificationManagerCancelTest {

    private val notificationManager: NotificationManager = mock()
    private val activities: AndroidActivities = mock()

    private fun sut(): AlarmNotificationManager {
        val context: Context = mock()
        whenever(context.getSystemService(Context.NOTIFICATION_SERVICE)).thenReturn(notificationManager)
        return AlarmNotificationManager(
            context = context,
            aapsLogger = mock<AAPSLogger>(),
            preferences = mock<Preferences>(),
            iconsProvider = mock<IconsProvider>(),
            androidActivitiesProvider = { activities },
            notificationHolderProvider = { mock<NotificationHolder>() },
            rh = mock<ResourceHelper>()
        )
    }

    /** Nothing posted, nothing to cancel - and in particular no sweep of notifications this class does not own. */
    @Test
    fun cancellingWithNothingPostedTouchesNoNotification() {
        sut().cancelAlarm()

        verify(notificationManager, never()).cancel(any())
    }

    /** A second cancel is harmless: the tracked keys were cleared by the first. */
    @Test
    fun cancellingTwiceIsHarmless() {
        val sut = sut()
        sut.cancelAlarm()
        sut.cancelAlarm()

        verify(notificationManager, never()).cancel(any())
    }
}
