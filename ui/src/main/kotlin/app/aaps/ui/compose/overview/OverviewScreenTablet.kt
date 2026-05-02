package app.aaps.ui.compose.overview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.GraphsSection
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.scenes.ActiveSceneBanner

/**
 * Tablet variant of the overview screen.
 *
 * Differs from [OverviewScreenSplit] (phone landscape) by hoisting [LargeClock]
 * to a full-width top row above the BG/chips row, so it can grow much larger
 * without competing with chip width.
 */
@Composable
fun OverviewScreenTablet(
    profileName: String,
    isProfileModified: Boolean,
    profileProgress: Float,
    profileSceneManaged: Boolean = false,
    tempTargetText: String,
    tempTargetState: TempTargetChipState,
    tempTargetProgress: Float,
    tempTargetReason: TT.Reason?,
    tempTargetSceneManaged: Boolean = false,
    runningMode: RM.Mode,
    runningModeText: String,
    runningModeProgress: Float,
    runningModeSceneManaged: Boolean = false,
    tbrState: TbrState,
    isSimpleMode: Boolean,
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    manageViewModel: ManageViewModel,
    statusViewModel: StatusViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    onNavigate: (NavigationRequest) -> Unit,
    onTbrChipClick: () -> Unit,
    paddingValues: PaddingValues,
    activeSceneState: ActiveSceneState? = null,
    sceneExpired: Boolean = false,
    onEndScene: () -> Unit = {},
    onDismissScene: () -> Unit = {},
    formatDuration: (Long) -> String = { ms -> "${(ms / 60000L).toInt()}m" },
    modifier: Modifier = Modifier
) {
    val config = LocalConfig.current
    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val sensitivityUiState by graphViewModel.sensitivityUiState.collectAsStateWithLifecycle()
    val iobUiState by graphViewModel.iobUiState.collectAsStateWithLifecycle()
    val cobUiState by graphViewModel.cobUiState.collectAsStateWithLifecycle()
    val statusState by statusViewModel.uiState.collectAsStateWithLifecycle()

    var statusExpanded by rememberSaveable { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (calcProgress < 100) {
            LinearProgressIndicator(
                progress = { calcProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            )
        }
        ActiveSceneBanner(
            activeState = activeSceneState,
            expired = sceneExpired,
            onEndClick = onEndScene,
            onDismiss = onDismissScene,
            formatDuration = formatDuration
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AapsSpacing.small)
        ) {
            // Left column — clock (top row), BG + chips row, status, NS card. Own scroll.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(end = AapsSpacing.small),
                verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AapsSpacing.small),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BgInfoSection(
                            bgInfo = bgInfoState.bgInfo,
                            timeAgoText = bgInfoState.timeAgoText,
                            showTimeAgo = false
                        )
                        SensitivityChipBlock(state = sensitivityUiState)
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = AapsSpacing.medium)
                    ) {
                        LargeClock(
                            bgTimestamp = bgInfoState.bgInfo?.timestamp,
                            maxFontSize = 73.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OverviewChipsColumn(
                            runningMode = runningMode,
                            runningModeText = runningModeText,
                            runningModeProgress = runningModeProgress,
                            runningModeSceneManaged = runningModeSceneManaged,
                            isSimpleMode = isSimpleMode,
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
                            iobUiState = iobUiState,
                            cobUiState = cobUiState,
                            onNavigate = onNavigate,
                            onTbrChipClick = onTbrChipClick
                        )
                    }
                }

                OverviewStatusSection(
                    sensorStatus = statusState.sensorStatus,
                    insulinStatus = statusState.insulinStatus,
                    cannulaStatus = statusState.cannulaStatus,
                    batteryStatus = statusState.batteryStatus,
                    showFill = statusState.showFill,
                    showPumpBatteryChange = statusState.showPumpBatteryChange,
                    onNavigate = onNavigate,
                    statusLightsDef = statusLightsDef,
                    onCopyFromNightscout = { manageViewModel.copyStatusLightsFromNightscout() },
                    expanded = statusExpanded,
                    onExpandedChange = { statusExpanded = it }
                )

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
            }

            // Right column — graphs, own scroll
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(start = AapsSpacing.small)
            ) {
                GraphsSection(graphViewModel = graphViewModel, isSimpleMode = isSimpleMode)
            }
        }
    }
}
