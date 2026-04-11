package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.ui.compose.StatusLevel

/**
 * UI state for the shared pump overview screen.
 * Each pump ViewModel produces this state by mapping pump-specific data.
 */
@Immutable
data class PumpOverviewUiState(
    val statusBanner: StatusBanner? = null,
    val infoRows: List<PumpInfoInterface> = emptyList(),
    val primaryActions: List<PumpAction> = emptyList(),
    val managementActions: List<PumpAction> = emptyList(),
    val queueStatus: String? = null
)

/**
 * Connection/status banner shown at the top of the pump overview.
 */
@Immutable
data class StatusBanner(
    val text: String,
    val level: StatusLevel = StatusLevel.UNSPECIFIED
)

/**
 * Interface for PumpInfo objects (Row, Group)
 */
interface PumpInfoInterface

/**
 * A single label:value row in the pump info section.
 */
@Immutable
data class PumpInfoRow(
    val label: String,
    val value: String,
    val level: StatusLevel = StatusLevel.UNSPECIFIED,
    val visible: Boolean = true
): PumpInfoInterface

/**
 * Group for PumpInfoRow. Group items are displayed together, with divider only at end of group (instead of each item)
 */
data class PumpInfoGroup(
    var list: MutableList<PumpInfoRow> = mutableListOf()
): PumpInfoInterface


/**
 * Action category for separating primary operational actions from device management actions.
 */
enum class ActionCategory {

    PRIMARY,
    MANAGEMENT
}

/**
 * An action button in the pump overview (refresh, history, pair, etc.).
 */
@Immutable
data class PumpAction(
    val label: String,
    val iconRes: Int = 0,
    val icon: ImageVector? = null,
    val category: ActionCategory = ActionCategory.PRIMARY,
    val enabled: Boolean = true,
    val visible: Boolean = true,
    val onClick: () -> Unit
)
