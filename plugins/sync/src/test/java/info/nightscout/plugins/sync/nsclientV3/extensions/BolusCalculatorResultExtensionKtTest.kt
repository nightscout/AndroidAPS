package info.nightscout.plugins.sync.nsclientV3.extensions

import app.aaps.core.nssdk.localmodel.treatment.NSBolusWizard
import app.aaps.core.nssdk.mapper.convertToRemoteAndBack
import app.aaps.database.entities.BolusCalculatorResult
import app.aaps.database.entities.embedments.InterfaceIDs
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

@Suppress("SpellCheckingInspection")
internal class BolusCalculatorResultExtensionKtTest {

    @Test
    fun toBolusCalculatorResult() {
        val bolus = BolusCalculatorResult(
            timestamp = 10000,
            isValid = true,
            targetBGLow = 110.0,
            targetBGHigh = 120.0,
            isf = 30.0,
            ic = 2.0,
            bolusIOB = 1.1,
            wasBolusIOBUsed = true,
            basalIOB = 1.2,
            wasBasalIOBUsed = true,
            glucoseValue = 150.0,
            wasGlucoseUsed = true,
            glucoseDifference = 30.0,
            glucoseInsulin = 1.3,
            glucoseTrend = 15.0,
            wasTrendUsed = true,
            trendInsulin = 1.4,
            cob = 24.0,
            wasCOBUsed = true,
            cobInsulin = 1.5,
            carbs = 36.0,
            wereCarbsUsed = true,
            carbsInsulin = 1.6,
            otherCorrection = 1.7,
            wasSuperbolusUsed = true,
            superbolusInsulin = 0.3,
            wasTempTargetUsed = false,
            totalInsulin = 9.1,
            percentageCorrection = 70,
            profileName = " sss",
            note = "ddd",
            interfaceIDs_backing = InterfaceIDs(
                nightscoutId = "nightscoutId",
                pumpId = 11000,
                pumpType = InterfaceIDs.PumpType.DANA_I,
                pumpSerial = "bbbb"
            )
        )

        val bolus2 = (bolus.toNSBolusWizard().convertToRemoteAndBack() as NSBolusWizard).toBolusCalculatorResult()!!
        assertThat(bolus.contentEqualsTo(bolus2)).isTrue()
        assertThat(bolus.interfaceIdsEqualsTo(bolus2)).isTrue()
    }
}
