package app.aaps.pump.omnipod.omnipod5.ui.compose

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.ComposablePluginContent
import app.aaps.core.ui.compose.ToolbarConfig
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.ui.compose.OmnipodComposeHost
import app.aaps.pump.omnipod.common.ui.compose.OmnipodPodHistoryScreen
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData
import app.aaps.pump.omnipod.omnipod5.ui.O5CredentialImportViewModel
import app.aaps.pump.omnipod.omnipod5.ui.O5CredentialImportWebViewScreen
import app.aaps.pump.omnipod.omnipod5.ui.wizard.compose.O5OmnipodWizardViewModel
import app.aaps.core.ui.R as CoreUiR

class OmnipodO5ComposeContent(
    private val pluginName: String,
    private val protectionCheck: ProtectionCheck,
    private val blePreCheck: BlePreCheck,
    private val rh: ResourceHelper
) : ComposablePluginContent {

    @Composable
    override fun Render(
        setToolbarConfig: (ToolbarConfig) -> Unit,
        onNavigateBack: () -> Unit,
        onSettings: (() -> Unit)?
    ) {
        val overviewViewModel: O5OverviewViewModel = metroViewModel()
        val credentialViewModel: O5CredentialImportViewModel = metroViewModel()
        OmnipodComposeHost(
            pluginName = pluginName,
            blePreCheck = blePreCheck,
            setToolbarConfig = setToolbarConfig,
            onNavigateBack = onNavigateBack,
            onSettings = onSettings,
            overviewState = overviewViewModel.uiState,
            overviewEvents = overviewViewModel.events,
            wizardViewModel = {
                val viewModel: O5OmnipodWizardViewModel = metroViewModel()
                viewModel
            },
            onConfirmDiscardPod = overviewViewModel::confirmDiscardPod,
            activationNeedsExtraContent = { O5RegistrationData.pickControllerId == 0L },
            showExtraContentForHistory = true,
            credentialImportContent = { onImported, onBack ->
                val context = LocalContext.current
                val title = stringResource(R.string.omnipod_5_certificate_store_import)
                val importedMessage = stringResource(R.string.omnipod_5_certificate_imported)
                val importErrorMessage = stringResource(R.string.omnipod_5_certificate_import_error)
                LaunchedEffect(Unit) {
                    setToolbarConfig(
                        ToolbarConfig(
                            title = title,
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
                                }
                            },
                            actions = {}
                        )
                    )
                }
                O5CredentialImportWebViewScreen(
                    url = stringResource(R.string.omnipod_5_login),
                    onImportCredential = credentialViewModel::importFromWebMessage,
                    onImported = {
                        Toast.makeText(context, importedMessage, Toast.LENGTH_LONG).show()
                        onImported()
                    },
                    onFailed = { throwable ->
                        credentialViewModel.importError(throwable)
                        Toast.makeText(context, importErrorMessage, Toast.LENGTH_LONG).show()
                    }
                )
            },
            extraContent = { onBack ->
                val title = stringResource(R.string.omnipod_5_name)
                LaunchedEffect(title) {
                    setToolbarConfig(
                        ToolbarConfig(
                            title = title,
                            navigationIcon = {
                                IconButton(onClick = { onBack.invoke() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(CoreUiR.string.back))
                                }
                            },
                            actions = {}
                        )
                    )
                }
                val historyViewModel: O5PodHistoryViewModel = metroViewModel()
                val records by historyViewModel.records.collectAsStateWithLifecycle()
                Box(Modifier.fillMaxSize()) {
                    OmnipodPodHistoryScreen(
                        records = records,
                        rh = historyViewModel.rh,
                        profileUtil = historyViewModel.profileUtil,
                        pumpType = PumpType.OMNIPOD_5
                    )
                }
            }
        )
    }
}
