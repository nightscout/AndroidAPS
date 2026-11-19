package app.aaps.pump.carelevo.common

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import app.aaps.pump.carelevo.ContextResourceHelper
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/** The Android phone notification behind [CarelevoSystemNotifier], against Robolectric's notification manager. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidCarelevoSystemNotifierTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val sut = AndroidCarelevoSystemNotifier(context, ContextResourceHelper(context))

    private val notificationManager: NotificationManager = context.getSystemService(NotificationManager::class.java)
    private val shadow: ShadowNotificationManager = Shadows.shadowOf(notificationManager)

    @Before
    fun setUp() {
        // The test app has no launcher screen; AAPS has one. Without it the tap intent is null, and Robolectric
        // fails on the second notification that updates the same PendingIntent.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(context.packageName)
        Shadows.shadowOf(context.packageManager).addResolveInfoForIntent(
            launcher,
            ResolveInfo().apply {
                activityInfo = ActivityInfo().apply {
                    packageName = context.packageName
                    name = "app.aaps.LaunchScreen"
                }
            }
        )
    }

    @Test
    fun `an alarm is shown on the Carelevo channel with its title and text`() {
        sut.show("alarm-1", "Pump clogged", "Change the patch.")

        assertThat(notificationManager.getNotificationChannel("carelevo_alarm_channel")).isNotNull()
        val notification = shadow.getNotification("alarm-1".hashCode())
        assertThat(notification).isNotNull()
        assertThat(notification.channelId).isEqualTo("carelevo_alarm_channel")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString()).isEqualTo("Pump clogged")
        assertThat(notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("Change the patch.")
    }

    @Test
    fun `a tap on the notification opens AAPS and closes it`() {
        sut.show("alarm-1", "Pump clogged", "Change the patch.")

        val notification = shadow.getNotification("alarm-1".hashCode())
        assertThat(notification.contentIntent).isNotNull()
        assertThat(notification.flags and Notification.FLAG_AUTO_CANCEL).isNotEqualTo(0)
    }

    @Test
    fun `a second call for the same alarm replaces the first`() {
        sut.show("alarm-1", "Pump clogged", "first")
        sut.show("alarm-1", "Pump clogged", "second")

        assertThat(shadow.allNotifications).hasSize(1)
        assertThat(shadow.getNotification("alarm-1".hashCode()).extras.getCharSequence(Notification.EXTRA_TEXT).toString()).isEqualTo("second")
    }

    @Test
    fun `two alarms are two notifications`() {
        sut.show("alarm-1", "Pump clogged", "first")
        sut.show("alarm-2", "Low insulin", "second")

        assertThat(shadow.allNotifications).hasSize(2)
    }
}
