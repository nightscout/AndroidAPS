package app.aaps.ui.compose.notificationsSheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings

@Composable
fun NotificationFab(
    notificationCount: Int,
    highestLevel: NotificationLevel?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = notificationCount > 0,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = modifier
    ) {
        val containerColor = highestLevel?.toColor() ?: AapsTheme.generalColors.notificationInfo
        val contentColor = AapsTheme.generalColors.onNotification
        // A screen reader heard only "3, button": the bell had no name and the urgency was only the
        // colour. Now "Notifications: 3, button", plus "critical" / "warning" when one needs attention.
        val spoken = stringResource(UiStrings.a11y_notifications, notificationCount)
        val urgency = highestLevel?.toDescription()?.let { stringResource(it) }

        FloatingActionButton(
            onClick = onClick,
            containerColor = containerColor,
            contentColor = contentColor,
            modifier = if (urgency != null) Modifier.semantics { stateDescription = urgency } else Modifier,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp,
                focusedElevation = 8.dp,
                hoveredElevation = 8.dp
            )
        ) {
            BadgedBox(
                badge = {
                    Badge {
                        // The count is part of the spoken name below.
                        Text(text = notificationCount.toString(), modifier = Modifier.clearAndSetSemantics { })
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = spoken
                )
            }
        }
    }
}
