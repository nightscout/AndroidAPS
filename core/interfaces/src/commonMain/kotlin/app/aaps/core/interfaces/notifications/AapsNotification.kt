package app.aaps.core.interfaces.notifications

import kotlin.time.Clock

data class AapsNotification(
    val id: NotificationId,
    val instanceKey: Int,
    val text: String,
    val level: NotificationLevel,
    val date: Long = Clock.System.now().toEpochMilliseconds(),
    val validTo: Long = 0L,
    val sound: AlarmSound? = null,
    val actions: List<NotificationAction> = emptyList(),
    val validityCheck: (() -> Boolean)? = null
)
