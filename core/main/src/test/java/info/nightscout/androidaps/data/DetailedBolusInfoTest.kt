package info.nightscout.androidaps.data

import android.content.Context
import com.google.gson.Gson
import info.nightscout.androidaps.TestBase
import info.nightscout.database.entities.Bolus
import info.nightscout.database.entities.BolusCalculatorResult
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.interfaces.pump.DetailedBolusInfo
import org.apache.commons.lang3.builder.EqualsBuilder
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.Mock

class DetailedBolusInfoTest : TestBase() {

    @Mock lateinit var context: Context

    @Test fun toStringShouldBeOverloaded() {
        val detailedBolusInfo = DetailedBolusInfo()
        Assertions.assertEquals(true, detailedBolusInfo.toJsonString().contains("insulin"))
    }

    @Test fun copyShouldCopyAllProperties() {
        val d1 = DetailedBolusInfo()
        d1.deliverAtTheLatest = 123
        val d2 = d1.copy()
        Assertions.assertTrue(EqualsBuilder.reflectionEquals(d2, d1, arrayListOf("id")))
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
        Assertions.assertEquals(1L, deserialized.bolusCalculatorResult?.timestamp)
        Assertions.assertEquals(DetailedBolusInfo.EventType.BOLUS_WIZARD, deserialized.eventType)
        // Context should be excluded
        Assertions.assertNull(deserialized.context)
    }

    @Test
    fun generateTherapyEventTest() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.timestamp = 1000
        detailedBolusInfo.notes = "note"
        detailedBolusInfo.mgdlGlucose = 180.0
        detailedBolusInfo.glucoseType = DetailedBolusInfo.MeterType.FINGER

        val therapyEvent = detailedBolusInfo.createTherapyEvent()
        Assertions.assertEquals(1000L, therapyEvent.timestamp)
        Assertions.assertEquals(TherapyEvent.Type.MEAL_BOLUS, therapyEvent.type)
        Assertions.assertEquals(TherapyEvent.GlucoseUnit.MGDL, therapyEvent.glucoseUnit)
        Assertions.assertEquals("note", therapyEvent.note)
        Assertions.assertEquals(180.0, therapyEvent.glucose)
        Assertions.assertEquals(TherapyEvent.MeterType.FINGER, therapyEvent.glucoseType)
    }

    @Test
    fun generateBolus() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.timestamp = 1000
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.SMB
        detailedBolusInfo.insulin = 7.0

        val bolus = detailedBolusInfo.createBolus()
        Assertions.assertEquals(1000L, bolus.timestamp)
        Assertions.assertEquals(Bolus.Type.SMB, bolus.type)
        Assertions.assertEquals(7.0, bolus.amount, 0.01)
    }

    @Test
    fun generateCarbs() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.timestamp = 1000
        detailedBolusInfo.carbs = 6.0

        val carbs = detailedBolusInfo.createCarbs()
        Assertions.assertEquals(1000L, carbs.timestamp)
        Assertions.assertEquals(6.0, carbs.amount, 0.01)
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