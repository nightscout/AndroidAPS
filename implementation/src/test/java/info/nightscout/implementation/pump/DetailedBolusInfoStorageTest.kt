package info.nightscout.implementation.pump

import info.nightscout.implementation.R
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
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
        Assertions.assertEquals(0, detailedBolusInfoStorage.store.size)
        detailedBolusInfoStorage.add(info1)
        Assertions.assertEquals(1, detailedBolusInfoStorage.store.size)
    }

    @Test
    fun findDetailedBolusInfo() {

        // Look for exact bolus
        setUp()
        var d: DetailedBolusInfo? = detailedBolusInfoStorage.findDetailedBolusInfo(1000000, 4.0)
        Assertions.assertEquals(4.0, d!!.insulin, 0.01)
        Assertions.assertEquals(2, detailedBolusInfoStorage.store.size)
        // Look for exact bolus
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000000, 3.0)
        Assertions.assertEquals(3.0, d!!.insulin, 0.01)
        Assertions.assertEquals(2, detailedBolusInfoStorage.store.size)
        // With less insulin (bolus not delivered completely). Should return first one matching date
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 2.0)
        Assertions.assertEquals(3.0, d!!.insulin, 0.01)
        Assertions.assertEquals(2, detailedBolusInfoStorage.store.size)
        // With less insulin (bolus not delivered completely). Should return first one matching date
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 3.5)
        Assertions.assertEquals(4.0, d!!.insulin, 0.01)
        Assertions.assertEquals(2, detailedBolusInfoStorage.store.size)
        // With more insulin should return null
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 4.5)
        Assertions.assertNull(d)
        Assertions.assertEquals(3, detailedBolusInfoStorage.store.size)
        // With more than one minute off should return null
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1070000, 4.0)
        Assertions.assertNull(d)
        Assertions.assertEquals(3, detailedBolusInfoStorage.store.size)
        // Use last, if bolus size is the same
//        setUp()
//        d = detailedBolusInfoStorage.findDetailedBolusInfo(1070000, 5.0)
//        assertEquals(5.0, d!!.insulin, 0.01)
//        assertEquals(2, detailedBolusInfoStorage.store.size)

    }
}