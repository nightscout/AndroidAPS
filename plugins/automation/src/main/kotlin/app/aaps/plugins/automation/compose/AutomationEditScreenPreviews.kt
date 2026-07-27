package app.aaps.plugins.automation.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.icons.IcAutomation

private fun sampleEditState(
    readOnly: Boolean = false,
    hasTrigger: Boolean = true,
    actions: Int = 2,
    userAction: Boolean = false
) = AutomationEditUiState(
    title = if (readOnly) "System wakeup" else "Morning TT",
    userAction = userAction,
    enabled = true,
    readOnly = readOnly,
    triggerDescription = if (hasTrigger) "BG < 4 mmol/L AND Delta < -0.1 mmol/L" else "",
    hasTrigger = hasTrigger,
    preconditionsDescription = if (hasTrigger) "Loop running AND profile active" else "",
    actions = (0 until actions).map { i ->
        AutomationActionUi(
            index = i,
            title = if (i == 0) "Start temp target 8 mmol/L for 60 min" else "Send notification",
            icon = IcAutomation,
            valid = i != actions - 1 || actions == 1
        )
    },
    titleError = false
)

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
internal fun PreviewAutomationEditScreenNew() {
    MaterialTheme {
        AutomationEditScreen(
            state = AutomationEditUiState(),
            onTitleChange = {},
            onUserActionChange = {},
            onEnabledChange = {},
            onEditTrigger = {},
            onAddAction = {},
            onRemoveAction = {},
            onActionChanged = {},
            liveActions = emptyList(),
            profileNames = emptyList(),
            sceneOptions = emptyList(),
            tick = 0
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
internal fun PreviewAutomationEditScreenEdit() {
    MaterialTheme {
        AutomationEditScreen(
            state = sampleEditState(),
            onTitleChange = {},
            onUserActionChange = {},
            onEnabledChange = {},
            onEditTrigger = {},
            onAddAction = {},
            onRemoveAction = {},
            onActionChanged = {},
            liveActions = emptyList(),
            profileNames = emptyList(),
            sceneOptions = emptyList(),
            tick = 0
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
internal fun PreviewAutomationEditScreenReadOnly() {
    MaterialTheme {
        AutomationEditScreen(
            state = sampleEditState(readOnly = true, actions = 1),
            onTitleChange = {},
            onUserActionChange = {},
            onEnabledChange = {},
            onEditTrigger = {},
            onAddAction = {},
            onRemoveAction = {},
            onActionChanged = {},
            liveActions = emptyList(),
            profileNames = emptyList(),
            sceneOptions = emptyList(),
            tick = 0
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
internal fun PreviewAutomationEditScreenUserAction() {
    MaterialTheme {
        AutomationEditScreen(
            state = sampleEditState(hasTrigger = false, userAction = true, actions = 1),
            onTitleChange = {},
            onUserActionChange = {},
            onEnabledChange = {},
            onEditTrigger = {},
            onAddAction = {},
            onRemoveAction = {},
            onActionChanged = {},
            liveActions = emptyList(),
            profileNames = emptyList(),
            sceneOptions = emptyList(),
            tick = 0
        )
    }
}
