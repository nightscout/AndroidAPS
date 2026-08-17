package app.aaps.ui.compose.treatments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import app.aaps.core.data.model.BCR
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalProfileUtil
import kotlin.math.abs
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.interfaces.R as InterfacesR

/**
 * Compose dialog showing the calculation breakdown of a Bolus Calculator Result.
 * Follows the same scaled/unscaled component structure as WizardDialogScreen.
 */
@Composable
internal fun WizardInfoDialog(
    bcr: BCR,
    decimalFormatter: DecimalFormatter,
    onDismiss: () -> Unit
) {
    val profileUtil = LocalProfileUtil.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(CoreUiR.string.boluswizard),
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            WizardInfoDialogContent(
                bcr = bcr,
                bgString = profileUtil.fromMgdlToStringInUnits(bcr.glucoseValue),
                isfInUnits = profileUtil.fromMgdlToUnits(bcr.isf),
                trendString = profileUtil.fromMgdlToStringInUnits(bcr.glucoseTrend * 3),
                decimalFormatter = decimalFormatter
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

/**
 * @see WizardInfoDialogContentPreview
 * @see WizardInfoDialogContentPercentagePreview
 */
@Composable
internal fun WizardInfoDialogContent(
    bcr: BCR,
    bgString: String,
    isfInUnits: Double,
    trendString: String,
    decimalFormatter: DecimalFormatter
) {
    Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)) {
        // === Scaled components (affected by percentage) ===

        // BG with ISF
        if (bcr.wasGlucoseUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.wizard_bg_label) + " $bgString (ISF: ${decimalFormatter.to1Decimal(isfInUnits)})",
                value = stringResource(InterfacesR.string.format_insulin_units, bcr.glucoseInsulin)
            )
            if (bcr.wasTempTargetUsed) {
                CalcRow(
                    label = stringResource(CoreUiR.string.tt_label),
                    value = ""
                )
            }
        }

        // Trend
        if (bcr.wasTrendUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.wizard_bg_label) + " \u039415m: $trendString",
                value = stringResource(InterfacesR.string.format_insulin_units, bcr.trendInsulin)
            )
        }

        // COB with IC
        if (bcr.wasCOBUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.cob) + " ${decimalFormatter.to1Decimal(bcr.cob)}g (IC: ${decimalFormatter.to1Decimal(bcr.ic)})",
                value = stringResource(InterfacesR.string.format_insulin_units, bcr.cobInsulin)
            )
        }

        // Carbs with IC
        if (bcr.wereCarbsUsed) {
            CalcRow(
                label = stringResource(InterfacesR.string.carbs) + " ${decimalFormatter.to0Decimal(bcr.carbs)}g (IC: ${decimalFormatter.to1Decimal(bcr.ic)})",
                value = stringResource(InterfacesR.string.format_insulin_units, bcr.carbsInsulin)
            )
        }

        // Percentage row — subtotal is sum of all scaled components (same as WizardDialogScreen)
        if (bcr.percentageCorrection != 100) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
            val scaledSubtotal = bcr.glucoseInsulin + bcr.trendInsulin + bcr.cobInsulin + bcr.carbsInsulin
            CalcRow(
                label = stringResource(CoreUiR.string.wizard_subtotal),
                value = stringResource(InterfacesR.string.format_insulin_units, scaledSubtotal)
            )
            val afterPercentage = scaledSubtotal * bcr.percentageCorrection / 100.0
            CalcRow(
                label = stringResource(CoreUiR.string.format_percent, bcr.percentageCorrection),
                value = stringResource(InterfacesR.string.format_insulin_units, afterPercentage)
            )
        }

        // === Unscaled components ===
        HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))

        // Bolus IOB
        if (bcr.wasBolusIOBUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.bolus_iob_label),
                value = stringResource(InterfacesR.string.format_insulin_units, -bcr.bolusIOB)
            )
        }

        // Basal IOB
        if (bcr.wasBasalIOBUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.treatments_wizard_basaliob_label),
                value = stringResource(InterfacesR.string.format_insulin_units, -bcr.basalIOB)
            )
        }

        // Direct Correction — threshold guards against floating-point noise
        if (abs(bcr.otherCorrection) > 0.005) {
            CalcRow(
                label = stringResource(CoreUiR.string.wizard_correction),
                value = stringResource(InterfacesR.string.format_insulin_units, bcr.otherCorrection)
            )
        }

        // Superbolus
        if (bcr.wasSuperbolusUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.superbolus),
                value = stringResource(InterfacesR.string.format_insulin_units, bcr.superbolusInsulin)
            )
        }

        // === Total ===
        HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
        CalcRow(
            label = stringResource(CoreUiR.string.wizard_total),
            value = stringResource(InterfacesR.string.format_insulin_units, bcr.totalInsulin)
        )

        // === Footer ===
        HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
        Text(
            text = stringResource(CoreUiR.string.profile) + ": " + bcr.profileName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (bcr.note.isNotEmpty()) {
            Text(
                text = stringResource(CoreUiR.string.notes_label) + ": " + bcr.note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CalcRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
