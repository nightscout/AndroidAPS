package app.aaps.ui.compose.treatments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.ui.UiStrings
import app.aaps.core.ui.compose.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcExtendedBolus
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTtHigh
import app.aaps.ui.R
import app.aaps.ui.compose.treatments.viewmodels.TreatmentsViewModel
import kotlinx.coroutines.launch

/**
 * Composable screen displaying treatments with tab navigation.
 * Uses Jetpack Compose for all content including each treatment type.
 *
 * @param viewModel ViewModel containing all dependencies and child ViewModels
 * @param onNavigateBack Callback when back navigation is requested
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreatmentsScreen(
    viewModel: TreatmentsViewModel,
    onNavigateBack: () -> Unit
) {
    val showExtendedBolusTab = viewModel.showExtendedBolusTab()
    val iconColors = AapsTheme.elementColors
    val defaultToolbarConfig = remember {
        ToolbarConfig(
            title = "",
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
            },
            actions = { }
        )
    }

    // Define tabs with their icons and content
    val tabs = remember(showExtendedBolusTab) {
        var currentIndex = 0
        buildList {
            add(
                TreatmentTab(
                    pageIndex = currentIndex++,
                    icon = IcCarbs,
                    titleRes = UiStrings.carbs_and_bolus,
                    colorGetter = { iconColors.carbs },
                )
            )
            if (showExtendedBolusTab) {
                add(
                    TreatmentTab(
                        pageIndex = currentIndex++,
                        icon = IcExtendedBolus,
                        titleRes = CoreUiStrings.extended_bolus,
                        colorGetter = { iconColors.extendedBolus },
                    )
                )
            }
            add(
                TreatmentTab(
                    pageIndex = currentIndex++,
                    icon = IcTbrHigh,
                    titleRes = CoreUiStrings.tempbasal_label,
                    colorGetter = { iconColors.tempBasal },
                )
            )
            add(
                TreatmentTab(
                    pageIndex = currentIndex++,
                    icon = IcTtHigh,
                    titleRes = CoreUiStrings.temporary_target,
                    colorGetter = { iconColors.tempTarget },
                )
            )
            add(
                TreatmentTab(
                    pageIndex = currentIndex++,
                    icon = IcProfile,
                    titleRes = CoreUiStrings.careportal_profileswitch,
                    colorGetter = { iconColors.profileSwitch },
                )
            )
            add(
                TreatmentTab(
                    pageIndex = currentIndex++,
                    icon = IcNote,
                    titleRes = CoreUiStrings.careportal,
                    colorGetter = { iconColors.careportal },
                )
            )
            add(
                TreatmentTab(
                    pageIndex = currentIndex++,
                    icon = Icons.AutoMirrored.Filled.DirectionsRun,
                    titleRes = CoreUiStrings.running_mode,
                    colorGetter = { iconColors.runningMode },
                )
            )
            add(
                TreatmentTab(
                    pageIndex = currentIndex++,
                    icon = Icons.AutoMirrored.Filled.Note,
                    titleRes = UiStrings.user_entry,
                    colorGetter = { iconColors.userEntry },
                )
            )
        }
    }

    // Per-tab toolbar config cache — each child writes to its own slot
    val toolbarConfigs = remember(tabs.size) {
        List<ToolbarConfig?>(tabs.size) { null }.toMutableStateList()
    }

    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // Derive active toolbar from current page's cached config
    val activeToolbar by remember {
        derivedStateOf { toolbarConfigs.getOrNull(pagerState.currentPage) ?: defaultToolbarConfig }
    }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(activeToolbar.title.ifEmpty { stringResource(CoreUiStrings.treatments_history) }) },
                navigationIcon = { activeToolbar.navigationIcon() },
                actions = { activeToolbar.actions(this) }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab row
            PrimaryScrollableTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = stringResource(tab.titleRes),
                                tint = tab.colorGetter(),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        text = {
                            Text(stringResource(tab.titleRes))
                        }
                    )
                }
            }

            // Pager with treatment screens
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0
            ) { page ->
                val tab = tabs[page]
                val setConfig: (ToolbarConfig) -> Unit = remember(tab.pageIndex) {
                    { config -> toolbarConfigs[tab.pageIndex] = config }
                }
                when (tab.titleRes) {
                    UiStrings.carbs_and_bolus                           ->
                        BolusCarbsScreen(
                            viewModel = viewModel.bolusCarbsViewModel,
                            setToolbarConfig = setConfig,
                            onNavigateBack = onNavigateBack
                        )

                    CoreUiStrings.extended_bolus           ->
                        ExtendedBolusScreen(
                            viewModel = viewModel.extendedBolusViewModel,
                            profileFunction = viewModel.profileFunction,
                            setToolbarConfig = setConfig,
                            onNavigateBack = onNavigateBack
                        )

                    CoreUiStrings.tempbasal_label          ->
                        TempBasalScreen(
                            viewModel = viewModel.tempBasalViewModel,
                            profileFunction = viewModel.profileFunction,
                            activePlugin = viewModel.activePlugin,
                            setToolbarConfig = setConfig,
                            onNavigateBack = onNavigateBack
                        )

                    CoreUiStrings.temporary_target         ->
                        TempTargetScreen(
                            viewModel = viewModel.tempTargetViewModel,
                            translator = viewModel.translator,
                            decimalFormatter = viewModel.decimalFormatter,
                            setToolbarConfig = setConfig,
                            onNavigateBack = onNavigateBack
                        )

                    CoreUiStrings.careportal_profileswitch ->
                        ProfileSwitchScreen(
                            viewModel = viewModel.profileSwitchViewModel,
                            decimalFormatter = viewModel.decimalFormatter,
                            uel = viewModel.uel,
                            setToolbarConfig = setConfig,
                            onNavigateBack = onNavigateBack
                        )

                    CoreUiStrings.careportal               ->
                        CareportalScreen(
                            viewModel = viewModel.careportalViewModel,
                            persistenceLayer = viewModel.persistenceLayer,
                            translator = viewModel.translator,
                            setToolbarConfig = setConfig,
                            onNavigateBack = onNavigateBack
                        )

                    CoreUiStrings.running_mode             ->
                        RunningModeScreen(
                            viewModel = viewModel.runningModeViewModel,
                            translator = viewModel.translator,
                            setToolbarConfig = setConfig,
                            onNavigateBack = onNavigateBack
                        )

                    UiStrings.user_entry                                ->
                        UserEntryScreen(
                            viewModel = viewModel.userEntryViewModel,
                            userEntryPresentationHelper = viewModel.userEntryPresentationHelper,
                            translator = viewModel.translator,
                            importExportPrefs = viewModel.importExportPrefs,
                            uel = viewModel.uel,
                            setToolbarConfig = setConfig,
                            onNavigateBack = onNavigateBack
                        )

                    // The tabs are built right above from this same set of titles, so nothing else can
                    // reach here. A TextRef is an interface rather than an Int, so the compiler wants
                    // the branch spelled out.
                    else                                   -> Unit
                }
            }
        }
    }
}

/**
 * Represents a treatment tab's metadata (icon, title, color).
 * Content is rendered in the pager via a `when` dispatch on [titleRes].
 *
 * Not a data class — [colorGetter] lambda makes structural equality meaningless.
 */
private class TreatmentTab(
    val pageIndex: Int,
    val icon: ImageVector,
    val titleRes: TextRef,
    val colorGetter: () -> Color,
)
