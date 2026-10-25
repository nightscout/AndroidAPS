package app.aaps.implementation.androidNotification

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.AlarmIntent
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.AlarmSoundPlayer
import app.aaps.core.interfaces.notifications.NotificationHolder
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import app.aaps.core.ui.R as CoreUiR

/**
 * The background alarm from `postFullScreenAlarm`, when the user cannot see its alarm screen (#5338).
 *
 * An automation alarm fired while another app was on top, so Android did not let the alarm screen come
 * up. The user had only the notification and the looping sound. Nothing in the AAPS main screen could
 * stop that sound. Since Android 14 an ongoing notification can be swiped away, and the swipe did
 * nothing, so the sound went on with nothing left on screen to stop it. Only a force stop ended it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmNotificationManagerFullScreenTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun sut(): AlarmNotificationManager {
        val uiInteraction: UiInteraction = mock()
        whenever(uiInteraction.errorHelperActivity).thenReturn(Activity::class)
        whenever(uiInteraction.mainActivity).thenReturn(Activity::class)
        val iconsProvider: IconsProvider = mock()
        whenever(iconsProvider.getNotificationIcon()).thenReturn(android.R.drawable.ic_dialog_alert)
        val rh: ResourceHelper = mock()
        whenever(rh.gs(CoreUiR.string.mute)).thenReturn("Mute")
        return AlarmNotificationManager(
            context = context,
            aapsLogger = mock<AAPSLogger>(),
            preferences = mock<Preferences>(),
            iconsProvider = iconsProvider,
            uiInteractionProvider = { uiInteraction },
            notificationHolderProvider = { mock<NotificationHolder>() },
            alarmSoundPlayer = mock<AlarmSoundPlayer>(),
            rh = rh
        )
    }

    private fun postedNotification(): Notification =
        checkNotNull(
            shadowOf(context.getSystemService(NotificationManager::class.java))
                .getNotification(AlarmNotificationManager.NOTIFICATION_ID_FULL_SCREEN)
        )

    @Test
    fun swipingTheNotificationAwayMutesTheAlarm() {
        sut().postFullScreenAlarm(status = "Check BG", title = "AAPS", sound = AlarmSound.ALARM)

        val swipe = shadowOf(checkNotNull(postedNotification().deleteIntent))
        assertThat(swipe.isBroadcast).isTrue()
        assertThat(swipe.savedIntent.component?.className).isEqualTo(AlarmMuteReceiver::class.java.name)
        assertThat(swipe.savedIntent.getBooleanExtra(AlarmMuteReceiver.EXTRA_SWIPED, false)).isTrue()
    }

    /**
     * The swipe and the Mute button send the same intent apart from the extra. If both used one
     * PendingIntent, the second would overwrite the extra of the first.
     */
    @Test
    fun theMuteButtonIsNotReportedAsASwipe() {
        sut().postFullScreenAlarm(status = "Check BG", title = "AAPS", sound = AlarmSound.ALARM)

        val mute = shadowOf(checkNotNull(postedNotification().actions.single().actionIntent))
        assertThat(mute.savedIntent.component?.className).isEqualTo(AlarmMuteReceiver::class.java.name)
        assertThat(mute.savedIntent.getBooleanExtra(AlarmMuteReceiver.EXTRA_SWIPED, false)).isFalse()
    }

    @Test
    fun theAlarmScreenCanBeOpenedAgainUntilTheAlarmIsAnswered() {
        val sut = sut()
        assertThat(sut.unansweredAlarmIntent()).isNull()

        sut.postFullScreenAlarm(status = "Check BG", title = "AAPS", sound = AlarmSound.ALARM)

        val intent = checkNotNull(sut.unansweredAlarmIntent())
        assertThat(intent.component?.className).isEqualTo(Activity::class.java.name)
        assertThat(intent.getStringExtra(AlarmIntent.EXTRA_STATUS)).isEqualTo("Check BG")
        assertThat(intent.getStringExtra(AlarmIntent.EXTRA_SOUND)).isEqualTo(AlarmSound.ALARM.name)

        // Every way of answering the alarm ends in cancelAlarm.
        sut.cancelAlarm()

        assertThat(sut.unansweredAlarmIntent()).isNull()
    }
}
