package app.aaps.pump.omnipod.omnipod5.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.stringResource
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.omnipod5.bledriver.comm.pair.O5RegistrationData

/**
 * Settings screen for viewing/removing Omnipod 5 certificates.
 */
@Composable
fun O5CredentialListScreen(
    viewModel: O5CredentialImportViewModel,
    rh: ResourceHelper
) {
    val importResult by viewModel.importResult.collectAsState()
    val installedCredentials by viewModel.installedCredentials.collectAsState()
    val canRemoveCertificate by viewModel.canRemoveCertificate.collectAsState()

    O5CredentialImportContent(
        removeCredential = viewModel::removeCredential,
        canRemoveCertificate = canRemoveCertificate,
        importResult = importResult,
        installedCredentials = installedCredentials,
    )
}

@Composable
private fun O5CredentialImportContent(
    removeCredential: (Long) -> Unit,
    canRemoveCertificate: Boolean,
    importResult: ImportResult,
    installedCredentials: List<InstalledCredentialRow>,
) {
    val removeBlockedMessage = stringResource(TextRef.AndroidRes(R.string.omnipod_5_certificate_store_remove_blocked))
    val showRemoveBlockedMessage = !canRemoveCertificate || importResult == ImportResult.RemoveBlocked

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (importResult) {
            is ImportResult.Success -> Text(
                text = stringResource(TextRef.AndroidRes(R.string.omnipod_5_certificate_store_imported), formatControllerId(importResult.controllerId)),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )

            is ImportResult.Failure -> Text(
                text = importResult.reason,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.labelLarge
            )

            ImportResult.None, ImportResult.RemoveBlocked -> Unit
        }

        if (installedCredentials.isNotEmpty()) {
            if (showRemoveBlockedMessage) {
                Text(
                    text = removeBlockedMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = stringResource(TextRef.AndroidRes(R.string.omnipod_5_certificate_store_installed_certificates)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                installedCredentials.forEach { row ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = stringResource(TextRef.AndroidRes(R.string.omnipod_5_certificate_store_controller_id), formatControllerId(row.controllerId)),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = row.source.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            TextButton(
                                onClick = { removeCredential(row.controllerId) },
                                enabled = canRemoveCertificate
                            ) {
                                Text(stringResource(TextRef.AndroidRes(app.aaps.core.ui.R.string.remove)))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportContent() {
    MaterialTheme {
        O5CredentialImportContent(
            removeCredential = {},
            canRemoveCertificate = true,
            importResult = ImportResult.None,
            installedCredentials = emptyList(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportedCredentialsContent() {
    MaterialTheme {
        O5CredentialImportContent(
            removeCredential = {},
            canRemoveCertificate = true,
            importResult = ImportResult.None,
            installedCredentials = listOf(InstalledCredentialRow(123, O5RegistrationData.O5RegistrationSource.IMPORTED)),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportFailureContent() {
    MaterialTheme {
        O5CredentialImportContent(
            removeCredential = {},
            canRemoveCertificate = true,
            importResult = ImportResult.Failure("Failed to import cert"),
            installedCredentials = emptyList(),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewCredentialImportSuccesssContent() {
    MaterialTheme {
        O5CredentialImportContent(
            removeCredential = {},
            canRemoveCertificate = true,
            importResult = ImportResult.Success(123),
            installedCredentials = listOf(InstalledCredentialRow(123, O5RegistrationData.O5RegistrationSource.IMPORTED)),
        )
    }
}
