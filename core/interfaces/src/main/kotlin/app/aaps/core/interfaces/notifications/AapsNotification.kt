package app.aaps.core.interfaces.notifications

import androidx.annotation.RawRes

data class AapsNotification(
    val id: NotificationId,
    val instanceKey: Int,
    val text: String,
    val level: NotificationLevel,
    val date: Long = System.currentTimeMillis(),
    val validTo: Long = 0L,
    @RawRes val soundRes: Int? = null,
    val actions: List<NotificationAction> = emptyList(),
    val validityCheck: (() -> Boolean)? = null
)
