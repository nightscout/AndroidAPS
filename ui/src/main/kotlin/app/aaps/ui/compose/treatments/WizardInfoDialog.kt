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
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.BCR
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.LocalProfileUtil
import java.text.DecimalFormat
import kotlin.math.abs
import app.aaps.core.ui.R as CoreUiR

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
                value = stringResource(CoreUiR.string.format_insulin_units, bcr.glucoseInsulin)
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
                value = stringResource(CoreUiR.string.format_insulin_units, bcr.trendInsulin)
            )
        }

        // COB with IC
        if (bcr.wasCOBUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.cob) + " ${decimalFormatter.to1Decimal(bcr.cob)}g (IC: ${decimalFormatter.to1Decimal(bcr.ic)})",
                value = stringResource(CoreUiR.string.format_insulin_units, bcr.cobInsulin)
            )
        }

        // Carbs with IC
        if (bcr.wereCarbsUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.carbs) + " ${decimalFormatter.to0Decimal(bcr.carbs)}g (IC: ${decimalFormatter.to1Decimal(bcr.ic)})",
                value = stringResource(CoreUiR.string.format_insulin_units, bcr.carbsInsulin)
            )
        }

        // Percentage row — subtotal is sum of all scaled components (same as WizardDialogScreen)
        if (bcr.percentageCorrection != 100) {
            HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
            val scaledSubtotal = bcr.glucoseInsulin + bcr.trendInsulin + bcr.cobInsulin + bcr.carbsInsulin
            CalcRow(
                label = stringResource(CoreUiR.string.wizard_subtotal),
                value = stringResource(CoreUiR.string.format_insulin_units, scaledSubtotal)
            )
            val afterPercentage = scaledSubtotal * bcr.percentageCorrection / 100.0
            CalcRow(
                label = stringResource(CoreUiR.string.format_percent, bcr.percentageCorrection),
                value = stringResource(CoreUiR.string.format_insulin_units, afterPercentage)
            )
        }

        // === Unscaled components ===
        HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))

        // Bolus IOB
        if (bcr.wasBolusIOBUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.bolus_iob_label),
                value = stringResource(CoreUiR.string.format_insulin_units, -bcr.bolusIOB)
            )
        }

        // Basal IOB
        if (bcr.wasBasalIOBUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.treatments_wizard_basaliob_label),
                value = stringResource(CoreUiR.string.format_insulin_units, -bcr.basalIOB)
            )
        }

        // Direct Correction — threshold guards against floating-point noise
        if (abs(bcr.otherCorrection) > 0.005) {
            CalcRow(
                label = stringResource(CoreUiR.string.wizard_correction),
                value = stringResource(CoreUiR.string.format_insulin_units, bcr.otherCorrection)
            )
        }

        // Superbolus
        if (bcr.wasSuperbolusUsed) {
            CalcRow(
                label = stringResource(CoreUiR.string.superbolus),
                value = stringResource(CoreUiR.string.format_insulin_units, bcr.superbolusInsulin)
            )
        }

        // === Total ===
        HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.small))
        CalcRow(
            label = stringResource(CoreUiR.string.wizard_total),
            value = stringResource(CoreUiR.string.format_insulin_units, bcr.totalInsulin)
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

// region Previews

private val previewDecimalFormatter = object : DecimalFormatter {
    private val f0 = DecimalFormat("0")
    private val f1 = DecimalFormat("0.0")
    private val f2 = DecimalFormat("0.00")
    private val f3 = DecimalFormat("0.000")
    override fun to0Decimal(value: Double) = f0.format(value)
    override fun to0Decimal(value: Double, unit: String) = "${f0.format(value)} $unit"
    override fun to1Decimal(value: Double) = f1.format(value)
    override fun to1Decimal(value: Double, unit: String) = "${f1.format(value)} $unit"
    override fun to2Decimal(value: Double) = f2.format(value)
    override fun to2Decimal(value: Double, unit: String) = "${f2.format(value)} $unit"
    override fun to3Decimal(value: Double) = f3.format(value)
    override fun to3Decimal(value: Double, unit: String) = "${f3.format(value)} $unit"
    override fun toPumpSupportedBolus(value: Double, bolusStep: Double) = f2.format(value)
    override fun toPumpSupportedBolusWithUnits(value: Double, bolusStep: Double) = "${f2.format(value)} U"
    override fun toPumpSupportedBolusWithUnits(value: PumpInsulin, bolusStep: Double) = "${f2.format(value.iU(1.0))} U"
    override fun pumpSupportedBolusFormat(bolusStep: Double) = f2
}

private val previewBcr = BCR(
    timestamp = System.currentTimeMillis(),
    targetBGLow = 90.0,
    targetBGHigh = 90.0,
    isf = 40.0,
    ic = 10.0,
    bolusIOB = 1.5,
    wasBolusIOBUsed = true,
    basalIOB = 0.3,
    wasBasalIOBUsed = true,
    glucoseValue = 180.0,
    wasGlucoseUsed = true,
    glucoseDifference = 90.0,
    glucoseInsulin = 2.25,
    glucoseTrend = 5.0,
    wasTrendUsed = true,
    trendInsulin = 0.38,
    cob = 20.0,
    wasCOBUsed = true,
    cobInsulin = 2.0,
    carbs = 50.0,
    wereCarbsUsed = true,
    carbsInsulin = 5.0,
    otherCorrection = 0.5,
    wasSuperbolusUsed = false,
    superbolusInsulin = 0.0,
    wasTempTargetUsed = true,
    totalInsulin = 8.63,
    percentageCorrection = 100,
    profileName = "Default",
    note = "Before lunch"
)

@Preview(showBackground = true)
@Composable
private fun WizardInfoDialogContentPreview() {
    MaterialTheme {
        WizardInfoDialogContent(
            bcr = previewBcr,
            bgString = "180",
            isfInUnits = 40.0,
            trendString = "15",
            decimalFormatter = previewDecimalFormatter
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WizardInfoDialogContentPercentagePreview() {
    MaterialTheme {
        WizardInfoDialogContent(
            bcr = previewBcr.copy(percentageCorrection = 80),
            bgString = "180",
            isfInUnits = 40.0,
            trendString = "15",
            decimalFormatter = previewDecimalFormatter
        )
    }
}

// endregion
