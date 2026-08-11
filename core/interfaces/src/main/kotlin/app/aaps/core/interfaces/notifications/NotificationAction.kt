package app.aaps.core.interfaces.notifications

import app.aaps.core.keys.interfaces.TextRef

data class NotificationAction(
    val buttonText: TextRef,
    val action: () -> Unit
)
