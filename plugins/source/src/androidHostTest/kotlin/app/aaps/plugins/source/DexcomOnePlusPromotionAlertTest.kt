package app.aaps.plugins.source

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.aaps.core.interfaces.notifications.NotificationHolder
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The status-bar alert that [DexcomOnePlusWarmupNotification.alert] posts for a promotion problem. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DexcomOnePlusPromotionAlertTest {

    @Test
    fun `bound failure notification shows the text and the action`() {
        val context = RuntimeEnvironment.getApplication()
        val text = context.getString(R.string.dexcom_oneplus_staging_promote_bound_failed)

        DexcomOnePlusWarmupNotification(context).alert(text)

        val manager = context.getSystemService(NotificationManager::class.java)
        val posted = shadowOf(manager).allNotifications.single()
        val shown = posted.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertThat(shown).isEqualTo(text)
        assertThat(shown).contains("older calibration entries could not be ignored")
        assertThat(shown).contains("Remove finger prick calibrations taken before this switch")
    }

    @Test
    fun `the session service shows the app's notification and adds none of its own`() {
        val context = RuntimeEnvironment.getApplication()
        val alertText = context.getString(R.string.dexcom_oneplus_staging_promote_bound_failed)
        val appNotification = NotificationCompat.Builder(context, APP_CHANNEL).setContentText(APP_TEXT).build()
        val holder = object : NotificationHolder {
            override val channelID = APP_CHANNEL
            override val notificationID = APP_NOTIFICATION_ID
            override var notification: Notification = appNotification
            override fun openAppIntent(): PendingIntent? = null
            override fun createNotificationChannel() = Unit
        }

        DexcomOnePlusWarmupNotification(context).alert(alertText)
        // Not create(): that asks the app for the injected fields, and the test app has none.
        val service = Robolectric.buildService(DexcomOnePlusSessionService::class.java).get()
        service.notificationHolder = holder
        service.onStartCommand(Intent(context, DexcomOnePlusSessionService::class.java), 0, 1)

        // In the foreground with the app's own notification.
        assertThat(shadowOf(service).lastForegroundNotificationId).isEqualTo(APP_NOTIFICATION_ID)
        assertThat(shadowOf(service).lastForegroundNotification).isSameInstanceAs(appNotification)
        // The alert stays, and the service posted nothing but the app's notification.
        val shown = shadowOf(context.getSystemService(NotificationManager::class.java))
            .allNotifications
            .map { notification ->
                notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            }
        assertThat(shown).containsExactly(alertText, APP_TEXT)
    }

    private companion object {

        const val APP_CHANNEL = "app"
        const val APP_NOTIFICATION_ID = 4711
        const val APP_TEXT = "the app's own notification"
    }
}
