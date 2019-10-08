package info.nightscout.androidaps.plugins.pump.common.bolusInfo

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.utils.SP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class DetailedBolusInfoStorageTest {

    private val info1 = DetailedBolusInfo()
    private val info2 = DetailedBolusInfo()
    private val info3 = DetailedBolusInfo()

    init {
        info1.date = 1000000
        info1.insulin = 3.0
        info2.date = 1000001
        info2.insulin = 4.0
        info3.date = 2000000
        info3.insulin = 5.0
    }

    private fun setUp() {
        DetailedBolusInfoStorage.store.clear()
        DetailedBolusInfoStorage.add(info1)
        DetailedBolusInfoStorage.add(info2)
        DetailedBolusInfoStorage.add(info3)
    }

    @Test
    fun add() {
        DetailedBolusInfoStorage.store.clear()
        assertEquals(0, DetailedBolusInfoStorage.store.size)
        DetailedBolusInfoStorage.add(info1)
        assertEquals(1, DetailedBolusInfoStorage.store.size)
    }

    @Test
    @PrepareForTest(MainApp::class, L::class, SP::class)
    fun findDetailedBolusInfo() {
        prepareMainApp()
        prepareSP()
        prepareLogging()

        // Look for exact bolus
        setUp()
        var d: DetailedBolusInfo? = DetailedBolusInfoStorage.findDetailedBolusInfo(1000000, 4.0)
        assertEquals(4.0, d!!.insulin, 0.01)
        assertEquals(2, DetailedBolusInfoStorage.store.size)
        // Look for exact bolus
        setUp()
        d = DetailedBolusInfoStorage.findDetailedBolusInfo(1000000, 3.0)
        assertEquals(3.0, d!!.insulin, 0.01)
        assertEquals(2, DetailedBolusInfoStorage.store.size)
        // With less insulin (bolus not delivered completely). Should return first one matching date
        setUp()
        d = DetailedBolusInfoStorage.findDetailedBolusInfo(1000500, 2.0)
        assertEquals(3.0, d!!.insulin, 0.01)
        assertEquals(2, DetailedBolusInfoStorage.store.size)
        // With less insulin (bolus not delivered completely). Should return first one matching date
        setUp()
        d = DetailedBolusInfoStorage.findDetailedBolusInfo(1000500, 3.5)
        assertEquals(4.0, d!!.insulin, 0.01)
        assertEquals(2, DetailedBolusInfoStorage.store.size)
        // With more insulin should return null
        setUp()
        d = DetailedBolusInfoStorage.findDetailedBolusInfo(1000500, 4.5)
        assertNull(d)
        assertEquals(3, DetailedBolusInfoStorage.store.size)
        // With more than one minute off should return null
        setUp()
        d = DetailedBolusInfoStorage.findDetailedBolusInfo(1070000, 4.0)
        assertNull(d)
        assertEquals(3, DetailedBolusInfoStorage.store.size)

    }

    private fun prepareMainApp() {
        PowerMockito.mockStatic(MainApp::class.java)
        val mainApp = Mockito.mock<MainApp>(MainApp::class.java)
        Mockito.`when`(MainApp.instance()).thenReturn(mainApp)
        Mockito.`when`(MainApp.gs(ArgumentMatchers.anyInt())).thenReturn("some dummy string")
    }

    private fun prepareSP() {
        PowerMockito.mockStatic(SP::class.java)
    }

    private fun prepareLogging() {
        PowerMockito.mockStatic(L::class.java)
        Mockito.`when`(L.isEnabled(Mockito.any())).thenReturn(true)
    }

}