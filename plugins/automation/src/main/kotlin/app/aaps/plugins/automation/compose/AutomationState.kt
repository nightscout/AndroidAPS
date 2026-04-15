package app.aaps.plugins.automation.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

enum class AutomationSelectionMode { None, Remove, Sort }

sealed interface AutomationRoute {
    data object List : AutomationRoute
    data class Edit(val position: Int) : AutomationRoute
    data object EditTrigger : AutomationRoute
    data class MapPicker(val initialLat: Double?, val initialLon: Double?) : AutomationRoute
}

data class AutomationIcon(
    val icon: ImageVector,
    val tint: Color? = null
)

data class AutomationActionUi(
    val index: Int,
    val title: String,
    val icon: ImageVector?,
    val valid: Boolean
)

data class AutomationEditUiState(
    val title: String = "",
    val userAction: Boolean = false,
    val enabled: Boolean = true,
    val readOnly: Boolean = false,
    val triggerDescription: String = "",
    val hasTrigger: Boolean = false,
    val preconditionsDescription: String = "",
    val actions: List<AutomationActionUi> = emptyList(),
    val titleError: Boolean = false
) {

    val canSave: Boolean
        get() = !readOnly && title.isNotBlank() && (hasTrigger || userAction) && actions.isNotEmpty()
}

data class AutomationEventUi(
    val position: Int,
    val title: String,
    val isEnabled: Boolean,
    val readOnly: Boolean,
    val userAction: Boolean,
    val systemAction: Boolean,
    val actionsValid: Boolean,
    val triggerIcons: List<AutomationIcon>,
    val actionIcons: List<AutomationIcon>,
    val isSelected: Boolean
)

data class AutomationUiState(
    val events: List<AutomationEventUi> = emptyList(),
    val logHtml: String = "",
    val selectionMode: AutomationSelectionMode = AutomationSelectionMode.None
)
