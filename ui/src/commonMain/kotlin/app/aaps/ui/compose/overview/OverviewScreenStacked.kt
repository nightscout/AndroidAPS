package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.main.VersionOverlay
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.scenes.ActiveSceneBanner

@Composable
fun OverviewScreenStacked(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean = false,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    runningMode: RM.Mode?,
    runningModeText: String,
    runningModeRemaining: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    tbrState: TbrState,
    tbrText: String = "",
    tbrProgress: Float = 0f,
    smbEnabled: Boolean,
    isSimpleMode: Boolean,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    onIobChipClick: () -> Unit,
    paddingValues: PaddingValues,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    activeSceneChainTargetName: String? = null,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    endSceneEnabled: Boolean = true,
    commandsAllowed: Boolean = true,
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    /** The "time to eat" bar, under the scene bar. */
    eatReminderBanner: @Composable () -> Unit = {},
    /** Height of the bars drawn over the content in preview mode (a short window, e.g. split screen); added to the end space. */
    overlaidBarsHeight: Dp = 0.dp,
    /** Progress of the IOB / graph calculation, 100 when done. Shown as a thin ring inside the BG ring. */
    calcProgress: Int = 100,
    /**
     * Opens the menu while the top bar is hidden: then the menu button sits in the empty top left corner
     * of the BG circle, where it is in the full bar too. Null while the full bar shows.
     */
    onCompactMenuClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val config = LocalConfig.current
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val sensitivityUiState by chipsViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val iobUiState by chipsViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by chipsViewModel.cobUiState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()

    var statusExpanded by rememberSaveable { mutableStateOf(false) }

    // BG, the chips, the client card and the status lights stay at the top; only the graphs scroll, so
    // all the state stays in view while the graphs are read. That needs height, see PINNED_LAYOUT_MIN_HEIGHT:
    // in a short window everything scrolls together. Measured outside the padding, so showing and hiding
    // the top bar does not switch it. Above them the version has its own line, so it is not drawn over BG.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val pinned = maxHeight >= PINNED_LAYOUT_MIN_HEIGHT
        val scrollState = rememberScrollState()
        val graphs: @Composable () -> Unit = {
            GraphsSection(
                graphViewModel = graphViewModel, isSimpleMode = isSimpleMode, iobText = iobUiState.text, cobText = cobUiState.text,
                overlaidBarsHeight = overlaidBarsHeight
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .then(if (pinned) Modifier else Modifier.verticalScroll(scrollState))
        ) {
            // The version above the content, in its own line, so it is not drawn over BG. While the top bar
            // is hidden the menu button is on the left of this line, its lines level with the version text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = if (onCompactMenuClick != null) VERSION_LINE_MIN_HEIGHT else 0.dp)
            ) {
                VersionOverlay(modifier = Modifier.align(Alignment.CenterEnd))
                if (onCompactMenuClick != null) {
                    CompactMenuButton(onClick = onCompactMenuClick, modifier = ZeroSizeInLine, iconLift = COMPACT_MENU_ICON_LIFT)
                }
            }
            ActiveSceneBanner(
                activeState = activeSceneState,
                expired = sceneExpired,
                chainTargetName = activeSceneChainTargetName,
                onEndClick = onEndScene,
                onDismiss = onDismissScene,
                endEnabled = endSceneEnabled,
                formatDuration = formatDuration
            )
            eatReminderBanner()
            // Right under the version line and at the top of the row: BG is the first thing on the screen. The
            // chips column is taller than the circle, so bottom alignment pushed the circle down.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = 8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BgInfoSection(
                        bgInfo = bgInfoState.bgInfo,
                        timeAgoText = bgInfoState.timeAgoText,
                        updateProgress = if (calcProgress in 1..99) calcProgress / 100f else 0f
                    )
                }

                OverviewChipsColumn(
                    runningMode = runningMode,
                    runningModeText = runningModeText,
                    runningModeRemaining = runningModeRemaining,
                    runningModeProgress = runningModeProgress,
                    runningModeSceneManaged = runningModeSceneManaged,
                    smbEnabled = smbEnabled,
                    profileName = profileName,
                    isProfileModified = isProfileModified,
                    profileProgress = profileProgress,
                    profileSceneManaged = profileSceneManaged,
                    tempTargetText = tempTargetText,
                    tempTargetState = tempTargetState,
                    tempTargetProgress = tempTargetProgress,
                    tempTargetReason = tempTargetReason,
                    tempTargetSceneManaged = tempTargetSceneManaged,
                    tbrState = tbrState,
                    tbrText = tbrText,
                    tbrProgress = tbrProgress,
                    iobUiState = iobUiState,
                    cobUiState = cobUiState,
                    sensitivityUiState = sensitivityUiState,
                    onNavigate = onNavigate,
                    onTbrChipClick = onTbrChipClick,
                    onIobChipClick = onIobChipClick,
                    commandsAllowed = commandsAllowed,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
            }

            // On a client, the client status card comes before the status lights
            if (config.AAPSCLIENT) {
                val nsClientStatus by graphViewModel.nsClientStatusFlow.collectAsStateWithLifecycle()
                val flavorTint = when {
                    config.AAPSCLIENT3 -> AapsTheme.generalColors.flavorClient3Tint
                    config.AAPSCLIENT2 -> AapsTheme.generalColors.flavorClient2Tint
                    else               -> AapsTheme.generalColors.flavorClient1Tint
                }
                AapsClientStatusCard(
                    statusData = nsClientStatus,
                    flavorTint = flavorTint
                )
            }

            OverviewStatusSection(
                sensorStatus = statusState.sensorStatus,
                insulinStatus = statusState.insulinStatus,
                cannulaStatus = statusState.cannulaStatus,
                batteryStatus = statusState.batteryStatus,
                warmUpStatus = statusState.warmUpStatus,
                secondSensorStatus = statusState.secondSensorStatus,
                canPromoteSecondSensor = statusState.canPromoteSecondSensor,
                onPromoteSecondSensor = { statusViewModel.promoteSecondSensor() },
                promotionMessage = statusState.promotionMessage,
                onDismissPromotionMessage = { statusViewModel.clearPromotionMessage() },
                showFill = statusState.showFill,
                showPumpBatteryChange = statusState.showPumpBatteryChange,
                commandsAllowed = commandsAllowed,
                onNavigate = onNavigate,
                statusLightsDef = statusLightsDef,
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = it }
            )

            if (pinned) {
                // Only the graphs scroll
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    graphs()
                }
            } else {
                graphs()
            }
        }
    }
}

/**
 * Least height of the whole overview area, also under the top and bottom bars, for BG, the chips and the
 * status lights to stay at the top while only the graphs scroll. Phones in portrait have it. Landscape
 * that is too narrow for two columns and split screen do not: there the pinned part left the graphs
 * almost no room, so the whole overview scrolls.
 */
private val PINNED_LAYOUT_MIN_HEIGHT = 600.dp

/**
 * The menu button on the left of the version line. It takes no space in the line, so the line does not
 * grow: its touch area reaches down over the empty top left corner of the round BG circle. The icon is
 * moved up so its three lines span the height of the version line.
 */
private val ZeroSizeInLine = Modifier.layout { measurable, _ ->
    val placeable = measurable.measure(Constraints())
    layout(0, 0) { placeable.place(0, 0) }
}

/** Least height of the version line while the menu button is on it, also in a build that shows no version. */
private val VERSION_LINE_MIN_HEIGHT = 12.dp

/**
 * The icon's lines start 6 dp below the top of its box. Lifted by 2 dp they sit about level with the
 * version text; lifted more, the top line went under the status bar.
 */
private val COMPACT_MENU_ICON_LIFT = 2.dp
