package info.nightscout.implementation.pump

import com.google.common.truth.Truth.assertThat
import info.nightscout.implementation.R
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class DetailedBolusInfoStorageTest : TestBase() {

    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper

    private val info1 = DetailedBolusInfo()
    private val info2 = DetailedBolusInfo()
    private val info3 = DetailedBolusInfo()

    private lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorageImpl

    init {
        info1.timestamp = 1000000
        info1.insulin = 3.0
        info2.timestamp = 1000001
        info2.insulin = 4.0
        info3.timestamp = 2000000
        info3.insulin = 5.0
    }

    @BeforeEach
    fun prepare() {
        Mockito.`when`(sp.getString(rh.gs(R.string.key_bolus_storage), "")).thenReturn("")
        detailedBolusInfoStorage = DetailedBolusInfoStorageImpl(aapsLogger, sp, rh)
    }

    private fun setUp() {
        detailedBolusInfoStorage.store.clear()
        detailedBolusInfoStorage.add(info1)
        detailedBolusInfoStorage.add(info2)
        detailedBolusInfoStorage.add(info3)
    }

    @Test
    fun add() {
        detailedBolusInfoStorage.store.clear()
        assertThat(detailedBolusInfoStorage.store).isEmpty()
        detailedBolusInfoStorage.add(info1)
        assertThat(detailedBolusInfoStorage.store).hasSize(1)
    }

    @Test
    fun findDetailedBolusInfo() {

        // Look for exact bolus
        setUp()
        var d: DetailedBolusInfo? = detailedBolusInfoStorage.findDetailedBolusInfo(1000000, 4.0)
        assertThat(d!!.insulin).isWithin(0.01).of(4.0)
        assertThat(detailedBolusInfoStorage.store).hasSize(2)
        // Look for exact bolus
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000000, 3.0)
        assertThat(d!!.insulin).isWithin(0.01).of(3.0)
        assertThat(detailedBolusInfoStorage.store).hasSize(2)
        // With less insulin (bolus not delivered completely). Should return first one matching date
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 2.0)
        assertThat(d!!.insulin).isWithin(0.01).of(3.0)
        assertThat(detailedBolusInfoStorage.store).hasSize(2)
        // With less insulin (bolus not delivered completely). Should return first one matching date
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 3.5)
        assertThat(d!!.insulin).isWithin(0.01).of(4.0)
        assertThat(detailedBolusInfoStorage.store).hasSize(2)
        // With more insulin should return null
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 4.5)
        assertThat(d).isNull()
        assertThat(detailedBolusInfoStorage.store).hasSize(3)
        // With more than one minute off should return null
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1070000, 4.0)
        assertThat(d).isNull()
        assertThat(detailedBolusInfoStorage.store).hasSize(3)
        // Use last, if bolus size is the same
//        setUp()
//        d = detailedBolusInfoStorage.findDetailedBolusInfo(1070000, 5.0)
//        assertThat( d!!.insulin).isWithin(0.01).of(5.0)
//        assertThat(detailedBolusInfoStorage.store).hasSize(2)

    }
}
