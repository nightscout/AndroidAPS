package app.aaps.ui.compose.notificationsSheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.notifications.NotificationCategory
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcPluginAutomation
import app.aaps.core.ui.compose.icons.IcPluginMaintenance
import app.aaps.core.ui.compose.icons.IcPluginNsClient
import app.aaps.core.ui.compose.icons.IcPluginVirtualPump
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.compose.stringResourceOrNull
import app.aaps.ui.UiStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationBottomSheet(
    notifications: List<AapsNotification>,
    onDismissSheet: () -> Unit,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit
) {
    LocalDateUtil.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissSheet,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn {
            item {
                Text(
                    text = stringResource(CoreUiStrings.notification),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            items(
                items = notifications,
                key = { "${it.id.name}_${it.instanceKey}" }
            ) { notification ->
                Box(modifier = Modifier.animateItem()) {
                    NotificationItem(
                        notification = notification,
                        onDismiss = { onDismissNotification(notification) },
                        onActionClick = { onNotificationActionClick(notification) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationItem(
    notification: AapsNotification,
    onDismiss: () -> Unit,
    onActionClick: () -> Unit
) {
    val dateUtil = LocalDateUtil.current
    val levelColor = notification.level.toColor()
    val categoryIcon = notification.id.category.toIcon()

    // How urgent a notification is was carried only by the tint of the leading icon, whose
    // description was null - so an alarm-tier message and a routine one were announced identically,
    // on a sheet that opens by itself whenever something important arrives. Colour does not even
    // separate the top two: URGENT and IMPORTANT share notificationUrgent.
    val levelState = stringResourceOrNull(notification.level.toDescription())

    Column {
        ListItem(
            leadingContent = {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = levelColor
                )
            },
            headlineContent = {
                Text(text = notification.text)
            },
            supportingContent = {
                Text(text = dateUtil.timeString(notification.date))
            },
            modifier = if (levelState != null) {
                Modifier.semantics { stateDescription = levelState }
            } else {
                Modifier
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (notification.actions.isNotEmpty()) {
                notification.actions.forEach { action ->
                    ActionButton(text = action.buttonText) {
                        action.action()
                        onActionClick()
                        onDismiss()
                    }
                }
            } else {
                ActionButton(text = CoreUiStrings.dismiss, onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun RowScope.ActionButton(text: TextRef, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.weight(1f)
    ) {
        Text(text = stringResource(text))
    }
}

/**
 * How urgent a notification is, in words, for a screen reader. Kept beside [toColor] because the
 * colour was the only place this lived.
 *
 * Only the two tiers that ask for attention get a word. Saying "normal" on every routine entry would
 * make the ordinary case the noisiest thing in the list, which is the opposite of the point - the
 * same reason [app.aaps.core.ui.compose.statusLevelToDescription] stays quiet for NORMAL.
 */
fun NotificationLevel.toDescription(): TextRef? = when (this) {
    NotificationLevel.URGENT       -> CoreUiStrings.critical
    NotificationLevel.IMPORTANT    -> CoreUiStrings.warning
    NotificationLevel.NORMAL,
    NotificationLevel.LOW,
    NotificationLevel.INFO,
    NotificationLevel.ANNOUNCEMENT -> null
}

@Composable
fun NotificationLevel.toColor(): Color = when (this) {
    NotificationLevel.URGENT       -> AapsTheme.generalColors.notificationUrgent
    NotificationLevel.IMPORTANT    -> AapsTheme.generalColors.notificationUrgent
    NotificationLevel.NORMAL       -> AapsTheme.generalColors.notificationNormal
    NotificationLevel.LOW          -> AapsTheme.generalColors.notificationLow
    NotificationLevel.INFO         -> AapsTheme.generalColors.notificationInfo
    NotificationLevel.ANNOUNCEMENT -> AapsTheme.generalColors.notificationAnnouncement
}

fun NotificationCategory.toIcon(): ImageVector = when (this) {
    NotificationCategory.PUMP       -> IcPluginVirtualPump
    NotificationCategory.PROFILE    -> IcProfile
    NotificationCategory.CGM        -> IcCgmInsert
    NotificationCategory.LOOP       -> IcLoopClosed
    NotificationCategory.SYNC       -> IcPluginNsClient
    NotificationCategory.SYSTEM     -> IcPluginMaintenance
    NotificationCategory.AUTOMATION -> IcPluginAutomation
    NotificationCategory.GENERAL    -> Icons.Default.Notifications
}
