package app.aaps.pump.omnipod.omnipod5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.pump.omnipod.common.R

/**
 * Manual certificate import screen: lets the user paste a certificate string and install it.
 * Reached from the "import" action in the top app bar of
 * [O5CredentialListScreen]/[O5CertificateStoreScreen]. Purely certificate management - no
 * dosing/pairing actions.
 */
@Composable
fun O5CredentialManualImportScreen(
    viewModel: O5CredentialImportViewModel,
    rh: ResourceHelper
) {
    val inputText by viewModel.inputText.collectAsState()
    val importResult by viewModel.importResult.collectAsState()

    O5CredentialManualImportContent(
        inputText = inputText,
        inputChanged = viewModel::onInputChanged,
        importInput = viewModel::importCurrentInput,
        importResult = importResult,
        rh = rh
    )
}

@Composable
private fun O5CredentialManualImportContent(
    inputText: String,
    inputChanged: (String) -> Unit,
    importInput: () -> Unit,
    importResult: ImportResult,
    rh: ResourceHelper,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = rh.gs(R.string.omnipod_5_certificate_manual_import_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = rh.gs(R.string.omnipod_5_certificate_manual_import_description),
            style = MaterialTheme.typography.bodySmall
        )

        OutlinedTextField(
            value = inputText,
            onValueChange = inputChanged,
            label = { Text(rh.gs(R.string.omnipod_5_certificate_manual_import_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 180.dp),
            singleLine = false
        )

        Button(
            onClick = importInput,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(rh.gs(app.aaps.core.ui.R.string.import_btn))
        }

        when (importResult) {
            is ImportResult.Success -> Text(
                text = rh.gs(R.string.omnipod_5_certificate_store_imported, formatControllerId(importResult.controllerId)),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )

            is ImportResult.Failure -> Text(
                text = importResult.reason,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge
            )

            ImportResult.RemoveBlocked -> Unit

            ImportResult.None       -> Unit
        }
    }
}
