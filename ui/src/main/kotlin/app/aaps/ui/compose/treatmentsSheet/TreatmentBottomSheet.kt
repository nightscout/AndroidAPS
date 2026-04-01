package app.aaps.ui.compose.treatmentsSheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCarbs
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.TonalIcon
import app.aaps.core.ui.compose.consumeOverscroll
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.core.ui.compose.navigation.color
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.descriptionResId
import app.aaps.core.ui.compose.navigation.icon
import app.aaps.core.ui.compose.navigation.labelResId
import app.aaps.core.ui.compose.preference.AdaptivePreferenceList
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.ui.compose.main.QuickWizardItem
import app.aaps.core.ui.R as CoreUiR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentBottomSheet(
    onDismiss: () -> Unit,
    // Visibility flags
    showCgm: Boolean,
    showCalibration: Boolean,
    showTreatment: Boolean,
    showInsulin: Boolean,
    showCarbs: Boolean,
    showCalculator: Boolean,
    isDexcomSource: Boolean,
    showSettingsIcon: Boolean,
    // QuickWizard
    quickWizardItems: List<QuickWizardItem>,
    // Callbacks
    onNavigate: (NavigationRequest) -> Unit,
    // For settings screen
    treatmentButtonsDef: PreferenceSubScreenDef? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showSettings by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        if (showSettings && treatmentButtonsDef != null) {
            TreatmentSettingsContent(
                settingsDef = treatmentButtonsDef,
                onBack = { showSettings = false }
            )
        } else {
            TreatmentSelectionContent(
                onDismiss = onDismiss,
                onNavigate = onNavigate,
                quickWizardItems = quickWizardItems,
                showCgm = showCgm,
                showCalibration = showCalibration,
                showTreatment = showTreatment,
                showInsulin = showInsulin,
                showCarbs = showCarbs,
                showCalculator = showCalculator,
                isDexcomSource = isDexcomSource,
                showSettingsIcon = showSettingsIcon,
                onSettingsClick = { showSettings = true }
            )
        }
    }
}

