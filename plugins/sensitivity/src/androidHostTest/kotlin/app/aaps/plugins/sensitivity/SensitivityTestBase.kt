package app.aaps.plugins.sensitivity

import androidx.collection.LongSparseArray
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.shared.tests.TestBaseWithProfile
import org.junit.jupiter.api.BeforeEach
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Shared setup for the sensitivity [app.aaps.core.interfaces.aps.Sensitivity.detectSensitivity] tests.
 *
 * Test inputs use the SAME deviation on every data point on purpose. The median (used by AAPS and
 * Oref1) and the weighted average (used by WeightedAverage) of a constant series is that same
 * constant, independent of the sample size or the percentile interpolation. That makes the expected
 * ratio exact and easy to reason about:
 *
 *     basalOff = deviation * (60 / 5) / sens
 *     ratio    = 1 + basalOff / maxDailyBasal
 *
 * With [sens] = 50 and the test profile's max daily basal = 1.0 U/h, a deviation of +0.5 gives
 * ratio 1.12, -0.5 gives 0.88 and 0.0 gives 1.0.
 *
 * Full Autosens contribution needs at least `MIN_HOURS_FULL_AUTOSENS * 12 = 48` values, so the exact
 * ratio tests use 48 (AAPS/Weighted) or a full 24 h of data (Oref1).
 *
 * The plugins take the profile, site changes and profile switches as parameters, so the concrete
 * tests build those directly (see each test's `detect` helper) instead of stubbing suspend reads.
 */
abstract class SensitivityTestBase : TestBaseWithProfile() {

    protected val toTime = now
    protected val fromTime = now - T.hours(25).msecs()

    protected val sens = 50.0
    protected val cob = 25.0

    @BeforeEach
    fun prepareSensitivityBase() {
        // full autosens contribution needs >= 48 values; detection windows read this key
        whenever(preferences.get(IntKey.AutosensPeriod)).thenReturn(24)
        whenever(preferences.get(DoubleKey.AutosensMin)).thenReturn(0.7)
        whenever(preferences.get(DoubleKey.AutosensMax)).thenReturn(1.2)
    }

    /** Minimal [AutosensData] fake exposing only the fields the sensitivity plugins read. */
    protected class FakeAutosensData(
        override var time: Long,
        override var deviation: Double = 0.0,
        override var bg: Double = 100.0,
        override var validDeviation: Boolean = true,
        override var sens: Double = 50.0,
        override var cob: Double = 0.0,
        override var pastSensitivity: String = ""
    ) : AutosensData {

        override var activeCarbsList: MutableList<AutosensData.CarbsInPast> = ArrayList()
        override var this5MinAbsorption = 0.0
        override var carbsFromBolus = 0.0
        override var bgi = 0.0
        override var delta = 0.0
        override var avgDelta = 0.0
        override var slopeFromMaxDeviation = 0.0
        override var slopeFromMinDeviation = 999.0
        override var usedMinCarbsImpact = 0.0
        override var failOverToMinAbsorptionRate = false
        override var avgDeviation = 0.0
        override var absorbing = false
        override var mealCarbs = 0.0
        override var mealStartCounter = 999
        override var type = ""
        override var uam = false
        override var extraDeviation: MutableList<Double> = ArrayList()
        override var autosensResult = AutosensResult()

        override fun cloneCarbsList(): MutableList<AutosensData.CarbsInPast> = ArrayList()
        override fun deductAbsorbedCarbs() {}
        override fun removeOldCarbs(toTime: Long, isAAPSOrWeighted: Boolean) {}
    }

    /**
     * Build [count] data points spaced 5 minutes apart, the newest at [toTime], each carrying the
     * same [deviation].
     */
    protected fun constantSeries(
        count: Int,
        deviation: Double,
        bg: Double = 100.0,
        validDeviation: Boolean = true
    ): List<FakeAutosensData> =
        (0 until count).map { i ->
            FakeAutosensData(
                time = toTime - T.mins((i * 5).toLong()).msecs(),
                deviation = deviation,
                bg = bg,
                validDeviation = validDeviation,
                sens = sens,
                cob = cob
            )
        }

    /** Mock [AutosensDataStore] backed by [entries]; [current] is returned by getAutosensDataAtTime. */
    protected fun autosensDataStore(
        entries: List<AutosensData>,
        current: AutosensData? = entries.firstOrNull()
    ): AutosensDataStore {
        val table = LongSparseArray<AutosensData>()
        entries.forEach { table.put(it.time, it) }
        val ads = mock<AutosensDataStore>()
        whenever(ads.autosensDataTable).thenReturn(table)
        whenever(ads.getAutosensDataAtTime(any())).thenReturn(current)
        whenever(ads.lastDataTime(any())).thenReturn("")
        return ads
    }
}
