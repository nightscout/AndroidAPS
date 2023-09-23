package info.nightscout.core.data

import android.content.Context
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.interfaces.pump.DetailedBolusInfo
import org.apache.commons.lang3.builder.EqualsBuilder
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DetailedBolusInfoTest : TestBase() {

    @Mock lateinit var context: Context

    @Test fun toStringShouldBeOverloaded() {
        val detailedBolusInfo = DetailedBolusInfo()
        assertThat(detailedBolusInfo.toJsonString()).contains("insulin")
    }

    @Test fun copyShouldCopyAllProperties() {
        val d1 = DetailedBolusInfo()
        d1.deliverAtTheLatest = 123
        val d2 = d1.copy()
        assertThat(EqualsBuilder.reflectionEquals(d2, d1, arrayListOf("id"))).isTrue()
    }

    private fun fromJsonString(json: String): DetailedBolusInfo =
        Gson().fromJson(json, DetailedBolusInfo::class.java)

    private fun DetailedBolusInfo.toJsonString(): String = Gson().toJson(this)

    @Test
    fun shouldAllowSerialization() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.bolusCalculatorResult = createBolusCalculatorResult()
        detailedBolusInfo.context = context
        detailedBolusInfo.eventType = DetailedBolusInfo.EventType.BOLUS_WIZARD
        val serialized = detailedBolusInfo.toJsonString()
        val deserialized = fromJsonString(serialized)
        assertThat(deserialized.bolusCalculatorResult?.timestamp).isEqualTo(1L)
        assertThat(deserialized.eventType).isEqualTo(DetailedBolusInfo.EventType.BOLUS_WIZARD)
        // Context should be excluded
        assertThat(deserialized.context).isNull()
    }

    @Test
    fun generateTherapyEventTest() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.timestamp = 1000
        detailedBolusInfo.notes = "note"
        detailedBolusInfo.mgdlGlucose = 180.0
        detailedBolusInfo.glucoseType = DetailedBolusInfo.MeterType.FINGER

        val therapyEvent = detailedBolusInfo.createTherapyEvent()
        assertThat(therapyEvent.timestamp).isEqualTo(1000L)
        assertThat(therapyEvent.type).isEqualTo(TherapyEvent.Type.MEAL_BOLUS)
        assertThat(therapyEvent.glucoseUnit).isEqualTo(TherapyEvent.GlucoseUnit.MGDL)
        assertThat(therapyEvent.note).isEqualTo("note")
        assertThat(therapyEvent.glucose).isEqualTo(180.0)
        assertThat(therapyEvent.glucoseType).isEqualTo(TherapyEvent.MeterType.FINGER)
    }

    @Test
    fun generateBolus() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.timestamp = 1000
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.SMB
        detailedBolusInfo.insulin = 7.0

        val bolus = detailedBolusInfo.createBolus()
        assertThat(bolus.timestamp).isEqualTo(1000L)
        assertThat(bolus.type).isEqualTo(Bolus.Type.SMB)
        assertThat(bolus.amount).isWithin(0.01).of(7.0)
    }

    @Test
    fun generateCarbs() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.timestamp = 1000
        detailedBolusInfo.carbs = 6.0

        val carbs = detailedBolusInfo.createCarbs()
        assertThat(carbs.timestamp).isEqualTo(1000L)
        assertThat(carbs.amount).isWithin(0.01).of(6.0)
    }

    private fun createBolusCalculatorResult(): BolusCalculatorResult =
        BolusCalculatorResult(
            timestamp = 1,
            targetBGLow = 5.0,
            targetBGHigh = 5.0,
            isf = 5.0,
            ic = 5.0,
            bolusIOB = 1.0,
            wasBolusIOBUsed = true,
            basalIOB = 1.0,
            wasBasalIOBUsed = true,
            glucoseValue = 10.0,
            wasGlucoseUsed = true,
            glucoseDifference = 1.0,
            glucoseInsulin = 1.0,
            glucoseTrend = 1.0,
            wasTrendUsed = true,
            trendInsulin = 1.0,
            cob = 10.0,
            wasCOBUsed = true,
            cobInsulin = 1.0,
            carbs = 5.0,
            wereCarbsUsed = true,
            carbsInsulin = 1.0,
            otherCorrection = 1.0,
            wasSuperbolusUsed = true,
            superbolusInsulin = 1.0,
            wasTempTargetUsed = true,
            totalInsulin = 15.0,
            percentageCorrection = 50,
            profileName = "profile",
            note = ""
        )
}
