package app.aaps.implementation.pump

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
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
        assertThat(temporaryBasalStorage.store).isEmpty()
        temporaryBasalStorage.add(info1)
        assertThat(temporaryBasalStorage.store).hasSize(1)
    }

    @Test
    fun findTemporaryBasal() {

        // Look for exact bolus
        setUp()
        var d = temporaryBasalStorage.findTemporaryBasal(1000000, 4.0)
        assertThat(d!!.rate).isWithin(0.01).of(4.0)
        assertThat(temporaryBasalStorage.store).hasSize(2)
        // Look for exact bolus
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1000000, 3.0)
        assertThat(d!!.rate).isWithin(0.01).of(3.0)
        assertThat(temporaryBasalStorage.store).hasSize(2)
        // With less rate (bolus not delivered completely). Should return first one matching date
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1000500, 2.0)
        assertThat(d!!.rate).isWithin(0.01).of(3.0)
        assertThat(temporaryBasalStorage.store).hasSize(2)
        // With less rate (bolus not delivered completely). Should return first one matching date
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1000500, 3.5)
        assertThat(d!!.rate).isWithin(0.01).of(4.0)
        assertThat(temporaryBasalStorage.store).hasSize(2)
        // With more rate should return null
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1000500, 4.5)
        assertThat(d).isNull()
        assertThat(temporaryBasalStorage.store).hasSize(3)
        // With more than one minute off should return null
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1070000, 4.0)
        assertThat(d).isNull()
        assertThat(temporaryBasalStorage.store).hasSize(3)
        // Use last, if bolus size is the same
        setUp()
        d = temporaryBasalStorage.findTemporaryBasal(1070000, 5.0)
        assertThat(d!!.rate).isWithin(0.01).of(5.0)
        assertThat(temporaryBasalStorage.store).hasSize(2)

    }
}
