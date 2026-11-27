package app.aaps.implementation.androidNotification

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.IconsProvider
import app.aaps.core.interfaces.ui.UiInteraction
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The placeholder notification, and the intent behind a tap on an AAPS notification.
 *
 * The intent has to be the same intent the launcher icon sends. Then Android brings a minimized AAPS back as it
 * is. It used to come from `TaskStackBuilder`, which adds `FLAG_ACTIVITY_CLEAR_TASK`: every tap
 * destroyed the running task, and the overview was built and calculated again from zero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationHolderImplTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun sut(): NotificationHolderImpl {
        val uiInteraction: UiInteraction = mock()
        whenever(uiInteraction.mainActivity).thenReturn(Activity::class)
        return NotificationHolderImpl(
            rh = mock<ResourceHelper>(),
            context = context,
            iconsProvider = mock<IconsProvider>(),
            uiInteraction = uiInteraction
        )
    }

    @Test
    fun openAppIntentIsTheLauncherIntent() {
        val pendingIntent = shadowOf(checkNotNull(sut().openAppIntent()))

        assertThat(pendingIntent.isActivityIntent).isTrue()
        // One intent, not a built back stack.
        assertThat(pendingIntent.savedIntents).hasLength(1)
        val intent = pendingIntent.savedIntent
        assertThat(intent.component?.className).isEqualTo(Activity::class.java.name)
        assertThat(intent.action).isEqualTo(Intent.ACTION_MAIN)
        assertThat(intent.categories).containsExactly(Intent.CATEGORY_LAUNCHER)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    /**
     * Every service that starts in the foreground reads the notification in Service.onCreate, on the main thread, and
     * some read it twice. The placeholder was built anew at each read, its channel created and the notification posted
     * again: binder calls on the main thread at app start (ANR, Crashlytics 2026-11). It is built once, and a read posts
     * nothing, because startForeground shows it.
     */
    @Test
    fun thePlaceholderIsBuiltOnceAndAReadPostsNothing() {
        val holder = sut()

        val first = holder.notification

        assertThat(holder.notification).isSameInstanceAs(first)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertThat(shadowOf(manager).allNotifications).isEmpty()
    }

    @Test
    fun theRealNotificationReplacesThePlaceholder() {
        val holder = sut()
        val placeholder = holder.notification
        val real = Notification()

        holder.notification = real

        assertThat(holder.notification).isSameInstanceAs(real)
        assertThat(holder.notification).isNotSameInstanceAs(placeholder)
    }

    @Test
    fun openAppIntentDoesNotClearTheRunningTask() {
        val intent = shadowOf(checkNotNull(sut().openAppIntent())).savedIntent

        assertThat(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK).isEqualTo(0)
        assertThat(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP).isEqualTo(0)
    }
}
