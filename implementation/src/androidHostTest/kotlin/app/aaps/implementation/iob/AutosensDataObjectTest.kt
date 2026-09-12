package app.aaps.implementation.iob

import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Covers [AutosensDataObject] carbs bookkeeping: cloneCarbsList deep copy, removeOldCarbs expiry
 * (AbsorptionMaxTime vs AbsorptionCutOff) + cob reduction, deductAbsorbedCarbs distribution, and toString.
 */
class AutosensDataObjectTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var dateUtil: DateUtil

    private fun sut() = AutosensDataObject(aapsLogger, preferences, dateUtil)

    private fun carb(time: Long, remaining: Double, carbs: Double = 20.0) =
        AutosensData.CarbsInPast(time = time, carbs = carbs, min5minCarbImpact = 3.0, remaining = remaining)

    @Test
    fun cloneCarbsList_deepCopies() {
        val o = sut()
        val c = carb(1000L, remaining = 10.0)
        o.activeCarbsList.add(c)
        val clone = o.cloneCarbsList()
        assertThat(clone).hasSize(1)
        assertThat(clone[0]).isNotSameInstanceAs(c)
        assertThat(clone[0].time).isEqualTo(1000L)
        assertThat(clone[0].remaining).isEqualTo(10.0)
    }

    @Test
    fun removeOldCarbs_removesExpiredAndReducesCob() {
        whenever(preferences.get(DoubleKey.AbsorptionMaxTime)).thenReturn(6.0) // hours
        val o = sut()
        val now = 100_000_000L
        o.cob = 30.0
        o.activeCarbsList.add(carb(now - 7 * 3_600_000L, remaining = 10.0)) // 7h old → expired
        o.activeCarbsList.add(carb(now - 1 * 3_600_000L, remaining = 5.0))  // 1h old → kept
        o.removeOldCarbs(now, isAAPSOrWeighted = true)
        assertThat(o.activeCarbsList).hasSize(1)
        assertThat(o.cob).isEqualTo(20.0) // 30 - 10 remaining of the removed carb
    }

    @Test
    fun removeOldCarbs_usesCutOffWhenNotWeighted() {
        whenever(preferences.get(DoubleKey.AbsorptionCutOff)).thenReturn(4.0)
        val o = sut()
        o.activeCarbsList.add(carb(0L, remaining = 5.0))
        o.removeOldCarbs(5 * 3_600_000L, isAAPSOrWeighted = false) // 5h > 4h cutoff → removed
        assertThat(o.activeCarbsList).isEmpty()
    }

    @Test
    fun deductAbsorbedCarbs_distributesAbsorptionAcrossRemaining() {
        val o = sut()
        o.this5MinAbsorption = 8.0
        o.activeCarbsList.add(carb(1000L, remaining = 5.0))
        o.activeCarbsList.add(carb(2000L, remaining = 10.0))
        o.deductAbsorbedCarbs()
        assertThat(o.activeCarbsList[0].remaining).isEqualTo(0.0)  // fully absorbed
        assertThat(o.activeCarbsList[1].remaining).isEqualTo(7.0)  // 3 of the 8 absorbed here
    }

    @Test
    fun toString_includesFormattedFields() {
        whenever(dateUtil.dateAndTimeString(anyLong())).thenReturn("2023-01-01 12:00")
        val o = sut().apply { pastSensitivity = "S"; cob = 12.0 }
        assertThat(o.toString()).contains("2023-01-01 12:00")
        assertThat(o.toString()).contains("pastSensitivity=S")
    }

    /**
     * Pins the whole line, not two fragments of it.
     *
     * This is a log line built by `String.format(Locale.ENGLISH, "%.02f", ...)` across twelve doubles.
     * Replacing that with a multiplatform formatter could silently change a separator (a comma on a
     * Czech phone), drop a trailing zero, or reorder a field, and nothing here would have noticed.
     *
     * One value below is deliberately a decimal tie. `4.005` is really `4.00499…` as a double, and the
     * two formatters disagree about it: `String.format` rounds from the shortest decimal text that
     * round-trips ("4.005", so up to 4.01), while [app.aaps.core.data.format.NumberFormat] rounds the
     * binary value it is given (below the tie, so down to 4.00). The 4.00 below is therefore a known,
     * accepted change from the JVM original - kept in the test on purpose so it stays visible rather
     * than being discovered again later. It only affects this debug line.
     */
    @Test
    fun toString_rendersEveryFieldInOrderWithTwoDecimalsAndADot() {
        whenever(dateUtil.dateAndTimeString(anyLong())).thenReturn("2023-01-01 12:00")
        val o = sut().apply {
            pastSensitivity = "S"
            delta = 1.5
            avgDelta = -2.25
            bgi = 0.0
            deviation = 3.456
            avgDeviation = 10.0
            this5MinAbsorption = 4.005
            carbsFromBolus = 7.0
            cob = 12.0
            slopeFromMaxDeviation = -0.125
            slopeFromMinDeviation = 100.0
        }

        assertThat(o.toString()).isEqualTo(
            "AutosensData: 2023-01-01 12:00 pastSensitivity=S  delta=1.50  avgDelta=-2.25 bgi=0.00 " +
                "deviation=3.46 avgDeviation=10.00 absorbed=4.00 carbsFromBolus=7.00 cob=12.00 " +
                "autosensRatio=1.00 slopeFromMaxDeviation=-0.13 slopeFromMinDeviation=100.00 activeCarbsList=[]"
        )
    }
}
