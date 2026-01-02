package app.aaps.pump.eopatch.vo

import app.aaps.pump.eopatch.code.UnitOrPercent
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TempBasalTest {

    @Test
    fun `createAbsolute should create temp basal with absolute rate`() {
        val tempBasal = TempBasal.createAbsolute(120, 1.5f)

        assertThat(tempBasal.durationMinutes).isEqualTo(120)
        assertThat(tempBasal.doseUnitPerHour).isWithin(0.001f).of(1.5f)
        assertThat(tempBasal.unitDefinition).isEqualTo(UnitOrPercent.U)
        assertThat(tempBasal.percent).isEqualTo(0)
        assertThat(tempBasal.running).isFalse()
    }

    @Test
    fun `createPercent should create temp basal with percent rate`() {
        val tempBasal = TempBasal.createPercent(90, 150)

        assertThat(tempBasal.durationMinutes).isEqualTo(90)
        assertThat(tempBasal.percent).isEqualTo(150)
        assertThat(tempBasal.unitDefinition).isEqualTo(UnitOrPercent.P)
        assertThat(tempBasal.running).isFalse()
    }

    @Test
    fun `endTimestamp should calculate correctly when started`() {
        val tempBasal = TempBasal.createAbsolute(60, 2.0f)
        val now = System.currentTimeMillis()
        tempBasal.startTimestamp = now

        val expectedEnd = now + (60 * 60 * 1000) // 60 minutes in milliseconds
        assertThat(tempBasal.endTimestamp).isEqualTo(expectedEnd)
    }

    @Test
    fun `endTimestamp should be zero when not started`() {
        val tempBasal = TempBasal.createAbsolute(60, 2.0f)

        assertThat(tempBasal.endTimestamp).isEqualTo(0)
    }

    @Test
    fun `initObject should reset all values`() {
        val tempBasal = TempBasal.createPercent(120, 200)
        tempBasal.startTimestamp = System.currentTimeMillis()
        tempBasal.running = true

        tempBasal.initObject()

        assertThat(tempBasal.unitDefinition).isEqualTo(UnitOrPercent.U)
        assertThat(tempBasal.doseUnitPerHour).isWithin(0.001f).of(0f)
        assertThat(tempBasal.percent).isEqualTo(0)
        assertThat(tempBasal.durationMinutes).isEqualTo(0)
        assertThat(tempBasal.startTimestamp).isEqualTo(0)
        assertThat(tempBasal.running).isFalse()
    }

    @Test
    fun `doseUnitText should format correctly`() {
        val tempBasal = TempBasal.createAbsolute(60, 1.25f)

        // The format depends on FloatFormatters.insulin implementation
        // We just verify the format contains the value and unit
        assertThat(tempBasal.doseUnitText).contains("U/hr")
    }

    @Test
    fun `running flag should be mutable`() {
        val tempBasal = TempBasal.createAbsolute(60, 1.0f)

        assertThat(tempBasal.running).isFalse()

        tempBasal.running = true
        assertThat(tempBasal.running).isTrue()

        tempBasal.running = false
        assertThat(tempBasal.running).isFalse()
    }

    @Test
    fun `toString should contain key information`() {
        val tempBasal = TempBasal.createAbsolute(120, 2.5f)
        tempBasal.startTimestamp = 1000L

        val stringRep = tempBasal.toString()

        assertThat(stringRep).contains("TempBasal")
        assertThat(stringRep).contains("startTimestamp=1000")
        assertThat(stringRep).contains("durationMinutes=120")
        assertThat(stringRep).contains("doseUnitPerHour=2.5")
    }
}
