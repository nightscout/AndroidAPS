package app.aaps.plugins.source

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
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
        assertThat(shown).contains("Remove fingerstick calibrations taken before this switch")
    }

    @Test
    fun `promotion alert and session service notification coexist`() {
        val context = RuntimeEnvironment.getApplication()
        val alertText = context.getString(R.string.dexcom_oneplus_staging_promote_bound_failed)
        val sessionText = context.getString(R.string.dexcom_oneplus_notif_session_alive)

        DexcomOnePlusWarmupNotification(context).alert(alertText)
        val service = Robolectric.buildService(DexcomOnePlusSessionService::class.java).create().get()
        service.onStartCommand(Intent(context, DexcomOnePlusSessionService::class.java), 0, 1)

        val shown = shadowOf(context.getSystemService(NotificationManager::class.java))
            .allNotifications
            .map { notification ->
                notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            }
        assertThat(shown).contains(alertText)
        assertThat(shown).contains(sessionText)
    }
}
