package app.aaps.pump.insight.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardScreen
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.insight.R

enum class InsightPairStep { SEARCH, CONNECTING, CODE_COMPARE, COMPLETED }

data class InsightPairDevice(val address: String, val name: String)

data class InsightPairUiState(
    val step: InsightPairStep = InsightPairStep.SEARCH,
    val devices: List<InsightPairDevice> = emptyList(),
    val verificationCode: String = ""
)

@Composable
fun InsightPairWizardScreen(
    state: InsightPairUiState,
    onDeviceSelected: (InsightPairDevice) -> Unit,
    onConfirmCode: () -> Unit,
    onRejectCode: () -> Unit,
    onExit: () -> Unit,
    onCancel: () -> Unit
) {
    val stepIndex = when (state.step) {
        InsightPairStep.SEARCH       -> 0
        InsightPairStep.CONNECTING   -> 1
        InsightPairStep.CODE_COMPARE -> 2
        InsightPairStep.COMPLETED    -> 3
    }

    WizardScreen(
        currentStep = state.step,
        totalSteps = 4,
        currentStepIndex = stepIndex,
        canGoBack = state.step == InsightPairStep.SEARCH,
        onBack = onCancel,
        cancelDialogTitle = stringResource(id = R.string.insight_pairing),
        cancelDialogText = stringResource(id = app.aaps.core.ui.R.string.cancel)
    ) { step, _ ->
        when (step) {
            InsightPairStep.SEARCH       -> SearchStep(
                devices = state.devices,
                onDeviceSelected = onDeviceSelected,
                onCancel = onCancel
            )

            InsightPairStep.CONNECTING   -> ConnectingStep(onCancel = onCancel)

            InsightPairStep.CODE_COMPARE -> CodeCompareStep(
                code = state.verificationCode,
                onYes = onConfirmCode,
                onNo = onRejectCode
            )

            InsightPairStep.COMPLETED    -> CompletedStep(onExit = onExit)
        }
    }
}

@Composable
private fun SearchStep(
    devices: List<InsightPairDevice>,
    onDeviceSelected: (InsightPairDevice) -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        scrollable = false,
        secondaryButton = WizardButton(
            text = stringResource(id = app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(id = R.string.searching_for_devices),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (devices.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(devices, key = { it.address }) { device ->
                    DeviceItem(device = device, onClick = { onDeviceSelected(device) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun DeviceItem(device: InsightPairDevice, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = device.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ConnectingStep(onCancel: () -> Unit) {
    WizardStepLayout(
        secondaryButton = WizardButton(
            text = stringResource(id = app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        CircularProgressIndicator(
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = app.aaps.core.ui.R.string.please_wait),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CodeCompareStep(
    code: String,
    onYes: () -> Unit,
    onNo: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(id = app.aaps.core.ui.R.string.yes),
            onClick = onYes
        ),
        secondaryButton = WizardButton(
            text = stringResource(id = app.aaps.core.ui.R.string.no),
            onClick = onNo
        )
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(id = R.string.code_compare),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = code,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompletedStep(onExit: () -> Unit) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(id = app.aaps.core.ui.R.string.exit),
            onClick = onExit
        )
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(id = R.string.pairing_completed),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
