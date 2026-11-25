package app.aaps.ui.compose.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.notifications.AapsNotification
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.pump.BolusProgressState
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.ThreeButtonDialog
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.stringResource
import app.aaps.ui.UiStrings
import app.aaps.ui.compose.aboutDialog.AboutAlertDialog
import app.aaps.ui.compose.aboutDialog.AboutDialogData
import app.aaps.ui.compose.maintenance.ImportSource
import app.aaps.ui.compose.maintenance.MaintenanceDialogs
import app.aaps.ui.compose.maintenance.MaintenanceViewModel
import app.aaps.ui.compose.manageSheet.ManageSheetState
import app.aaps.ui.compose.manageSheet.ManageViewModel
import app.aaps.ui.compose.overview.EatReminderBanner
import app.aaps.ui.compose.overview.OverviewScreen
import app.aaps.ui.compose.overview.chips.ChipsViewModel
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.statusLights.StatusViewModel
import app.aaps.ui.compose.quickLaunch.QuickLaunchAction
import app.aaps.ui.compose.quickLaunch.QuickLaunchToolbar
import app.aaps.ui.compose.quickLaunch.ResolvedQuickLaunchItem
import app.aaps.ui.compose.scenesSheet.ScenesBottomSheet
import app.aaps.ui.compose.scenesSheet.ScenesViewModel
import app.aaps.ui.compose.treatmentsSheet.TreatmentBottomSheet
import app.aaps.ui.compose.treatmentsSheet.TreatmentViewModel
import app.aaps.ui.search.SearchIndexEntry
import app.aaps.ui.search.SearchResults
import app.aaps.ui.search.SearchUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    aboutDialogData: AboutDialogData?,
    manageSheetState: ManageSheetState,
    manageViewModel: ManageViewModel,
    maintenanceViewModel: MaintenanceViewModel,
    statusViewModel: StatusViewModel,
    treatmentViewModel: TreatmentViewModel,
    scenesViewModel: ScenesViewModel,
    loopActionViewModel: app.aaps.ui.compose.loopSheet.LoopActionViewModel,
    // Search
    searchUiState: SearchUiState,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onSearchResultClick: (SearchIndexEntry) -> Unit,
    onSearchHistoryClick: (String) -> Unit,
    onSearchHistoryRemove: (String) -> Unit,
    onSearchPluginToggle: (PluginBase) -> Unit,
    onConfirmSearchPluginSwitch: () -> Unit,
    onDismissSearchPluginSwitch: () -> Unit,
    onConfirmSearchHardwarePump: () -> Unit,
    onDismissSearchHardwarePump: () -> Unit,
    // Menu/navigation
    onMenuClick: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    onDrawerClosed: () -> Unit,
    onAboutDialogDismiss: () -> Unit,
    /** Null hides the button - only Android has the problem it links to. */
    onOpenBatteryHelp: (() -> Unit)?,
    onMaintenanceSheetDismiss: () -> Unit,
    onDirectoryClick: () -> Unit,
    onLaunchBrowser: (String) -> Unit,
    onBringToForeground: () -> Unit,
    onImportSettingsNavigate: (ImportSource) -> Unit,
    onRecreateActivity: () -> Unit,
    // Notifications
    notifications: List<AapsNotification>,
    onDismissNotification: (AapsNotification) -> Unit,
    onNotificationActionClick: (AapsNotification) -> Unit,
    autoShowNotificationSheet: Boolean,
    onAutoShowConsumed: () -> Unit,
    // Pump setup
    pumpSetupPlugin: PluginBase? = null,
    // BG source shortcut
    bgSetupPlugin: PluginBase? = null,
    bgQualityBadgeIcon: ImageVector? = null,
    bgQualityBadgeTint: Color = Color.Unspecified,
    bgQualityBadgeDescription: String? = null,
    // Objectives progress
    objectivesSetupPlugin: PluginBase? = null,
    objectivesProgressText: String? = null,
    objectivesProgressDescription: String? = null,
    // Permissions
    permissionsMissing: Boolean = false,
    onPermissionsClick: () -> Unit = {},
    // Toolbar
    quickLaunchItems: List<ResolvedQuickLaunchItem> = emptyList(),
    onQuickLaunchActionClick: (QuickLaunchAction) -> Unit = {},
    calcProgress: Int,
    graphViewModel: GraphViewModel,
    chipsViewModel: ChipsViewModel,
    statusLightsDef: PreferenceSubScreenDef,
    treatmentButtonsDef: PreferenceSubScreenDef,
    // Pump activity
    bolusState: BolusProgressState? = null,
    pumpStatusText: String = "",
    queueStatusText: AnnotatedString? = null,
    isPumpCommunicating: Boolean = false,
    onStopBolus: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LocalDateUtil.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showTreatmentSheet by remember { mutableStateOf(false) }
    var showAutomationSheet by remember { mutableStateOf(false) }
    var showLoopActionSheet by remember { mutableStateOf(false) }
    val automationState by scenesViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = LocalSnackbarHostState.current

    // Sync drawer state with ui state
    LaunchedEffect(uiState.isDrawerOpen) {
        if (uiState.isDrawerOpen) {
            drawerState.open()
        } else {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isClosed) {
        if (drawerState.isClosed && uiState.isDrawerOpen) {
            onDrawerClosed()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            MainDrawer(
                drawerState = drawerState,
                versionName = mainViewModel.versionName,
                onNavigate = { request ->
                    // Close the drawer first, then open the screen. Opened while the drawer was still
                    // sliding in (a fast tap, as the e2e test makes), one Back from that screen left
                    // the app instead of coming back to the overview.
                    scope.launch {
                        drawerState.close()
                        onDrawerClosed()
                        onNavigate(request)
                    }
                },
                isTreatmentsEnabled = uiState.isProfileLoaded,
                showExit = mainViewModel.showExit
            )
        },
        // Opened only from the menu button. A swipe from the left edge opened it while the user was
        // dragging a graph to scroll it back in time. A swipe still closes it once it is open.
        gesturesEnabled = drawerState.isOpen,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val previewMode = maxHeight < PREVIEW_MODE_MIN_HEIGHT
            var chromeVisible by remember { mutableStateOf(false) }
            val showChrome = !previewMode || chromeVisible
            val accessibilityManager = LocalAccessibilityManager.current

            // Measure actual bar heights for content padding in non-preview mode
            var topBarHeightPx by remember { mutableIntStateOf(0) }
            var bottomBarHeightPx by remember { mutableIntStateOf(0) }

            // Over the one-column overview the top bar (menu, search, settings) is a switch with two
            // states, shown or hidden, never half. A push up in the scrolling part hides it; a pull down
            // when that part is already at its top shows it again. The content moves down and up by its
            // height, nothing is drawn over anything. At the start of the app it shows, then hides (a
            // peek), so everyone sees where it is. It stays while a search is open. A tap on the menu
            // button left while it is hidden shows it again too, behind the opening menu.
            //
            // In preview mode (a short window, a phone in landscape) the same gestures show and hide all
            // the bars, which then lie over the content, and the menu button stays in the top left corner.
            // The content takes every touch: a layer over it that only woke the bars ate the first touch,
            // so a swipe on the graphs did not scroll them.
            var overviewStacked by remember { mutableStateOf(false) }
            var topBarHidden by remember { mutableStateOf(false) }
            var topBarPeekDone by rememberSaveable { mutableStateOf(false) }
            val searchActive by rememberUpdatedState(searchUiState.isSearchActive)
            val inPreviewMode by rememberUpdatedState(previewMode)
            LaunchedEffect(overviewStacked) {
                if (!overviewStacked || topBarPeekDone) return@LaunchedEffect
                delay(TOP_BAR_PEEK_MS)
                if (!searchActive) topBarHidden = true
                topBarPeekDone = true
            }
            val topBarScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                        if (searchActive) return Offset.Zero
                        val userInput = source == NestedScrollSource.UserInput
                        // A push up that the content took, or one it could not take: only the graphs scroll,
                        // and a short set of graphs fits without scrolling. The bar must hide then too
                        val pushUp = consumed.y < -TOP_BAR_SCROLL_THRESHOLD_PX || (userInput && available.y < -TOP_BAR_SCROLL_THRESHOLD_PX)
                        // A drag down that the content could not take: it is at its top already
                        val pullDown = userInput && available.y > TOP_BAR_SCROLL_THRESHOLD_PX
                        if (inPreviewMode) {
                            if (pushUp) chromeVisible = false else if (pullDown) chromeVisible = true
                        } else if (overviewStacked) {
                            if (pushUp) topBarHidden = true else if (pullDown) topBarHidden = false
                        }
                        return Offset.Zero
                    }
                }
            }
            // In preview mode the top bar comes and goes with the other bars
            val topBarShown = if (previewMode) showChrome
            else showChrome && !(overviewStacked && topBarHidden && !searchUiState.isSearchActive)

            // Auto-hide chrome after timeout, reset when leaving preview mode. Not while the menu or a search
            // is open: the bars shown behind the menu are there when it closes, and the timeout starts then
            val drawerOpen = drawerState.isOpen
            LaunchedEffect(chromeVisible, previewMode, drawerOpen, searchUiState.isSearchActive) {
                if (!previewMode) {
                    chromeVisible = false
                    return@LaunchedEffect
                }
                if (chromeVisible && !drawerOpen && !searchUiState.isSearchActive) {
                    // Honours the system "Time to take action" setting: a screen reader user moving
                    // through the bars needs longer than 3 s before they hide again.
                    delay(accessibilityManager?.calculateRecommendedTimeoutMillis(AUTO_HIDE_DELAY_MS, containsIcons = true, containsControls = true) ?: AUTO_HIDE_DELAY_MS)
                    chromeVisible = false
                }
            }

            // topBar/bottomBar are intentionally absent — chrome is rendered as
            // overlays inside the content (see AnimatedVisibility blocks below) so
            // it can hide in preview mode without reflowing layout. The status-bar
            // and navigation-bar protection scrims rely on this: they read raw
            // WindowInsets.statusBars / navigationBars, which would be consumed
            // (returning zero height) if those Scaffold slots were populated.
            Scaffold { scaffoldPadding ->
                val hasToolbar = quickLaunchItems.isNotEmpty()

                // Content padding: in preview mode use only system bars;
                // in normal mode add measured bar heights
                // The top bar's height moves with it when it is switched off over the overview
                val topBarSpace by animateDpAsState(
                    targetValue = if (topBarShown) with(density) { topBarHeightPx.toDp() } else 0.dp,
                    label = "topBarSpace"
                )
                val contentPadding = if (previewMode) scaffoldPadding
                else {
                    val bottomBarHeight = with(density) { bottomBarHeightPx.toDp() }
                    PaddingValues(
                        top = scaffoldPadding.calculateTopPadding() + topBarSpace,
                        bottom = scaffoldPadding.calculateBottomPadding() + bottomBarHeight
                    )
                }

                val activeSceneState by mainViewModel.activeSceneState.collectAsStateWithLifecycle()
                val sceneExpired by mainViewModel.sceneExpired.collectAsStateWithLifecycle()
                val activeSceneChainTargetName by mainViewModel.activeSceneChainTargetName.collectAsStateWithLifecycle()
                val eatReminderState by mainViewModel.eatReminderState.collectAsStateWithLifecycle()
                val masterReachable by mainViewModel.masterReachable.collectAsStateWithLifecycle()
                // Stable pairing signal — hides the mutating nav buttons on an unpaired client.
                val masterOrPairedClient by mainViewModel.masterOrPairedClient.collectAsStateWithLifecycle()
                // (Probe-while-offline is now global — see ComposeMainActivity. This screen still reads
                // masterReachable for its own gating.)
                // In preview mode with the bars hidden, a screen reader user gets them back with an action of the
                // overview: a scroll gesture is hard to make with one, and the action keeps its name
                val showControls = stringResource(UiStrings.a11y_show_controls)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(topBarScrollConnection)
                        .semantics {
                            if (previewMode && !chromeVisible) {
                                customActions = listOf(CustomAccessibilityAction(showControls) { chromeVisible = true; true })
                            }
                        }
                ) {
                    // Main content
                    OverviewScreen(
                        profileName = uiState.profileName,
                        profilePsId = uiState.profilePsId,
                        isProfileModified = uiState.isProfileModified,
                        profileProgress = uiState.profileProgress,
                        tempTargetText = uiState.tempTargetText,
                        tempTargetState = uiState.tempTargetState,
                        tempTargetProgress = uiState.tempTargetProgress,
                        tempTargetReason = uiState.tempTargetReason,
                        tempTargetRecordId = uiState.tempTargetRecordId,
                        runningMode = uiState.runningMode,
                        runningModeText = uiState.runningModeText,
                        runningModeRemaining = uiState.runningModeRemaining,
                        runningModeProgress = uiState.runningModeProgress,
                        runningModeRecordId = uiState.runningModeRecordId,
                        tbrState = uiState.tbrState,
                        tbrText = uiState.tbrText,
                        tbrProgress = uiState.tbrProgress,
                        smbEnabled = uiState.smbEnabled,
                        isSimpleMode = uiState.isSimpleMode,
                        calcProgress = calcProgress,
                        graphViewModel = graphViewModel,
                        chipsViewModel = chipsViewModel,
                        manageViewModel = manageViewModel,
                        statusViewModel = statusViewModel,
                        statusLightsDef = statusLightsDef,
                        onNavigate = onNavigate,
                        onTbrChipClick = mainViewModel::showTbrInfo,
                        onIobChipClick = chipsViewModel::showIobInfo,
                        notifications = notifications,
                        onDismissNotification = onDismissNotification,
                        onNotificationActionClick = onNotificationActionClick,
                        autoShowNotificationSheet = autoShowNotificationSheet,
                        onAutoShowConsumed = onAutoShowConsumed,
                        activeSceneState = activeSceneState,
                        sceneExpired = sceneExpired,
                        activeSceneChainTargetName = activeSceneChainTargetName,
                        onEndScene = { mainViewModel.requestSceneDeactivation() },
                        onDismissScene = { mainViewModel.dismissExpiredScene() },
                        endSceneEnabled = masterReachable,
                        commandsAllowed = masterOrPairedClient,
                        formatDuration = mainViewModel::formatDuration,
                        eatReminderBanner = {
                            EatReminderBanner(
                                state = eatReminderState,
                                onStop = mainViewModel::stopEatReminder,
                                onExtend = mainViewModel::extendEatReminder,
                                // Like End scene: a client's buttons ask the master, which needs the connection.
                                controlsEnabled = masterReachable
                            )
                        },
                        paddingValues = contentPadding,
                        fabBottomOffset = if (hasToolbar && showChrome) 56.dp else 0.dp,
                        // In preview mode the bottom bar lies over the content instead of above it, so the end of each
                        // column needs its height too. The height is kept once measured, so the columns do not move each
                        // time the bars show and hide.
                        overlaidBarsHeight = if (previewMode) with(density) { bottomBarHeightPx.toDp() } else 0.dp,
                        bolusState = bolusState,
                        pumpStatusText = pumpStatusText,
                        queueStatusText = queueStatusText,
                        isPumpCommunicating = isPumpCommunicating,
                        onStopBolus = onStopBolus,
                        onStackedLayout = { overviewStacked = it },
                        // While the full bar is hidden the menu button stays, in the corner of the BG circle.
                        // A tap opens the menu and shows the bar again behind it (in preview mode all the bars):
                        // when the menu closes, search and settings are there, which reminds where they live.
                        // The menu still takes one tap. The bar comes after the menu is open: shown at the tap,
                        // it stopped the menu opening.
                        onCompactMenuClick = if (!topBarShown) {
                            {
                                scope.launch {
                                    drawerState.open()
                                    if (inPreviewMode) chromeVisible = true else topBarHidden = false
                                    onMenuClick()
                                }
                            }
                        } else null
                    )

                    // Search results overlay
                    if (searchUiState.isSearchActive) {
                        SearchResults(
                            results = searchUiState.results,
                            wikiResults = searchUiState.wikiResults,
                            isSearching = searchUiState.isSearching,
                            isSearchingWiki = searchUiState.isSearchingWiki,
                            wikiOffline = searchUiState.wikiOffline,
                            revision = searchUiState.revision,
                            onResultClick = onSearchResultClick,
                            onPluginToggle = onSearchPluginToggle,
                            query = searchUiState.query,
                            history = searchUiState.history,
                            onHistoryClick = onSearchHistoryClick,
                            onHistoryRemove = onSearchHistoryRemove,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(contentPadding)
                        )
                    }

                    // Plugin enable/disable confirmations raised from search results (same dialogs as Config Builder)
                    searchUiState.pluginSwitchConfirmation?.let { confirmation ->
                        OkCancelDialog(
                            title = stringResource(CoreUiStrings.configbuilder_switch_confirmation_title),
                            message = stringResource(
                                CoreUiStrings.configbuilder_switch_confirmation,
                                confirmation.fromName,
                                confirmation.toName
                            ),
                            onConfirm = onConfirmSearchPluginSwitch,
                            onDismiss = onDismissSearchPluginSwitch
                        )
                    }
                    searchUiState.hardwarePumpConfirmation?.let { confirmation ->
                        OkCancelDialog(
                            title = stringResource(CoreUiStrings.confirmation),
                            message = confirmation.message,
                            onConfirm = onConfirmSearchHardwarePump,
                            onDismiss = onDismissSearchHardwarePump
                        )
                    }

                    // Version overlay. Fixed to the screen, not to the scrolling content. With the top
                    // bar shown it is lifted into the bar's empty band under the search bar, so it sits
                    // above the profile chip instead of level with the chip's text. In preview mode the
                    // content starts right under the status bar, so there it stays at the content's edge.
                    // The one-column overview shows the version in its own strip, see OverviewScreenStacked
                    if (!overviewStacked) {
                        VersionOverlay(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(contentPadding)
                                .offset(y = if (showChrome) -VERSION_LABEL_LIFT else 0.dp)
                        )
                    }

                    // Status bar protection scrim — keeps system icons legible
                    // against the floating search bar / graph content
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(MaterialTheme.colorScheme.surface)
                    )

                    // Navigation bar protection scrim
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(MaterialTheme.colorScheme.surface)
                    )

                    // Top bar overlay
                    AnimatedVisibility(
                        visible = topBarShown,
                        enter = slideInVertically { -it },
                        exit = slideOutVertically { -it },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = scaffoldPadding.calculateTopPadding())
                            // Clipped under the status bar: it slid over the clock while it went away
                            .clipToBounds()
                    ) {
                        MainTopBar(
                            searchUiState = searchUiState,
                            onMenuClick = {
                                scope.launch {
                                    drawerState.open()
                                    onMenuClick()
                                }
                            },
                            onPreferencesClick = { onNavigate(NavigationRequest.Element(ElementType.SETTINGS)) },
                            onSearchQueryChange = onSearchQueryChange,
                            onSearchClear = onSearchClear,
                            onSearchActiveChange = onSearchActiveChange,
                            isSimpleMode = uiState.isSimpleMode,
                            // Guard against transient 0 heights during AnimatedVisibility exit:
                            // the resulting contentPadding invalidation can schedule a remeasure
                            // on a node that's losing its owner — crashes in dispatchDraw.
                            modifier = Modifier.onSizeChanged {
                                if (it.height > 0 && it.height != topBarHeightPx) topBarHeightPx = it.height
                            }
                        )
                    }

                    // Bottom bar overlay
                    AnimatedVisibility(
                        visible = showChrome,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = scaffoldPadding.calculateBottomPadding())
                    ) {
                        val loopActionState = loopActionViewModel.uiState.collectAsStateWithLifecycle().value
                        MainNavigationBar(
                            onManageClick = { manageSheetState.show() },
                            onTreatmentClick = {
                                treatmentViewModel.refreshState()
                                showTreatmentSheet = true
                            },
                            masterOrPairedClient = masterOrPairedClient,
                            onAutomationClick = {
                                scenesViewModel.refreshState()
                                showAutomationSheet = true
                            },
                            // Total drives nav-button visibility (button stays visible whenever
                            // scenes/automation exist, even if currently un-activatable).
                            automationTotal = automationState.items.size + automationState.sceneItems.size,
                            pumpSetupPlugin = pumpSetupPlugin,
                            bgSetupPlugin = bgSetupPlugin,
                            bgQualityBadgeIcon = bgQualityBadgeIcon,
                            bgQualityBadgeTint = bgQualityBadgeTint,
                            bgQualityBadgeDescription = bgQualityBadgeDescription,
                            objectivesSetupPlugin = objectivesSetupPlugin,
                            objectivesProgressText = objectivesProgressText,
                            objectivesProgressDescription = objectivesProgressDescription,
                            onNavigate = onNavigate,
                            permissionsMissing = permissionsMissing,
                            onPermissionsClick = onPermissionsClick,
                            loopActionAvailable = loopActionState.actionAvailable,
                            onLoopActionClick = { showLoopActionSheet = true },
                            modifier = Modifier.onSizeChanged {
                                if (it.height > 0 && it.height != bottomBarHeightPx) bottomBarHeightPx = it.height
                            }
                        )
                    }

                    // Quick launch toolbar overlay
                    AnimatedVisibility(
                        visible = hasToolbar && showChrome,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(
                                bottom = scaffoldPadding.calculateBottomPadding() +
                                    with(density) { bottomBarHeightPx.toDp() } + 8.dp
                            )
                    ) {
                        QuickLaunchToolbar(
                            items = quickLaunchItems,
                            onActionClick = onQuickLaunchActionClick,
                        )
                    }
                }
            }
        }
    }

    // Treatment bottom sheet
    if (showTreatmentSheet) {
        val treatmentState by treatmentViewModel.uiState.collectAsStateWithLifecycle()
        TreatmentBottomSheet(
            onDismiss = { showTreatmentSheet = false },
            showCgm = treatmentState.showCgm,
            showCalibration = treatmentState.showCalibration,
            showTreatment = treatmentState.showTreatment,
            showInsulin = treatmentState.showInsulin,
            showCarbs = treatmentState.showCarbs,
            showCalculator = treatmentState.showCalculator,
            isDexcomSource = treatmentState.isDexcomSource,
            showSettingsIcon = treatmentState.showSettingsIcon,
            quickWizardItems = treatmentState.quickWizardItems,
            onNavigate = onNavigate,
            treatmentButtonsDef = treatmentButtonsDef,
        )
    }

    // Automation bottom sheet
    if (showAutomationSheet) {
        ScenesBottomSheet(
            onDismiss = { showAutomationSheet = false },
            automationItems = automationState.items,
            onItemClick = { item -> mainViewModel.requestAutomationConfirmation(item.eventId) },
            sceneItems = automationState.sceneItems,
            onSceneClick = { sceneId -> mainViewModel.requestSceneConfirmation(sceneId) },
            onEditScenesClick = { onNavigate(NavigationRequest.Element(ElementType.SCENE_MANAGEMENT)) }
        )
    }

    // Loop accept action bottom sheet
    if (showLoopActionSheet) {
        val loopState by loopActionViewModel.uiState.collectAsStateWithLifecycle()
        app.aaps.ui.compose.loopSheet.LoopActionBottomSheet(
            state = loopState,
            onPerform = { mainViewModel.performLoopAccept() },
            onDismiss = { showLoopActionSheet = false }
        )
    }

    // Shared confirmation dialog (automation actions, TT presets, scene end — from toolbar or
    // bottom sheets). When the confirmation carries a secondary action (e.g., scene chain skip),
    // render a 3-button dialog; otherwise the standard 2-button OK/Cancel.
    val actionConfirmation by mainViewModel.actionConfirmation.collectAsStateWithLifecycle()
    actionConfirmation?.let { confirmation ->
        val secondaryAction = confirmation.secondaryAction
        val secondaryLabel = confirmation.secondaryLabel
        if (secondaryAction != null && secondaryLabel != null) {
            ThreeButtonDialog(
                title = confirmation.title,
                message = confirmation.message,
                icon = confirmation.icon,
                primaryLabel = confirmation.confirmLabel ?: stringResource(CoreUiStrings.ok),
                onPrimary = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                secondaryLabel = secondaryLabel,
                onSecondary = { mainViewModel.executeConfirmableAction(secondaryAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        } else {
            OkCancelDialog(
                title = confirmation.title,
                message = confirmation.message,
                icon = confirmation.icon,
                onConfirm = { mainViewModel.executeConfirmableAction(confirmation.onConfirmAction) },
                onDismiss = { mainViewModel.dismissActionConfirmation() }
            )
        }
    }

    // Maintenance dialogs (sheets, confirmations, export chain)
    MaintenanceDialogs(
        maintenanceViewModel = maintenanceViewModel,
        showMaintenanceSheet = uiState.showMaintenanceSheet,
        onMaintenanceSheetDismiss = onMaintenanceSheetDismiss,
        onDirectoryClick = onDirectoryClick,
        onImportSettingsNavigate = onImportSettingsNavigate,
        onRecreateActivity = onRecreateActivity,
        onLaunchBrowser = onLaunchBrowser,
        onBringToForeground = onBringToForeground,
        onSnackbar = { snackbarHostState.showSnackbar(it) }
    )

    // About dialog
    if (uiState.showAboutDialog && aboutDialogData != null) {
        AboutAlertDialog(
            data = aboutDialogData,
            onDismiss = onAboutDialogDismiss,
            onOpenBatteryHelp = onOpenBatteryHelp
        )
    }
}

private val PREVIEW_MODE_MIN_HEIGHT: Dp = 500.dp
private const val AUTO_HIDE_DELAY_MS = 3000L

/** How long the top bar shows at the start of the app before it hides, see topBarHidden. */
private const val TOP_BAR_PEEK_MS = 1500L

/** A scroll step smaller than this does not switch the top bar, so a jitter does not flicker it. */
private const val TOP_BAR_SCROLL_THRESHOLD_PX = 4f

/**
 * How far the version label is lifted above the content's top edge while the top bar is shown. The
 * bar has 8 dp of nothing under its search bar and the first chip starts a few dp into the content;
 * this centers the 12 dp label between the two.
 */
private val VERSION_LABEL_LIFT: Dp = 6.dp
