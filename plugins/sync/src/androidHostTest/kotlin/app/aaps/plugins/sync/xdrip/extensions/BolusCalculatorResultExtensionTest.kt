package app.aaps.plugins.sync.xdrip.extensions

import app.aaps.core.data.model.BCR
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.IDs
import app.aaps.core.data.model.TE
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/** Covers the xdrip [BCR] toJson: event/units/glucose/notes mapping, embedded Gson blob, and add-only nightscout id. */
class BolusCalculatorResultExtensionTest : TestBase() {

    private val dateUtil: DateUtil = mock()
    private val profileUtil: ProfileUtil = mock()

    @BeforeEach
    fun setup() {
        whenever(dateUtil.toISOString(any())).thenReturn("2023-01-01T00:00:00Z")
        whenever(profileUtil.fromMgdlToUnits(any(), any())).thenReturn(6.5)
        whenever(profileUtil.units).thenReturn(GlucoseUnit.MMOL)
    }

    private fun bcr(ids: IDs = IDs()) = BCR(
        timestamp = 1000L, targetBGLow = 90.0, targetBGHigh = 120.0, isf = 50.0, ic = 10.0,
        bolusIOB = 0.5, wasBolusIOBUsed = true, basalIOB = 0.2, wasBasalIOBUsed = true,
        glucoseValue = 150.0, wasGlucoseUsed = true, glucoseDifference = 30.0, glucoseInsulin = 0.6,
        glucoseTrend = 1.0, wasTrendUsed = false, trendInsulin = 0.0, cob = 20.0, wasCOBUsed = true,
        cobInsulin = 2.0, carbs = 20.0, wereCarbsUsed = true, carbsInsulin = 2.0, otherCorrection = 0.0,
        wasSuperbolusUsed = false, superbolusInsulin = 0.0, wasTempTargetUsed = false, totalInsulin = 5.0,
        percentageCorrection = 100, profileName = "Profile", note = "wizard", ids = ids
    )

    @Test
    fun mapsFields() {
        val json = bcr().toJson(isAdd = true, dateUtil = dateUtil, profileUtil = profileUtil)
        assertThat(json.getString("eventType")).isEqualTo(TE.Type.BOLUS_WIZARD.text)
        assertThat(json.getDouble("glucose")).isEqualTo(6.5)
        assertThat(json.getString("units")).isEqualTo(GlucoseUnit.MMOL.asText)
        assertThat(json.getString("notes")).isEqualTo("wizard")
        assertThat(json.getLong("date")).isEqualTo(1000L)
        assertThat(json.has("bolusCalculatorResult")).isTrue() // Gson-serialized blob embedded
    }

    @Test
    fun includesNightscoutIdOnAdd() {
        val json = bcr(IDs(nightscoutId = "N")).toJson(isAdd = true, dateUtil = dateUtil, profileUtil = profileUtil)
        assertThat(json.getString("_id")).isEqualTo("N")
    }

    @Test
    fun omitsNightscoutIdWhenNotAdd() {
        val json = bcr(IDs(nightscoutId = "N")).toJson(isAdd = false, dateUtil = dateUtil, profileUtil = profileUtil)
        assertThat(json.has("_id")).isFalse()
    }
}
