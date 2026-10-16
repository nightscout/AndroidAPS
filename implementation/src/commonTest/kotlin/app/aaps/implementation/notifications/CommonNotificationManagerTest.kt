package app.aaps.implementation.notifications

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.SystemNotificationPlatform
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the notification registry, which is the part that is the same on every platform.
 *
 * In `commonTest` rather than `androidHostTest` on purpose: this class was moved to `commonMain` so
 * that iOS could use it, and a test that only runs on the JVM would say nothing about that. Running
 * here means it runs on both the JVM and the iOS simulator. The cost is no Mockito, which is JVM
 * only - hence the hand written fakes below, which suit this test anyway because most of what
 * matters is the *order* of what reaches the platform.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CommonNotificationManagerTest {

    /** Records what the platform was asked to do, in order. */
    private class RecordingPlatform : SystemNotificationPlatform {

        val calls = mutableListOf<String>()
        var audibleKey: Int? = null
        var audibleSound: AlarmSound? = null
        var audibleCallCount = 0

        /** The last notification handed over, so tests can assert on fields Android decides with. */
        var lastShown: AapsNotification? = null

        override fun show(notification: AapsNotification, title: String) {
            lastShown = notification
            val urgent = notification.level == NotificationLevel.URGENT
            calls.add("show:${notification.instanceKey}:${notification.text}:urgent=$urgent")
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

    /** Returns the literal for a [TextRef.Literal] and a fixed marker for anything else. */
    private object FakeTextResolver : TextResolver {

        override fun gs(ref: TextRef): String = (ref as? TextRef.Literal)?.text ?: "title"
        override fun gs(ref: TextRef, vararg args: Any?): String = gs(ref)
        override fun gsNotLocalised(ref: TextRef): String = gs(ref)
        override fun shortTextMode(): Boolean = false
    }

    private object SilentLogger : AAPSLogger {

        override fun debug(message: String) {}
        override fun debug(enable: Boolean, tag: LTag, message: String) {}
        override fun debug(tag: LTag, message: String) {}
        override fun debug(tag: LTag, accessor: () -> String) {}
        override fun debug(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun warn(tag: LTag, message: String) {}
        override fun warn(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun info(tag: LTag, message: String) {}
        override fun info(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(tag: LTag, message: String) {}
        override fun error(tag: LTag, message: String, throwable: Throwable) {}
        override fun error(tag: LTag, format: String, vararg arguments: Any?) {}
        override fun error(message: String) {}
        override fun error(message: String, throwable: Throwable) {}
        override fun error(format: String, vararg arguments: Any?) {}
        override fun debug(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun info(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun warn(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
        override fun error(className: String, methodName: String, lineNumber: Int, tag: LTag, message: String) {}
    }

    private val platform = RecordingPlatform()

    private fun createSut() =
        CommonNotificationManager(SilentLogger, FakeTextResolver, platform, TestScope(UnconfinedTestDispatcher()))

    private fun texts(sut: CommonNotificationManager) = sut.notifications.value.map { it.text }

    // ---------------------------------------------------------------------------------------------
    // The registry
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a posted notification shows up in the list and on the platform`() = runTest {
        val sut = createSut()

        val handle = sut.post(NotificationId.TOAST_ALARM, "over the limit")

        assertEquals(listOf("over the limit"), texts(sut))
        assertContains(platform.calls, "show:${handle.instanceKey}:over the limit:urgent=true")
    }

    /** allowMultiple = false, so a second post of the same id replaces the first rather than adding. */
    @Test
    fun `reposting the same id replaces instead of stacking`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "first")

        sut.post(NotificationId.TOAST_ALARM, "second")

        assertEquals(listOf("second"), texts(sut))
    }

    /** allowMultiple = true, so these are separate notifications with separate keys. */
    @Test
    fun `an id that allows multiple keeps both`() = runTest {
        val sut = createSut()

        val first = sut.post(NotificationId.AUTOMATION_MESSAGE, "one")
        val second = sut.post(NotificationId.AUTOMATION_MESSAGE, "two")

        assertNotEquals(first.instanceKey, second.instanceKey)
        assertEquals(listOf("one", "two"), texts(sut))
    }

    @Test
    fun `the list is ordered by level - most severe first`() = runTest {
        val sut = createSut()

        sut.post(NotificationId.SCENE_ENDED, "info", level = NotificationLevel.INFO)
        sut.post(NotificationId.TOAST_ALARM, "urgent", level = NotificationLevel.URGENT)

        assertEquals(listOf("urgent", "info"), texts(sut))
    }

    @Test
    fun `dismissing by handle removes only that instance`() = runTest {
        val sut = createSut()
        val first = sut.post(NotificationId.AUTOMATION_MESSAGE, "one")
        sut.post(NotificationId.AUTOMATION_MESSAGE, "two")

        sut.dismiss(first)

        assertEquals(listOf("two"), texts(sut))
        assertContains(platform.calls, "cancel:${first.instanceKey}")
    }

    @Test
    fun `dismissing by id removes every instance of it`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.AUTOMATION_MESSAGE, "one")
        sut.post(NotificationId.AUTOMATION_MESSAGE, "two")

        sut.dismiss(NotificationId.AUTOMATION_MESSAGE)

        assertTrue(sut.notifications.value.isEmpty())
    }

    /** Dismissing something that is not there must not touch the list or the platform. */
    @Test
    fun `dismissing an absent notification is a no-op`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "kept")
        platform.calls.clear()

        sut.dismiss(NotificationId.AUTOMATION_MESSAGE)

        assertEquals(listOf("kept"), texts(sut))
        assertTrue(platform.calls.isEmpty())
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

        assertEquals(listOf("fresh"), texts(sut))
    }

    @Test
    fun `validTo of zero means it never expires`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.SCENE_ENDED, "forever", date = 1_000L, validTo = 0L)

        sut.cleanUp()

        assertEquals(listOf("forever"), texts(sut))
    }

    @Test
    fun `a validityCheck that turns false drops the notification`() = runTest {
        var stillValid = true
        val sut = createSut()
        sut.post(NotificationId.SCENE_ENDED, "conditional", validityCheck = { stillValid })
        assertEquals(1, sut.notifications.value.size)

        stillValid = false
        sut.cleanUp()

        assertTrue(sut.notifications.value.isEmpty())
    }

    // ---------------------------------------------------------------------------------------------
    // Alarm ownership - the part most worth pinning, because it is a handoff and not a flag.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `an urgent notification with a sound takes the audio`() = runTest {
        val sut = createSut()

        val handle = sut.post(NotificationId.TOAST_ALARM, "alarm", sound = AlarmSound.ERROR)

        assertEquals(handle.instanceKey, platform.audibleKey)
        assertEquals(AlarmSound.ERROR, platform.audibleSound)
    }

    /** Sound is gated on URGENT: a sound on a lower level is deliberately ignored. */
    @Test
    fun `a sound below urgent never rings`() = runTest {
        val sut = createSut()

        sut.post(NotificationId.SCENE_ENDED, "quiet", level = NotificationLevel.INFO, sound = AlarmSound.ERROR)

        assertNull(platform.audibleKey)
    }

    /** The whole point of the owner slot: a second alarm must not restart the first one's sound. */
    @Test
    fun `a newer alarm takes over the audio`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "older", date = 1_000L, sound = AlarmSound.ERROR)

        val newer = sut.post(NotificationId.EOFLOW_PATCH_ALERT, "newer", date = 2_000L, sound = AlarmSound.ALARM)

        assertEquals(newer.instanceKey, platform.audibleKey)
        assertEquals(AlarmSound.ALARM, platform.audibleSound)
    }

    /** Dismissing the audible one promotes the next remaining alarm rather than going silent. */
    @Test
    fun `dismissing the sounding alarm promotes the next one`() = runTest {
        val sut = createSut()
        val older = sut.post(NotificationId.EOFLOW_PATCH_ALERT, "older", date = 1_000L, sound = AlarmSound.ERROR)
        val newer = sut.post(NotificationId.EOFLOW_PATCH_ALERT, "newer", date = 2_000L, sound = AlarmSound.ALARM)
        assertEquals(newer.instanceKey, platform.audibleKey)

        sut.dismiss(newer)

        assertEquals(older.instanceKey, platform.audibleKey)
        assertEquals(AlarmSound.ERROR, platform.audibleSound)
    }

    /** Reposting while the same alarm still owns the audio must not re-trigger the sound. */
    @Test
    fun `the owner is not reset while it keeps owning`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "alarm", sound = AlarmSound.ERROR)
        val callsAfterFirst = platform.audibleCallCount

        sut.post(NotificationId.SCENE_ENDED, "unrelated", level = NotificationLevel.INFO)

        assertEquals(callsAfterFirst, platform.audibleCallCount)
    }

    @Test
    fun `the last alarm going away silences the audio`() = runTest {
        val sut = createSut()
        val handle = sut.post(NotificationId.TOAST_ALARM, "alarm", sound = AlarmSound.ERROR)

        sut.dismiss(handle)

        assertNull(platform.audibleKey)
        assertNull(platform.audibleSound)
    }

    @Test
    fun `mute all clears audible alarms but leaves quiet notifications`() = runTest {
        val sut = createSut()
        sut.post(NotificationId.TOAST_ALARM, "loud", sound = AlarmSound.ERROR)
        sut.post(NotificationId.SCENE_ENDED, "quiet", level = NotificationLevel.INFO)

        sut.muteAllAlarms()

        assertEquals(listOf("quiet"), texts(sut))
        assertNull(platform.audibleKey)
        assertContains(platform.calls, "cancelAll")
    }

    // ---------------------------------------------------------------------------------------------
    // Text
    // ---------------------------------------------------------------------------------------------

    /**
     * Pins what the platform is given, because Android decides with it.
     *
     * `NotificationManagerImpl` posts an urgent notification that carries a sound *silently*, since
     * the ramping audio is driven separately, and gates the plain visual one on a preference and on
     * there being no actions. An implementation can only do that if the registry hands over `sound`
     * and `actions`, so this asserts it does.
     */
    @Test
    fun `the platform is given the sound and the actions`() = runTest {
        val sut = createSut()
        val action = NotificationAction(TextRef.Literal("Snooze")) {}

        sut.post(NotificationId.TOAST_ALARM, "alarm", sound = AlarmSound.ERROR, actions = listOf(action))

        val shown = platform.lastShown
        assertEquals(AlarmSound.ERROR, shown?.sound)
        assertEquals(1, shown?.actions?.size)
        assertEquals(NotificationLevel.URGENT, shown?.level)
    }

    @Test
    fun `a TextRef is resolved when it is posted`() = runTest {
        val sut = createSut()

        sut.post(NotificationId.SCENE_ENDED, TextRef.Literal("resolved text"))

        assertEquals(listOf("resolved text"), texts(sut))
    }
}
