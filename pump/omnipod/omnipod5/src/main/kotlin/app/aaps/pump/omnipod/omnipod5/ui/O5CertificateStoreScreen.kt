package app.aaps.pump.omnipod.omnipod5.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.metroViewModel
import app.aaps.pump.omnipod.common.R

@Composable
fun O5CertificateStoreScreen(
    rh: ResourceHelper,
    onBack: () -> Unit,
    allowManualImport: Boolean
) {
    val viewModel: O5CredentialImportViewModel = metroViewModel()
    var showManualImport by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (showManualImport) R.string.omnipod_5_certificate_store_import
                            else R.string.omnipod_5_certificate_store
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { if (showManualImport) showManualImport = false else onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                        )
                    }
                },
                actions = {
                    if (!showManualImport && allowManualImport) {
                        TextButton(onClick = { showManualImport = true }) {
                            Text(
                                text = stringResource(app.aaps.core.ui.R.string.import_btn)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showManualImport && allowManualImport) {
                O5CredentialManualImportScreen(viewModel = viewModel, rh = rh)
            } else {
                O5CredentialListScreen(viewModel = viewModel, rh = rh)
            }
        }
    }
}
