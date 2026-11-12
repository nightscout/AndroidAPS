package app.aaps.plugins.source

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import app.aaps.core.interfaces.source.SensorSlot
import app.aaps.plugins.libre3.Libre3WarmupState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The pre-soak message uses its own id, so it cannot replace the message about the sensor that
 * feeds the loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class Libre3WarmupNotificationSlotTest {

    private val application: Application get() = RuntimeEnvironment.getApplication()

    @Test
    fun `the pre-soak message is posted under id 4472 and production stays on 4471`() {
        val production = Libre3WarmupNotification(application, SensorSlot.PRODUCTION)
        val presoak = Libre3WarmupNotification(application, SensorSlot.STAGING)
        val pairing = Libre3WarmupState(phase = Libre3WarmupState.Phase.PAIRING)

        production.update(pairing)
        presoak.update(pairing)

        val manager = application.getSystemService(NotificationManager::class.java)
        val ids = manager.activeNotifications.map { it.id }.toSet()
        assertThat(ids).contains(4471)
        assertThat(ids).contains(4472)
    }

    @Test
    fun `a promotion problem is posted under id 4474 and does not reuse the warm-up ids`() {
        val text = application.getString(R.string.libre3_presoak_promote_bound_failed)

        Libre3WarmupNotification(application).alert(text)

        val manager = application.getSystemService(NotificationManager::class.java)
        val posted = manager.activeNotifications.single()
        assertThat(posted.id).isEqualTo(4474)
        assertThat(posted.id).isNotEqualTo(4471)
        assertThat(posted.id).isNotEqualTo(4472)
        assertThat(posted.id).isNotEqualTo(4473)
        assertThat(posted.id).isNotEqualTo(8932)
        assertThat(posted.id).isNotEqualTo(8933)
        assertThat(posted.notification.channelId).isEqualTo(Libre3WarmupNotification.CHANNEL_ID)
        val channel = manager.getNotificationChannel(Libre3WarmupNotification.CHANNEL_ID)
        assertThat(channel.importance).isEqualTo(LIBRE3_SENSOR_STATUS_CHANNEL_IMPORTANCE)
        val shown = posted.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
        assertThat(shown).isEqualTo(text)
        assertThat(shown).contains("old calibration entries could not be ignored")
    }
}
