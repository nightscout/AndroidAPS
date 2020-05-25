package info.nightscout.androidaps.plugins.pump.common.bolusInfo

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.data.DetailedBolusInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DetailedBolusInfoStorageTest : TestBase() {

    private val info1 = DetailedBolusInfo()
    private val info2 = DetailedBolusInfo()
    private val info3 = DetailedBolusInfo()

    lateinit var detailedBolusInfoStorage: DetailedBolusInfoStorage
    
    init {
        info1.date = 1000000
        info1.insulin = 3.0
        info2.date = 1000001
        info2.insulin = 4.0
        info3.date = 2000000
        info3.insulin = 5.0
    }

    @Before
    fun prepare() {
        detailedBolusInfoStorage = DetailedBolusInfoStorage(aapsLogger)
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
        assertEquals(0, detailedBolusInfoStorage.store.size)
        detailedBolusInfoStorage.add(info1)
        assertEquals(1, detailedBolusInfoStorage.store.size)
    }

    @Test
    fun findDetailedBolusInfo() {

        // Look for exact bolus
        setUp()
        var d: DetailedBolusInfo? = detailedBolusInfoStorage.findDetailedBolusInfo(1000000, 4.0)
        assertEquals(4.0, d!!.insulin, 0.01)
        assertEquals(2, detailedBolusInfoStorage.store.size)
        // Look for exact bolus
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000000, 3.0)
        assertEquals(3.0, d!!.insulin, 0.01)
        assertEquals(2, detailedBolusInfoStorage.store.size)
        // With less insulin (bolus not delivered completely). Should return first one matching date
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 2.0)
        assertEquals(3.0, d!!.insulin, 0.01)
        assertEquals(2, detailedBolusInfoStorage.store.size)
        // With less insulin (bolus not delivered completely). Should return first one matching date
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 3.5)
        assertEquals(4.0, d!!.insulin, 0.01)
        assertEquals(2, detailedBolusInfoStorage.store.size)
        // With more insulin should return null
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1000500, 4.5)
        assertNull(d)
        assertEquals(3, detailedBolusInfoStorage.store.size)
        // With more than one minute off should return null
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1070000, 4.0)
        assertNull(d)
        assertEquals(3, detailedBolusInfoStorage.store.size)
        // Use last, if bolus size is the same
        setUp()
        d = detailedBolusInfoStorage.findDetailedBolusInfo(1070000, 5.0)
        assertEquals(5.0, d!!.insulin, 0.01)
        assertEquals(2, detailedBolusInfoStorage.store.size)

    }
}