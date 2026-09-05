package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.AlarmSound
import app.aaps.core.interfaces.notifications.NotificationAction
import app.aaps.core.interfaces.notifications.NotificationHandle
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.keys.interfaces.TextRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Remembers what was posted, so a test can ask whether the user would have been told.
 *
 * Written by hand rather than mocked because these tests are in `commonTest` and Mockito is JVM
 * only - and a recording fake is the better tool here anyway, since what matters is *which* id was
 * posted and how many times, not that a call happened.
 */
class RecordingNotificationManager : NotificationManager {

    val posted = mutableListOf<Pair<NotificationId, TextRef?>>()

    override val notifications: StateFlow<List<AapsNotification>> = MutableStateFlow(emptyList())

    override fun cleanUp() = Unit

    override fun post(
        id: NotificationId,
        text: String,
        level: NotificationLevel,
        validMinutes: Int,
        sound: AlarmSound?,
        actions: List<NotificationAction>,
        validityCheck: (() -> Boolean)?
    ): NotificationHandle {
        posted += id to null
        return NotificationHandle(posted.size)
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
    ): NotificationHandle {
        posted += id to null
        return NotificationHandle(posted.size)
    }

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
    ): NotificationHandle {
        posted += id to textRef
        return NotificationHandle(posted.size)
    }

    override fun dismiss(id: NotificationId) = Unit
    override fun dismiss(handle: NotificationHandle) = Unit
    override fun muteAllAlarms() = Unit
}
