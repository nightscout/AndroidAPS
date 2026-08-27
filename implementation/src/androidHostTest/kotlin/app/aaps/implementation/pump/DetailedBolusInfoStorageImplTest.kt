package app.aaps.implementation.pump

import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Covers [DetailedBolusInfoStorageImpl]: add + the two findDetailedBolusInfo match passes (exact bolus,
 * time-only fallback), the ±1 min window, remove-on-find, and loadStore JSON round-trip.
 */
class DetailedBolusInfoStorageImplTest : TestBase() {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var rh: ResourceHelper

    private lateinit var sut: DetailedBolusInfoStorageImpl

    @BeforeEach
    fun setup() {
        whenever(preferences.get(StringNonKey.BolusInfoStorage)).thenReturn("")
        sut = DetailedBolusInfoStorageImpl(aapsLogger, preferences, rh)
    }

    private fun info(time: Long, insulin: Double) = DetailedBolusInfo().apply {
        timestamp = time
        this.insulin = insulin
    }

    @Test
    fun findByExactBolusAndTime_returnsThenRemoves() {
        val d = info(1000L, 2.0)
        sut.add(d)
        assertThat(sut.findDetailedBolusInfo(1000L, 2.0)).isSameInstanceAs(d)
        assertThat(sut.findDetailedBolusInfo(1000L, 2.0)).isNull() // removed on find
    }

    @Test
    fun findWithinOneMinuteWindow() {
        sut.add(info(100_000L, 1.5))
        assertThat(sut.findDetailedBolusInfo(100_000L + 30_000L, 1.5)).isNotNull()
    }

    @Test
    fun noMatchOutsideWindow_returnsNull() {
        sut.add(info(100_000L, 1.5))
        assertThat(sut.findDetailedBolusInfo(100_000L + 120_000L, 1.5)).isNull()
    }

    @Test
    fun timeOnlyFallback_whenRequestedBolusNotGreaterThanStored() {
        sut.add(info(100_000L, 3.0))
        val found = sut.findDetailedBolusInfo(100_000L, 1.0) // 1.0 <= 3.0 → time-only match
        assertThat(found).isNotNull()
        assertThat(found!!.insulin).isEqualTo(3.0)
    }

    @Test
    fun loadStore_parsesExistingJson() {
        val json = Gson().toJson(listOf(info(5000L, 2.5)))
        whenever(preferences.get(StringNonKey.BolusInfoStorage)).thenReturn(json)
        val loaded = DetailedBolusInfoStorageImpl(aapsLogger, preferences, rh)
        assertThat(loaded.findDetailedBolusInfo(5000L, 2.5)).isNotNull()
    }
}
