package app.aaps.implementation.overview

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class LastBgDataImplTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var autosensDataStore: AutosensDataStore

    private lateinit var sut: LastBgDataImpl
    private val time = 1000000L

    private val glucoseValue =
        GV(raw = 200.0, noise = 0.0, value = 200.0, timestamp = time, sourceSensor = SourceSensor.UNKNOWN, trendArrow = TrendArrow.FLAT)

    @BeforeEach
    fun setup() {
        Mockito.`when`(iobCobCalculator.ads).thenReturn(autosensDataStore)
        sut = LastBgDataImpl(rh, dateUtil, persistenceLayer, profileFunction, preferences, iobCobCalculator)
        Mockito.`when`(preferences.get(UnitDoubleKey.OverviewLowMark)).thenReturn(80.0)
        Mockito.`when`(preferences.get(UnitDoubleKey.OverviewHighMark)).thenReturn(180.0)
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun lastBg() {
        val bucketedData: MutableList<InMemoryGlucoseValue> = mutableListOf()
        bucketedData.add(InMemoryGlucoseValue(time, 70.0, sourceSensor = SourceSensor.UNKNOWN))
        // no data
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(persistenceLayer.getLastGlucoseValue()).thenReturn(null)
        assertThat(sut.lastBg()).isNull()
        assertThat(sut.isLow()).isFalse()
        assertThat(sut.isHigh()).isFalse()

        // no bucketed but in db
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(persistenceLayer.getLastGlucoseValue()).thenReturn(glucoseValue)
        assertThat(sut.lastBg()?.value).isEqualTo(200.0)
        assertThat(sut.isLow()).isFalse()
        assertThat(sut.isHigh()).isTrue()

        // in bucketed
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(bucketedData)
        Mockito.`when`(persistenceLayer.getLastGlucoseValue()).thenReturn(glucoseValue)
        assertThat(sut.lastBg()?.value).isEqualTo(70.0)
        assertThat(sut.isLow()).isTrue()
        assertThat(sut.isHigh()).isFalse()
    }

    @Test
    fun isActualBg() {
        // no bucketed but in db
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(persistenceLayer.getLastGlucoseValue()).thenReturn(glucoseValue)
        Mockito.`when`(dateUtil.now()).thenReturn(time + T.mins(1).msecs())
        assertThat(sut.isActualBg()).isTrue()
        Mockito.`when`(dateUtil.now()).thenReturn(time + T.mins(9).msecs() + 1)
        assertThat(sut.isActualBg()).isFalse()

        // no data
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(persistenceLayer.getLastGlucoseValue()).thenReturn(null)
        assertThat(sut.isActualBg()).isFalse()
    }
}