@Composable
private fun TreatmentSelectionContent(
    onDismiss: () -> Unit,
    onNavigate: (NavigationRequest) -> Unit,
    quickWizardItems: List<QuickWizardItem>,
    showCgm: Boolean,
    showCalibration: Boolean,
    showTreatment: Boolean,
    showInsulin: Boolean,
    showCarbs: Boolean,
    showCalculator: Boolean,
    isDexcomSource: Boolean,
    showSettingsIcon: Boolean,
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(CoreUiR.string.treatments),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (showSettingsIcon) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(CoreUiR.string.settings),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        val disabledAlpha = 0.38f

        // QuickWizard entries
        quickWizardItems.forEach { item ->
            val itemEnabled = item.isEnabled
            val itemIcon = when (item.mode) {
                1    -> IcBolus    // INSULIN
                2    -> IcCarbs    // CARBS
                else -> ElementType.QUICK_WIZARD.icon() // WIZARD
            }
            val itemColor = when (item.mode) {
                1    -> ElementType.INSULIN.color()
                2    -> ElementType.CARBS.color()
                else -> ElementType.QUICK_WIZARD.color()
            }
            val supportingText = if (itemEnabled) item.detail
            else item.disabledReason?.let { reason ->
                if (item.detail != null) "${item.detail} — $reason" else reason
            } ?: item.detail
            ListItem(
                headlineContent = {
                    Text(
                        text = item.buttonText,
                        color = if (itemEnabled) itemColor
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
                    )
                },
                supportingContent = supportingText?.let {
                    {
                        Text(
                            text = it,
                            color = if (itemEnabled) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                        )
                    }
                },
                leadingContent = {
                    TonalIcon(
                        painter = rememberVectorPainter(itemIcon),
                        color = if (itemEnabled) itemColor
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                        enabled = itemEnabled
                    )
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = if (itemEnabled) Modifier.clickable {
                    onDismiss()
                    onNavigate(NavigationRequest.QuickWizard(item.guid))
                } else Modifier
            )
        }

        // Divider between QuickWizard and other buttons
        if (quickWizardItems.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
        }

        // CGM
        if (showCgm) {
            val cgmType = if (isDexcomSource) ElementType.CGM_DEX else ElementType.CGM_XDRIP
            TreatmentItem(
                elementType = cgmType,
                enabled = true,
                disabledAlpha = disabledAlpha,
                onDismiss = onDismiss,
                onClick = { onNavigate(NavigationRequest.Element(cgmType)) }
            )
        }

        // Calibration
        if (showCalibration) {
            TreatmentItem(
                elementType = ElementType.CALIBRATION,
                enabled = true,
                disabledAlpha = disabledAlpha,
                onDismiss = onDismiss,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.CALIBRATION)) }
            )
        }

        // Treatment
        if (showTreatment) {
            TreatmentItem(
                elementType = ElementType.TREATMENT,
                enabled = true,
                disabledAlpha = disabledAlpha,
                onDismiss = onDismiss,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.TREATMENT)) }
            )
        }

        // Insulin
        if (showInsulin) {
            TreatmentItem(
                elementType = ElementType.INSULIN,
                enabled = true,
                disabledAlpha = disabledAlpha,
                onDismiss = onDismiss,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.INSULIN)) }
            )
        }

        // Carbs
        if (showCarbs) {
            TreatmentItem(
                elementType = ElementType.CARBS,
                enabled = true,
                disabledAlpha = disabledAlpha,
                onDismiss = onDismiss,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.CARBS)) }
            )
        }

        // Calculator
        if (showCalculator) {
            TreatmentItem(
                elementType = ElementType.BOLUS_WIZARD,
                enabled = true,
                disabledAlpha = disabledAlpha,
                onDismiss = onDismiss,
                onClick = { onNavigate(NavigationRequest.Element(ElementType.BOLUS_WIZARD)) }
            )
        }
    }
}

@Composable
private fun TreatmentSettingsContent(
    settingsDef: PreferenceSubScreenDef,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .consumeOverscroll()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 24.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(CoreUiR.string.back)
                )
            }
            Text(
                text = stringResource(CoreUiR.string.settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ProvidePreferenceTheme {
            AdaptivePreferenceList(
                items = settingsDef.items
            )
        }
    }
}

@Composable
private fun TreatmentItem(
    elementType: ElementType,
    enabled: Boolean,
    disabledAlpha: Float,
    onDismiss: () -> Unit,
    onClick: () -> Unit
) {
    val color = elementType.color()
    val descResId = elementType.descriptionResId()
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(elementType.labelResId()),
                color = if (enabled) color
                else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha)
            )
        },
        supportingContent = if (descResId != 0) {
            {
                Text(
                    text = stringResource(descResId),
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = disabledAlpha)
                )
            }
        } else null,
        leadingContent = {
            TonalIcon(
                painter = rememberVectorPainter(elementType.icon()),
                color = if (enabled) color
                else MaterialTheme.colorScheme.onSurface.copy(alpha = disabledAlpha),
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = if (enabled) Modifier.clickable {
            onDismiss()
            onClick()
        } else Modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun TreatmentBottomSheetPreview() {
    MaterialTheme {
        TreatmentSelectionContent(
            onDismiss = {},
            onNavigate = {},
            quickWizardItems = listOf(
                QuickWizardItem(guid = "1", buttonText = "Meal", detail = "36g / 2.5U", isEnabled = true),
                QuickWizardItem(guid = "2", buttonText = "Snack", detail = "12g / 0.8U", disabledReason = "No insulin required")
            ),
            showCgm = true,
            showCalibration = true,
            showTreatment = true,
            showInsulin = true,
            showCarbs = true,
            showCalculator = true,
            isDexcomSource = false,
            showSettingsIcon = true,
            onSettingsClick = {}
        )
    }
}
