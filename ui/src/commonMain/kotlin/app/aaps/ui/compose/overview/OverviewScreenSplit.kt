package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.model.ActiveSceneState
import app.aaps.core.data.model.RM
import app.aaps.core.data.model.TT
import app.aaps.core.interfaces.overview.graph.TbrState
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.ui.compose.main.TempTargetChipState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.aapsClient.AapsClientStatusCard
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.scenes.ActiveSceneBanner

@Composable
fun OverviewScreenSplit(
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
    /** Height of the bars drawn over the content in preview mode; both columns need it as extra space at their end. */
    overlaidBarsHeight: Dp = 0.dp,
    /** Progress of the IOB / graph calculation, 100 when done. Shown as a thin ring inside the BG ring. */
    calcProgress: Int = 100,
    /**
     * Opens the menu while the bars are hidden (preview mode, e.g. a phone in landscape): then the menu
     * button sits in the top left corner of the left column, over the BG circle. Null while the bars show.
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

    var statusExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
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

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp)
        ) {
            // Left column — BG + chips + status + NS card, own scroll. The menu button lies over its top left
            // corner while the bars are hidden, fixed, not scrolling with the column
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(end = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // No large clock here, unlike the tablet layout: this layout is lower than 600 dp (a phone in
                    // landscape, a small window), and the clock row took about a fifth of that height for a time
                    // the status bar already shows, and pushed BG down. At the top of the row, as in portrait:
                    // BG is the first thing on the screen.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            BgInfoSection(
                                bgInfo = bgInfoState.bgInfo,
                                timeAgoText = bgInfoState.timeAgoText,
                                updateProgress = if (calcProgress in 1..99) calcProgress / 100f else 0f
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                        ) {
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
                                commandsAllowed = commandsAllowed
                            )
                        }
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
                    // The QuickLaunch toolbar is centred over both columns, so this one needs space at its end too,
                    // like the graphs column has
                    Spacer(Modifier.height(AapsSpacing.fabClearance + overlaidBarsHeight))
                }
                if (onCompactMenuClick != null) {
                    CompactMenuButton(onClick = onCompactMenuClick, modifier = Modifier.align(Alignment.TopStart))
                }
            }

            // Right column — graphs, own scroll
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 4.dp)
            ) {
                GraphsSection(
                    graphViewModel = graphViewModel, isSimpleMode = isSimpleMode, iobText = iobUiState.text, cobText = cobUiState.text,
                    overlaidBarsHeight = overlaidBarsHeight
                )
            }
        }
    }
}
