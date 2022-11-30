package info.nightscout.implementation.pump

import info.nightscout.androidaps.TestBase
import info.nightscout.interfaces.pump.PumpSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TemporaryBasalStorageTest : TestBase() {

    private val info1 = PumpSync.PumpState.TemporaryBasal(1000000, 1000, 3.0, false, PumpSync.TemporaryBasalType.NORMAL, 0L, 0L)
    private val info2 = PumpSync.PumpState.TemporaryBasal(1000001, 1000, 4.0, false, PumpSync.TemporaryBasalType.NORMAL, 0L, 0L)
    private val info3 = PumpSync.PumpState.TemporaryBasal(2000000, 1000, 5.0, false, PumpSync.TemporaryBasalType.NORMAL, 0L, 0L)

    private lateinit var temporaryBasalStorage: TemporaryBasalStorageImpl

    @BeforeEach
    fun prepare() {
        temporaryBasalStorage = TemporaryBasalStorageImpl(aapsLogger)
    }

    private fun setUp() {
        temporaryBasalStorage.store.clear()
        temporaryBasalStorage.add(info1)
        temporaryBasalStorage.add(info2)
        temporaryBasalStorage.add(info3)
    }

    @Test
    fun add() {
        temporaryBasalStorage.store.clear()
        assertEquals(0, temporaryBasalStorage.store.size)
        temporaryBasalStorage.add(info1)
        assertEquals(1, temporaryBasalStorage.store.size)
    }

    @Test
    fun findTemporaryBasal() {

        // Look for exact bolus
        setUp()
        var d = temporaryBasalStorage.findTemporaryBasal(1000000, 4.0)
        assertEquals(4.0, d!!.rate, 0.01)
        assertEquals(2, temporaryBasalStorage.store.size)
        // Look for exact bolus
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1000000, 3.0)
        assertEquals(3.0, d!!.rate, 0.01)
        assertEquals(2, temporaryBasalStorage.store.size)
        // With less rate (bolus not delivered completely). Should return first one matching date
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1000500, 2.0)
        assertEquals(3.0, d!!.rate, 0.01)
        assertEquals(2, temporaryBasalStorage.store.size)
        // With less rate (bolus not delivered completely). Should return first one matching date
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1000500, 3.5)
        assertEquals(4.0, d!!.rate, 0.01)
        assertEquals(2, temporaryBasalStorage.store.size)
        // With more rate should return null
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1000500, 4.5)
        assertNull(d)
        assertEquals(3, temporaryBasalStorage.store.size)
        // With more than one minute off should return null
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1070000, 4.0)
        assertNull(d)
        assertEquals(3, temporaryBasalStorage.store.size)
        // Use last, if bolus size is the same
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1070000, 5.0)
        assertEquals(5.0, d!!.rate, 0.01)
        assertEquals(2, temporaryBasalStorage.store.size)

    }
}