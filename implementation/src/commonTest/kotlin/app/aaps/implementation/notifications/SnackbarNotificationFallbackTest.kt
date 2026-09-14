package app.aaps.implementation.notifications

import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventShowSnackbar
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The half of the snackbar design that used to live in `MainApp`, and so existed on Android only.
 *
 * In `commonTest` rather than `androidHostTest` on purpose: the point of moving this class was that
 * iOS and desktop run it too, and a test that only runs on the JVM would say nothing about that.
 *
 * What matters here is the hand-off. A message must be surfaced exactly once: as a snackbar while a
 * host is up, as a notification when none is. Both mistakes are real - a lost message and a
 * duplicated one - so both directions are tested.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SnackbarNotificationFallbackTest {

    private val rxBus = FakeRxBus()
    private val notificationManager = RecordingNotificationManager()
    private val presence = SnackbarHostPresenceImpl()

    private fun startSut() =
        SnackbarNotificationFallback(rxBus, notificationManager, presence, TestScope(UnconfinedTestDispatcher()))
            .also { it.start() }

    @Test
    fun `with no host up the message becomes a notification`() {
        startSut()

        rxBus.send(EventShowSnackbar("Export failed", EventShowSnackbar.Type.Error))

        assertEquals(listOf("Export failed"), notificationManager.postedTexts)
    }

    @Test
    fun `while a host is up nothing is posted`() {
        startSut()
        presence.acquire()

        rxBus.send(EventShowSnackbar("Saved", EventShowSnackbar.Type.Success))

        assertEquals(emptyList(), notificationManager.postedTexts)
    }

    @Test
    fun `once the last host goes away posting resumes`() {
        startSut()
        val host = presence.acquire()
        rxBus.send(EventShowSnackbar("seen on screen", EventShowSnackbar.Type.Info))

        host.close()
        rxBus.send(EventShowSnackbar("nobody there", EventShowSnackbar.Type.Info))

        assertEquals(listOf("nobody there"), notificationManager.postedTexts)
    }

    /** Two hosts up at once - a dialog activity over the main one - still means somebody can see it. */
    @Test
    fun `one host closing while another stays up posts nothing`() {
        startSut()
        val first = presence.acquire()
        presence.acquire()

        first.close()
        rxBus.send(EventShowSnackbar("still covered", EventShowSnackbar.Type.Warning))

        assertEquals(emptyList(), notificationManager.postedTexts)
    }

    /** The level mapping is the one thing the fallback decides on its own, so it is pinned here. */
    @Test
    fun `an error becomes a normal notification, not an urgent one`() {
        startSut()

        rxBus.send(EventShowSnackbar("Export failed", EventShowSnackbar.Type.Error))

        assertEquals(listOf(NotificationLevel.NORMAL), notificationManager.postedLevels)
    }

    @Test
    fun `success and info become the quietest level`() {
        startSut()

        rxBus.send(EventShowSnackbar("Saved", EventShowSnackbar.Type.Success))
        rxBus.send(EventShowSnackbar("Note", EventShowSnackbar.Type.Info))

        assertEquals(listOf(NotificationLevel.INFO, NotificationLevel.INFO), notificationManager.postedLevels)
    }

    private class FakeRxBus : RxBus {

        private val events = MutableSharedFlow<Event>(extraBufferCapacity = 16)

        override fun send(event: Event) {
            check(events.tryEmit(event)) { "event buffer overflow" }
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Event> toFlow(eventType: KClass<T>): Flow<T> =
            events.filter { eventType.isInstance(it) } as Flow<T>
    }

    /** Records what was posted. The two overloads this class never calls fail loudly instead. */
    private class RecordingNotificationManager : NotificationManager {

        val postedTexts = mutableListOf<String>()
        val postedLevels = mutableListOf<NotificationLevel>()

        override val notifications: StateFlow<List<AapsNotification>> = MutableStateFlow(emptyList())

        override fun cleanUp() {}

        override fun post(
            id: NotificationId,
            text: String,
            level: NotificationLevel,
            validMinutes: Int,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle {
            postedTexts.add(text)
            postedLevels.add(level)
            return NotificationHandle(postedTexts.size)
        }

        override fun post(
            id: NotificationId,
            text: String,
            level: NotificationLevel,
            date: Long,
            validTo: Long,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle = error("the fallback posts with validMinutes, not with a date range")

        override fun post(
            id: NotificationId,
            textRef: TextRef,
            level: NotificationLevel,
            validMinutes: Int,
            date: Long,
            validTo: Long,
            sound: AlarmSound?,
            actions: List<NotificationAction>,
            validityCheck: (() -> Boolean)?
        ): NotificationHandle = error("the message is already localized, so the fallback posts a String")

        override fun dismiss(id: NotificationId) {}

        override fun dismiss(handle: NotificationHandle) {}

        override fun muteAllAlarms() {}
    }
}
