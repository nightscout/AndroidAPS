package info.nightscout.implementation.overview

import com.google.common.truth.Truth.assertThat
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.profile.DefaultValueHelper
import info.nightscout.shared.utils.T
import info.nightscout.sharedtests.TestBaseWithProfile
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class OverviewDataImplTest : TestBaseWithProfile() {

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var autosensDataStore: AutosensDataStore

    private lateinit var sut: OverviewDataImpl
    private val time = 1000000L

    private val glucoseValue =
        GlucoseValue(raw = 200.0, noise = 0.0, value = 200.0, timestamp = time, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN, trendArrow = GlucoseValue.TrendArrow.FLAT)

    @BeforeEach
    fun setup() {
        sut = OverviewDataImpl(aapsLogger, rh, dateUtil, sp, activePlugin, defaultValueHelper, profileFunction, repository, decimalFormatter)
        Mockito.`when`(defaultValueHelper.determineLowLine()).thenReturn(80.0)
        Mockito.`when`(defaultValueHelper.determineHighLine()).thenReturn(180.0)
        Mockito.`when`(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    @Test
    fun lastBg() {
        val bucketedData: MutableList<InMemoryGlucoseValue> = mutableListOf()
        bucketedData.add(InMemoryGlucoseValue(time, 70.0, sourceSensor = GlucoseValue.SourceSensor.UNKNOWN))
        // no data
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Absent()))
        assertThat(sut.lastBg(autosensDataStore)).isNull()
        assertThat(sut.isLow(autosensDataStore)).isFalse()
        assertThat(sut.isHigh(autosensDataStore)).isFalse()

        // no bucketed but in db
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Existing(glucoseValue)))
        assertThat(sut.lastBg(autosensDataStore)?.value).isEqualTo(200.0)
        assertThat(sut.isLow(autosensDataStore)).isFalse()
        assertThat(sut.isHigh(autosensDataStore)).isTrue()

        // in bucketed
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(bucketedData)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Existing(glucoseValue)))
        assertThat(sut.lastBg(autosensDataStore)?.value).isEqualTo(70.0)
        assertThat(sut.isLow(autosensDataStore)).isTrue()
        assertThat(sut.isHigh(autosensDataStore)).isFalse()
    }

    @Test
    fun isActualBg() {
        // no bucketed but in db
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Existing(glucoseValue)))
        Mockito.`when`(dateUtil.now()).thenReturn(time + T.mins(1).msecs())
        assertThat(sut.isActualBg(autosensDataStore)).isTrue()
        Mockito.`when`(dateUtil.now()).thenReturn(time + T.mins(9).msecs() + 1)
        assertThat(sut.isActualBg(autosensDataStore)).isFalse()

        // no data
        Mockito.`when`(autosensDataStore.bucketedData).thenReturn(null)
        Mockito.`when`(repository.getLastGlucoseValueWrapped()).thenReturn(Single.just(ValueWrapper.Absent()))
        assertThat(sut.isActualBg(autosensDataStore)).isFalse()
    }
}
