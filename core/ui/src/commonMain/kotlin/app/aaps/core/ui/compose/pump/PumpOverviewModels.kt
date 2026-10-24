package app.aaps.core.ui.compose.pump

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import app.aaps.core.ui.compose.ScreenAction
import app.aaps.core.ui.compose.StatusLevel

/**
 * UI state for the shared pump overview screen.
 * Each pump ViewModel produces this state by mapping pump-specific data.
 */
@Immutable
data class PumpOverviewUiState(
    val statusBanner: StatusBanner? = null,
    val infoRows: List<PumpInfoInterface> = emptyList(),
    val primaryActions: List<ScreenAction> = emptyList(),
    val managementActions: List<ScreenAction> = emptyList(),
    val queueStatus: AnnotatedString? = null
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
) : PumpInfoInterface

/**
 * Group for PumpInfoRow. Group items are displayed together, with divider only at end of group (instead of each item)
 */
data class PumpInfoGroup(
    var list: MutableList<PumpInfoRow> = mutableListOf()
) : PumpInfoInterface

/**
 * PumpInfoRow with custom compose content.
 */
interface PumpInfoComposable : PumpInfoInterface {

    fun composableContent(): @Composable () -> Unit
    fun hasDividerOnEnd(): Boolean = false
}
