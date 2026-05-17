package app.aaps.plugins.aps.autotune.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.LocalProfileManager
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.plugins.aps.autotune.AutotuneFS
import app.aaps.plugins.aps.autotune.AutotunePlugin
import app.aaps.plugins.aps.autotune.data.ATProfile
import javax.inject.Provider

class AutotuneComposeContent(
    private val autotunePlugin: AutotunePlugin,
    private val autotuneFS: AutotuneFS,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val localProfileManager: LocalProfileManager,
    private val preferences: Preferences,
    private val dateUtil: DateUtil,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val uel: UserEntryLogger,
    private val loop: Loop,
    private val insulin: Insulin,
    private val profileStoreProvider: Provider<ProfileStore>,
    private val atProfileProvider: Provider<ATProfile>
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val scope = rememberCoroutineScope()

        val viewModel = remember {
            AutotuneViewModel(
                autotunePlugin = autotunePlugin,
                autotuneFS = autotuneFS,
                profileFunction = profileFunction,
                profileUtil = profileUtil,
                localProfileManager = localProfileManager,
                preferences = preferences,
                dateUtil = dateUtil,
                rh = rh,
                rxBus = rxBus,
                uel = uel,
                loop = loop,
                insulin = insulin,
                profileStoreProvider = profileStoreProvider,
                atProfileProvider = atProfileProvider,
                scope = scope
            )
        }

        DisposableEffect(Unit) {
            onDispose { viewModel.onDispose() }
        }

        val state = viewModel.uiState.collectAsStateWithLifecycle()

        AutotuneScreen(
            state = state.value,
            rh = rh,
            dateUtil = dateUtil,
            profileFunction = profileFunction,
            profileUtil = profileUtil,
            onProfileSelected = viewModel::onProfileSelected,
            onDaysChanged = viewModel::onDaysChanged,
            onDayToggle = viewModel::onDayToggle,
            onToggleWeekDays = viewModel::onToggleWeekDays,
            onRunAutotune = viewModel::onRunAutotune,
            onLoadLastRun = viewModel::onLoadLastRun,
            onCopyLocal = viewModel::onCopyLocalClick,
            onUpdateProfile = viewModel::onUpdateProfileClick,
            onRevertProfile = viewModel::onRevertProfileClick,
            onProfileSwitch = viewModel::onProfileSwitchClick,
            onCheckInputProfile = viewModel::onCheckInputProfile,
            onCompareProfiles = viewModel::onCompareProfiles,
            onDialogConfirm = {
                when (state.value.dialogState) {
                    is DialogState.UpdateProfile -> viewModel.onUpdateProfileConfirm()
                    is DialogState.RevertProfile -> viewModel.onRevertProfileConfirm()
                    is DialogState.ProfileSwitch -> viewModel.onProfileSwitchConfirm()
                    else                         -> viewModel.dismissDialog()
                }
            },
            onDialogDismiss = viewModel::dismissDialog,
            onCopyLocalConfirm = viewModel::onCopyLocalConfirm
        )
    }
}
