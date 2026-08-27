package app.aaps.implementation.notifications

import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

/**
 * Covers the notification registry, which is the part that is the same on every platform.
 *
 * The system tray is a recording fake rather than a mock, because most of what matters here is the
 * *order* of what reaches the platform - which notification is cancelled before which is shown, and
 * which alarm is handed the sound afterwards.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommonNotificationManagerTest : TestBase() {

    @Mock lateinit var rh: TextResolver

    /** Records what the platform was asked to do, in order. */
    private class RecordingPlatform : SystemNotificationPlatform {

        val calls = mutableListOf<String>()
        var audibleKey: Int? = null
        var audibleSound: AlarmSound? = null
        var audibleCallCount = 0

        override fun show(instanceKey: Int, title: String, text: String, urgent: Boolean) {
            calls.add("show:$instanceKey:$text:urgent=$urgent")
        }

        override fun cancel(instanceKey: Int) {
            calls.add("cancel:$instanceKey")
        }

        override fun cancelAll() {
            calls.add("cancelAll")
        }

        override fun setAudibleAlarm(instanceKey: Int?, sound: AlarmSound?) {
            audibleKey = instanceKey
            audibleSound = sound
            audibleCallCount++
            calls.add("audible:$instanceKey")
        }

        override fun onDismissed(callback: (Int) -> Unit) {}
    }

    private val platform = RecordingPlatform()

    private fun createSut(): CommonNotificationManager {
        // The registry resolves a title for every post. Tests that care about a specific TextRef
        // stub it after this call, so their narrower stubbing wins.
        whenever(rh.gs(any<TextRef>())).thenReturn("title")
        return CommonNotificationManager(aapsLogger, rh, platform, TestScope(UnconfinedTestDispatcher()))
    }

    // ---------------------------------------------------------------------------------------------
    // The registry
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a posted notification shows up in the list and on the platform`() = runTest {
        val sut = createSut()

        val handle = sut.post(NotificationId.TOAST_ALARM, "over the limit")

        assertThat(sut.notifications.value.map { it.text }).containsExactly("over the limit")
        assertThat(platform.calls).contains("show:${handle.instanceKey}:over the limit:urgent=true")
    }

    /** allowMultiple = false, so a second post of the same id replaces the first rather than adding. */
    @Test
    fun `reposting the same id replaces instead of stacking`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "first")

        sut.post(NotificationId.TOAST_ALARM, "second")

        assertThat(sut.notifications.value.map { it.text }).containsExactly("second")
    }

    /** allowMultiple = true, so these are separate notifications with separate keys. */
    @Test
    fun `an id that allows multiple keeps both`() = runTest {
        val sut = createSut()

        val first = sut.post(NotificationId.AUTOMATION_MESSAGE, "one")
        val second = sut.post(NotificationId.AUTOMATION_MESSAGE, "two")

        assertThat(first.instanceKey).isNotEqualTo(second.instanceKey)
        assertThat(sut.notifications.value.map { it.text }).containsExactly("one", "two")
    }

    @Test
    fun `the list is ordered by level, most severe first`() = runTest {
        val sut = createSut()

        sut.post(NotificationId.SCENE_ENDED, "info", level = NotificationLevel.INFO)
        sut.post(NotificationId.TOAST_ALARM, "urgent", level = NotificationLevel.URGENT)

        assertThat(sut.notifications.value.map { it.text }).containsExactly("urgent", "info").inOrder()
    }

    @Test
    fun `dismissing by handle removes only that instance`() = runTest {
        val sut = createSut()
        val first = sut.post(NotificationId.AUTOMATION_MESSAGE, "one")
        sut.post(NotificationId.AUTOMATION_MESSAGE, "two")

        sut.dismiss(first)

        assertThat(sut.notifications.value.map { it.text }).containsExactly("two")
        assertThat(platform.calls).contains("cancel:${first.instanceKey}")
    }

    @Test
    fun `dismissing by id removes every instance of it`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.AUTOMATION_MESSAGE, "one")
        sut.post(NotificationId.AUTOMATION_MESSAGE, "two")

        sut.dismiss(NotificationId.AUTOMATION_MESSAGE)

        assertThat(sut.notifications.value).isEmpty()
    }

    /** Dismissing something that is not there must not touch the list or the platform. */
    @Test
    fun `dismissing an absent notification is a no-op`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "kept")
        platform.calls.clear()

        sut.dismiss(NotificationId.AUTOMATION_MESSAGE)

        assertThat(sut.notifications.value.map { it.text }).containsExactly("kept")
        assertThat(platform.calls).isEmpty()
    }

    // ---------------------------------------------------------------------------------------------
    // Expiry
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a notification past its validTo is gone by the next write`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.SCENE_ENDED, "stale", date = 1_000L, validTo = 2_000L)

        // Any later write runs the expiry sweep.
        sut.post(NotificationId.TOAST_ALARM, "fresh")

        assertThat(sut.notifications.value.map { it.text }).containsExactly("fresh")
    }

    @Test
    fun `validTo of zero means it never expires`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.SCENE_ENDED, "forever", date = 1_000L, validTo = 0L)

        sut.cleanUp()

        assertThat(sut.notifications.value.map { it.text }).containsExactly("forever")
    }

    @Test
    fun `a validityCheck that turns false drops the notification`() = runTest {
        var stillValid = true
        val sut = createSut()
        sut.post(NotificationId.SCENE_ENDED, "conditional", validityCheck = { stillValid })
        assertThat(sut.notifications.value).hasSize(1)

        stillValid = false
        sut.cleanUp()

        assertThat(sut.notifications.value).isEmpty()
    }

    // ---------------------------------------------------------------------------------------------
    // Alarm ownership - the part most worth pinning, because it is a handoff and not a flag.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an urgent notification with a sound takes the audio`() = runTest {
        val sut = createSut()

        val handle = sut.post(NotificationId.TOAST_ALARM, "alarm", sound = AlarmSound.ERROR)

        assertThat(platform.audibleKey).isEqualTo(handle.instanceKey)
        assertThat(platform.audibleSound).isEqualTo(AlarmSound.ERROR)
    }

    /** Sound is gated on URGENT: a sound on a lower level is deliberately ignored. */
    @Test
    fun `a sound below urgent never rings`() = runTest {
        val sut = createSut()

        sut.post(NotificationId.SCENE_ENDED, "quiet", level = NotificationLevel.INFO, sound = AlarmSound.ERROR)

        assertThat(platform.audibleKey).isNull()
    }

    /** The whole point of the owner slot: a second alarm must not restart the first one's sound. */
    @Test
    fun `a newer alarm takes over the audio`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "older", date = 1_000L, sound = AlarmSound.ERROR)

        val newer = sut.post(NotificationId.EOFLOW_PATCH_ALERT, "newer", date = 2_000L, sound = AlarmSound.ALARM)

        assertThat(platform.audibleKey).isEqualTo(newer.instanceKey)
        assertThat(platform.audibleSound).isEqualTo(AlarmSound.ALARM)
    }

    /** Dismissing the audible one promotes the next remaining alarm rather than going silent. */
    @Test
    fun `dismissing the sounding alarm promotes the next one`() = runTest {
        val sut = createSut()
        val older = sut.post(NotificationId.EOFLOW_PATCH_ALERT, "older", date = 1_000L, sound = AlarmSound.ERROR)
        val newer = sut.post(NotificationId.EOFLOW_PATCH_ALERT, "newer", date = 2_000L, sound = AlarmSound.ALARM)
        assertThat(platform.audibleKey).isEqualTo(newer.instanceKey)

        sut.dismiss(newer)

        assertThat(platform.audibleKey).isEqualTo(older.instanceKey)
        assertThat(platform.audibleSound).isEqualTo(AlarmSound.ERROR)
    }

    /** Reposting while the same alarm still owns the audio must not re-trigger the sound. */
    @Test
    fun `the owner is not reset while it keeps owning`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "alarm", sound = AlarmSound.ERROR)
        val callsAfterFirst = platform.audibleCallCount

        sut.post(NotificationId.SCENE_ENDED, "unrelated", level = NotificationLevel.INFO)

        assertThat(platform.audibleCallCount).isEqualTo(callsAfterFirst)
    }

    @Test
    fun `the last alarm going away silences the audio`() = runTest {
        val sut = createSut()
        val handle = sut.post(NotificationId.TOAST_ALARM, "alarm", sound = AlarmSound.ERROR)

        sut.dismiss(handle)

        assertThat(platform.audibleKey).isNull()
        assertThat(platform.audibleSound).isNull()
    }

    @Test
    fun `mute all clears audible alarms but leaves quiet notifications`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "loud", sound = AlarmSound.ERROR)
        sut.post(NotificationId.SCENE_ENDED, "quiet", level = NotificationLevel.INFO)

        sut.muteAllAlarms()

        assertThat(sut.notifications.value.map { it.text }).containsExactly("quiet")
        assertThat(platform.audibleKey).isNull()
        assertThat(platform.calls).contains("cancelAll")
    }

    // ---------------------------------------------------------------------------------------------
    // Text
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a TextRef is resolved when it is posted`() = runTest {
        val ref = TextRef.Literal("resolved text")
        val sut = createSut()
        whenever(rh.gs(ref)).thenReturn("resolved text")

        sut.post(NotificationId.SCENE_ENDED, ref)

        assertThat(sut.notifications.value.map { it.text }).containsExactly("resolved text")
    }
}
