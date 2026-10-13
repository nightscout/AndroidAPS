package app.aaps.implementation.stats

import androidx.collection.LongSparseArray
import app.aaps.core.data.aps.AverageTDD
import app.aaps.core.data.model.TDD
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.MidnightTime
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class TddCalculatorImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var iobCobCalculator: IobCobCalculator
    @Mock lateinit var persistenceLayer: PersistenceLayer

    private lateinit var tddCalculator: TddCalculatorImpl

    private val now = 1000000000L
    private val midnight = MidnightTime.calc(now)

    @BeforeEach
    fun setup() {
        tddCalculator = TddCalculatorImpl(aapsLogger, rh, activePlugin, profileFunction, dateUtil, iobCobCalculator, persistenceLayer)
        whenever(dateUtil.now()).thenReturn(now)
    }

    @Test
    fun `averageTDD returns null when input is null`() {
        val result = tddCalculator.averageTDD(null)
        assertThat(result).isNull()
    }

    @Test
    fun `averageTDD returns null when input is empty`() {
        val tdds = LongSparseArray<TDD>()
        val result = tddCalculator.averageTDD(tdds)
        assertThat(result).isNull()
    }

    @Test
    fun `averageTDD calculates correct average for single day`() {
        val tdds = LongSparseArray<TDD>()
        val tdd = TDD(timestamp = midnight).apply {
            basalAmount = 20.0
            bolusAmount = 10.0
            totalAmount = 30.0
            carbs = 150.0
        }
        tdds.put(midnight, tdd)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.data?.basalAmount).isEqualTo(20.0)
        assertThat(result?.data?.bolusAmount).isEqualTo(10.0)
        assertThat(result?.data?.totalAmount).isEqualTo(30.0)
        assertThat(result?.data?.carbs).isEqualTo(150.0)
        assertThat(result?.allDaysHaveCarbs).isTrue()
    }

    @Test
    fun `averageTDD calculates correct average for multiple days`() {
        val tdds = LongSparseArray<TDD>()

        val tdd1 = TDD(timestamp = midnight).apply {
            basalAmount = 20.0
            bolusAmount = 10.0
            totalAmount = 30.0
            carbs = 150.0
        }
        val tdd2 = TDD(timestamp = midnight + T.days(1).msecs()).apply {
            basalAmount = 30.0
            bolusAmount = 20.0
            totalAmount = 50.0
            carbs = 200.0
        }
        val tdd3 = TDD(timestamp = midnight + T.days(2).msecs()).apply {
            basalAmount = 25.0
            bolusAmount = 15.0
            totalAmount = 40.0
            carbs = 175.0
        }

        tdds.put(midnight, tdd1)
        tdds.put(midnight + T.days(1).msecs(), tdd2)
        tdds.put(midnight + T.days(2).msecs(), tdd3)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.data?.basalAmount).isWithin(0.01).of(25.0)  // (20+30+25)/3
        assertThat(result?.data?.bolusAmount).isWithin(0.01).of(15.0)  // (10+20+15)/3
        assertThat(result?.data?.totalAmount).isWithin(0.01).of(40.0)  // (30+50+40)/3
        assertThat(result?.data?.carbs).isWithin(0.01).of(175.0)  // (150+200+175)/3
        assertThat(result?.allDaysHaveCarbs).isTrue()
    }

    @Test
    fun `averageTDD detects days without carbs`() {
        val tdds = LongSparseArray<TDD>()

        val tdd1 = TDD(timestamp = midnight).apply {
            basalAmount = 20.0
            bolusAmount = 10.0
            totalAmount = 30.0
            carbs = 150.0
        }
        val tdd2 = TDD(timestamp = midnight + T.days(1).msecs()).apply {
            basalAmount = 30.0
            bolusAmount = 20.0
            totalAmount = 50.0
            carbs = 0.0  // No carbs
        }

        tdds.put(midnight, tdd1)
        tdds.put(midnight + T.days(1).msecs(), tdd2)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.allDaysHaveCarbs).isFalse()
    }

    @Test
    fun `averageTDD handles zero values correctly`() {
        val tdds = LongSparseArray<TDD>()

        val tdd1 = TDD(timestamp = midnight).apply {
            basalAmount = 0.0
            bolusAmount = 0.0
            totalAmount = 0.0
            carbs = 0.0
        }
        val tdd2 = TDD(timestamp = midnight + T.days(1).msecs()).apply {
            basalAmount = 0.0
            bolusAmount = 0.0
            totalAmount = 0.0
            carbs = 0.0
        }

        tdds.put(midnight, tdd1)
        tdds.put(midnight + T.days(1).msecs(), tdd2)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.data?.basalAmount).isEqualTo(0.0)
        assertThat(result?.data?.bolusAmount).isEqualTo(0.0)
        assertThat(result?.data?.totalAmount).isEqualTo(0.0)
        assertThat(result?.data?.carbs).isEqualTo(0.0)
        assertThat(result?.allDaysHaveCarbs).isFalse()
    }

    @Test
    fun `averageTDD handles large numbers correctly`() {
        val tdds = LongSparseArray<TDD>()

        val tdd1 = TDD(timestamp = midnight).apply {
            basalAmount = 1000.0
            bolusAmount = 500.0
            totalAmount = 1500.0
            carbs = 5000.0
        }
        val tdd2 = TDD(timestamp = midnight + T.days(1).msecs()).apply {
            basalAmount = 2000.0
            bolusAmount = 1000.0
            totalAmount = 3000.0
            carbs = 10000.0
        }

        tdds.put(midnight, tdd1)
        tdds.put(midnight + T.days(1).msecs(), tdd2)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.data?.basalAmount).isEqualTo(1500.0)
        assertThat(result?.data?.bolusAmount).isEqualTo(750.0)
        assertThat(result?.data?.totalAmount).isEqualTo(2250.0)
        assertThat(result?.data?.carbs).isEqualTo(7500.0)
    }

    @Test
    fun `averageTDD handles fractional values correctly`() {
        val tdds = LongSparseArray<TDD>()

        val tdd1 = TDD(timestamp = midnight).apply {
            basalAmount = 20.5
            bolusAmount = 10.3
            totalAmount = 30.8
            carbs = 150.7
        }
        val tdd2 = TDD(timestamp = midnight + T.days(1).msecs()).apply {
            basalAmount = 25.7
            bolusAmount = 15.9
            totalAmount = 41.6
            carbs = 200.3
        }
        val tdd3 = TDD(timestamp = midnight + T.days(2).msecs()).apply {
            basalAmount = 22.3
            bolusAmount = 12.1
            totalAmount = 34.4
            carbs = 175.5
        }

        tdds.put(midnight, tdd1)
        tdds.put(midnight + T.days(1).msecs(), tdd2)
        tdds.put(midnight + T.days(2).msecs(), tdd3)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.data?.basalAmount).isWithin(0.01).of((20.5 + 25.7 + 22.3) / 3.0)
        assertThat(result?.data?.bolusAmount).isWithin(0.01).of((10.3 + 15.9 + 12.1) / 3.0)
        assertThat(result?.data?.totalAmount).isWithin(0.01).of((30.8 + 41.6 + 34.4) / 3.0)
        assertThat(result?.data?.carbs).isWithin(0.01).of((150.7 + 200.3 + 175.5) / 3.0)
    }

    @Test
    fun `averageTDD sets timestamp to current time`() {
        val tdds = LongSparseArray<TDD>()
        val tdd = TDD(timestamp = midnight).apply {
            basalAmount = 20.0
            bolusAmount = 10.0
            totalAmount = 30.0
            carbs = 150.0
        }
        tdds.put(midnight, tdd)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.data?.timestamp).isEqualTo(now)
    }

    @Test
    fun `averageTDD handles seven days correctly`() {
        val tdds = LongSparseArray<TDD>()

        for (i in 0 until 7) {
            val tdd = TDD(timestamp = midnight + T.days(i.toLong()).msecs()).apply {
                basalAmount = 20.0 + i
                bolusAmount = 10.0 + i
                totalAmount = 30.0 + (i * 2)
                carbs = 150.0 + (i * 10)
            }
            tdds.put(midnight + T.days(i.toLong()).msecs(), tdd)
        }

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        // Average of (20, 21, 22, 23, 24, 25, 26) = 23
        assertThat(result?.data?.basalAmount).isWithin(0.01).of(23.0)
        // Average of (10, 11, 12, 13, 14, 15, 16) = 13
        assertThat(result?.data?.bolusAmount).isWithin(0.01).of(13.0)
        // Average of (30, 32, 34, 36, 38, 40, 42) = 36
        assertThat(result?.data?.totalAmount).isWithin(0.01).of(36.0)
        // Average of (150, 160, 170, 180, 190, 200, 210) = 180
        assertThat(result?.data?.carbs).isWithin(0.01).of(180.0)
        assertThat(result?.allDaysHaveCarbs).isTrue()
    }

    @Test
    fun `averageTDD respects original TDD immutability`() {
        val tdds = LongSparseArray<TDD>()
        val originalBasal = 20.0
        val originalBolus = 10.0
        val tdd = TDD(timestamp = midnight).apply {
            basalAmount = originalBasal
            bolusAmount = originalBolus
            totalAmount = 30.0
            carbs = 150.0
        }
        tdds.put(midnight, tdd)

        tddCalculator.averageTDD(tdds)

        // Verify original TDD wasn't modified
        assertThat(tdd.basalAmount).isEqualTo(originalBasal)
        assertThat(tdd.bolusAmount).isEqualTo(originalBolus)
    }

    @Test
    fun `averageTDD handles mixed carb data correctly`() {
        val tdds = LongSparseArray<TDD>()

        // Day 1: Has carbs
        val tdd1 = TDD(timestamp = midnight).apply {
            basalAmount = 20.0
            bolusAmount = 10.0
            totalAmount = 30.0
            carbs = 150.0
        }
        // Day 2: No carbs
        val tdd2 = TDD(timestamp = midnight + T.days(1).msecs()).apply {
            basalAmount = 25.0
            bolusAmount = 15.0
            totalAmount = 40.0
            carbs = 0.0
        }
        // Day 3: Has carbs
        val tdd3 = TDD(timestamp = midnight + T.days(2).msecs()).apply {
            basalAmount = 22.0
            bolusAmount = 12.0
            totalAmount = 34.0
            carbs = 175.0
        }

        tdds.put(midnight, tdd1)
        tdds.put(midnight + T.days(1).msecs(), tdd2)
        tdds.put(midnight + T.days(2).msecs(), tdd3)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.allDaysHaveCarbs).isFalse()
        // Carb average still calculated: (150 + 0 + 175) / 3 = 108.33...
        assertThat(result?.data?.carbs).isWithin(0.01).of(108.33)
    }

    @Test
    fun `averageTDD result has correct AverageTDD structure`() {
        val tdds = LongSparseArray<TDD>()
        val tdd = TDD(timestamp = midnight).apply {
            basalAmount = 20.0
            bolusAmount = 10.0
            totalAmount = 30.0
            carbs = 150.0
        }
        tdds.put(midnight, tdd)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isInstanceOf(AverageTDD::class.java)
        assertThat(result?.data).isInstanceOf(TDD::class.java)
        assertThat(result?.allDaysHaveCarbs).isInstanceOf(Boolean::class.java)
    }

    @Test
    fun `averageTDD handles very small fractional differences`() {
        val tdds = LongSparseArray<TDD>()

        val tdd1 = TDD(timestamp = midnight).apply {
            basalAmount = 20.001
            bolusAmount = 10.001
            totalAmount = 30.002
            carbs = 150.001
        }
        val tdd2 = TDD(timestamp = midnight + T.days(1).msecs()).apply {
            basalAmount = 20.002
            bolusAmount = 10.002
            totalAmount = 30.004
            carbs = 150.002
        }

        tdds.put(midnight, tdd1)
        tdds.put(midnight + T.days(1).msecs(), tdd2)

        val result = tddCalculator.averageTDD(tdds)

        assertThat(result).isNotNull()
        assertThat(result?.data?.basalAmount).isWithin(0.0001).of(20.0015)
        assertThat(result?.data?.bolusAmount).isWithin(0.0001).of(10.0015)
    }
}
