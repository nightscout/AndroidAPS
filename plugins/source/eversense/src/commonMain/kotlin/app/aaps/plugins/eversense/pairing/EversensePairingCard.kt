package app.aaps.plugins.eversense.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.aaps.core.interfaces.source.CgmAttention
import app.aaps.core.ui.compose.AapsCard
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.eversense.EversenseStrings

/**
 * The pairing on the status tab, where the bottom bar's "!" leads: what is wrong with the button that fixes it, or the
 * paired transmitter with the login and Unpair.
 */
@Composable
fun EversensePairingCard(
    attention: CgmAttention?,
    account: EversenseAccount?,
    transmitter: String?,
    onPair: () -> Unit,
    onSignInAgain: () -> Unit,
    onUnpair: () -> Unit
) {
    var confirmUnpair by remember { mutableStateOf(false) }
    AapsCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(AapsSpacing.extraLarge), verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
            when (attention) {
                CgmAttention.NOT_PAIRED     -> {
                    Text(stringResource(EversenseStrings.eversense_not_paired), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onPair, modifier = Modifier.fillMaxWidth()) { Text(stringResource(EversenseStrings.eversense_pair_title)) }
                }

                CgmAttention.SIGN_IN_FAILED -> {
                    Text(stringResource(EversenseStrings.eversense_sign_in_failed), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onSignInAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(EversenseStrings.eversense_sign_in_again)) }
                }

                null                        -> {
                    transmitter?.let { Text(stringResource(EversenseStrings.eversense_paired_transmitter, it), style = MaterialTheme.typography.bodyLarge) }
                    account?.let { Text(stringResource(EversenseStrings.eversense_signed_in_as, it.username), style = MaterialTheme.typography.bodyMedium) }
                    OutlinedButton(onClick = { confirmUnpair = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(EversenseStrings.eversense_unpair))
                    }
                }
            }
        }
    }
    if (confirmUnpair) {
        OkCancelDialog(
            title = stringResource(EversenseStrings.eversense_unpair),
            message = stringResource(EversenseStrings.eversense_unpair_confirm, transmitter.orEmpty()),
            onConfirm = {
                confirmUnpair = false
                onUnpair()
            },
            onDismiss = { confirmUnpair = false }
        )
    }
}
