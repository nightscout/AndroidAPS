package app.aaps.ui.compose.treatments

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.BCR
import app.aaps.core.interfaces.pump.PumpInsulin
import app.aaps.core.interfaces.utils.DecimalFormatter

private val previewDecimalFormatter = object : DecimalFormatter {
    private val f0 = NumberFormat.INTEGER
    private val f1 = NumberFormat.DECIMAL_1
    private val f2 = NumberFormat.DECIMAL_2
    private val f3 = NumberFormat.DECIMAL_3
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
internal fun WizardInfoDialogContentPreview() {
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
internal fun WizardInfoDialogContentPercentagePreview() {
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
