package app.aaps.core.interfaces.notifications

import androidx.annotation.StringRes

data class NotificationAction(
    @StringRes val buttonTextRes: Int,
    val action: () -> Unit
)
