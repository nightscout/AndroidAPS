package app.aaps.implementation.iob

import app.aaps.core.data.iob.InMemoryGlucoseValue
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensDataStore
import app.aaps.core.interfaces.aps.GlucoseStatusAutoIsf
import app.aaps.plugins.aps.openAPS.DeltaCalculator
import app.aaps.plugins.aps.openAPSAutoISF.GlucoseStatusCalculatorAutoIsf
import app.aaps.plugins.aps.openAPSAutoISF.extensions.asRounded
import app.aaps.plugins.aps.openAPSAutoISF.extensions.log
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

/**
 * Created by mike on 26.03.2018.
 */
class GlucoseStatusTestAutoIsf : TestBaseWithProfile() {

    @Mock lateinit var autosensDataStore: AutosensDataStore

    @BeforeEach
    fun prepare() {
        whenever(dateUtil.now()).thenReturn(1514766900000L + T.mins(1).msecs())
        whenever(iobCobCalculator.ads).thenReturn(autosensDataStore)
    }

    @Test fun toStringShouldBeOverloaded() {
        val glucoseStatus = GlucoseStatusAutoIsf(glucose = 0.0, noise = 0.0, delta = 0.0, shortAvgDelta = 0.0, longAvgDelta = 0.0, date = 0)
        assertThat(glucoseStatus.log(decimalFormatter)).contains("Delta")
    }

    @Test fun roundTest() {
        val glucoseStatus = GlucoseStatusAutoIsf(glucose = 100.11111, noise = 0.0, delta = 0.0, shortAvgDelta = 0.0, longAvgDelta = 0.0, date = 0)
        assertThat(glucoseStatus.asRounded().glucose).isWithin(0.0001).of(100.1)
    }

    @Test fun calculateValidGlucoseStatusAutoIsf() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateValidBgData())
        val glucoseStatus = GlucoseStatusCalculatorAutoIsf(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator).getGlucoseStatusData(false)!!
        assertThat(glucoseStatus.glucose).isWithin(0.001).of(214.0)
        assertThat(glucoseStatus.delta).isWithin(0.001).of(-2.0)
        assertThat(glucoseStatus.shortAvgDelta).isWithin(0.001).of(-2.5) // -2 -2.5 -3 deltas are relative to current value
        assertThat(glucoseStatus.longAvgDelta).isWithin(0.001).of(-2.0) // -2 -2 -2 -2
        assertThat(glucoseStatus.date).isEqualTo(1514766900000L) // latest date

        assertThat(glucoseStatus.duraISFminutes).isEqualTo(35.0) // plateau size records in minutes
        assertThat(glucoseStatus.duraISFaverage).isWithin(0.1).of(221.5) // average during above time window
        assertThat(glucoseStatus.parabolaMinutes).isWithin(0.1).of(15.0) // parabola size records in minutes
        assertThat(glucoseStatus.deltaPl).isWithin(0.1).of(-2.0) // last delta
        assertThat(glucoseStatus.deltaPn).isWithin(0.1).of(-1.0) // next delta
        assertThat(glucoseStatus.bgAcceleration).isWithin(0.01).of(1.0) // glucose acceleration
        assertThat(glucoseStatus.a0).isWithin(0.1).of(214.0) //
        assertThat(glucoseStatus.a1).isWithin(0.0001).of(-1.5) //
        assertThat(glucoseStatus.a2).isWithin(0.0001).of(0.5) //
        assertThat(glucoseStatus.corrSqu).isWithin(0.001).of(1.0) // parabola fit quality
    }

    @Test fun oneRecordShouldProduceZeroDeltas() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateOneCurrentRecordBgData())
        val glucoseStatus = GlucoseStatusCalculatorAutoIsf(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator).getGlucoseStatusData(false)!!
        assertThat(glucoseStatus.glucose).isWithin(0.001).of(214.0)
        assertThat(glucoseStatus.delta).isWithin(0.001).of(0.0)
        assertThat(glucoseStatus.shortAvgDelta).isWithin(0.001).of(0.0) // -2 -2.5 -3 deltas are relative to current value
        assertThat(glucoseStatus.longAvgDelta).isWithin(0.001).of(0.0) // -2 -2 -2 -2
        assertThat(glucoseStatus.date).isEqualTo(1514766900000L) // latest date

        assertThat(glucoseStatus.duraISFminutes).isEqualTo(0.0) // size records in minutes
        assertThat(glucoseStatus.duraISFaverage).isWithin(0.01).of(214.0) // last value
        assertThat(glucoseStatus.parabolaMinutes).isWithin(0.001).of(0.0) // size records in minutes
        assertThat(glucoseStatus.bgAcceleration).isWithin(0.001).of(0.0) //
        assertThat(glucoseStatus.a0).isWithin(0.001).of(214.0) // last value
        assertThat(glucoseStatus.corrSqu).isWithin(0.001).of(0.0) //
    }

    @Test fun insufficientDataShouldReturnNull() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateInsufficientBgData())
        val glucoseStatus = GlucoseStatusCalculatorAutoIsf(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator).getGlucoseStatusData(false)
        assertThat(glucoseStatus).isNull()
    }

    @Test fun oldDataShouldReturnNull() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateOldBgData())
        val glucoseStatus = GlucoseStatusCalculatorAutoIsf(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator).getGlucoseStatusData(false)
        assertThat(glucoseStatus).isNull()
    }

    @Test fun returnOldDataIfAllowed() {
        whenever(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateOldBgData())
        val glucoseStatus = GlucoseStatusCalculatorAutoIsf(aapsLogger, iobCobCalculator, dateUtil, decimalFormatter, deltaCalculator).getGlucoseStatusData(true)
        assertThat(glucoseStatus).isNotNull()
    }

    @Test fun averageShouldNotFailOnEmptyArray() {
        assertThat(DeltaCalculator.average(ArrayList())).isWithin(0.001).of(0.0)
    }

    // [{"mgdl":214,"mills":1521895773113,"device":"xDrip-DexcomG5","direction":"Flat","filtered":191040,"unfiltered":205024,"noise":1,"rssi":100},{"mgdl":219,"mills":1521896073352,"device":"xDrip-DexcomG5","direction":"Flat","filtered":200160,"unfiltered":209760,"noise":1,"rssi":100},{"mgdl":222,"mills":1521896372890,"device":"xDrip-DexcomG5","direction":"Flat","filtered":207360,"unfiltered":212512,"noise":1,"rssi":100},{"mgdl":220,"mills":1521896673062,"device":"xDrip-DexcomG5","direction":"Flat","filtered":211488,"unfiltered":210688,"noise":1,"rssi":100},{"mgdl":193,"mills":1521896972933,"device":"xDrip-DexcomG5","direction":"Flat","filtered":212384,"unfiltered":208960,"noise":1,"rssi":100},{"mgdl":181,"mills":1521897273336,"device":"xDrip-DexcomG5","direction":"SingleDown","filtered":210592,"unfiltered":204320,"noise":1,"rssi":100},{"mgdl":176,"mills":1521897572875,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":206720,"unfiltered":197440,"noise":1,"rssi":100},{"mgdl":168,"mills":1521897872929,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":201024,"unfiltered":187904,"noise":1,"rssi":100},{"mgdl":161,"mills":1521898172814,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":193376,"unfiltered":178144,"noise":1,"rssi":100},{"mgdl":148,"mills":1521898472879,"device":"xDrip-DexcomG5","direction":"SingleDown","filtered":183264,"unfiltered":161216,"noise":1,"rssi":100},{"mgdl":139,"mills":1521898772862,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":170784,"unfiltered":148928,"noise":1,"rssi":100},{"mgdl":132,"mills":1521899072896,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":157248,"unfiltered":139552,"noise":1,"rssi":100},{"mgdl":125,"mills":1521899372834,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":144416,"unfiltered":129616.00000000001,"noise":1,"rssi":100},{"mgdl":128,"mills":1521899973456,"device":"xDrip-DexcomG5","direction":"Flat","filtered":130240.00000000001,"unfiltered":133536,"noise":1,"rssi":100},{"mgdl":132,"mills":1521900573287,"device":"xDrip-DexcomG5","direction":"Flat","filtered":133504,"unfiltered":138720,"noise":1,"rssi":100},{"mgdl":127,"mills":1521900873711,"device":"xDrip-DexcomG5","direction":"Flat","filtered":136480,"unfiltered":132992,"noise":1,"rssi":100},{"mgdl":127,"mills":1521901180151,"device":"xDrip-DexcomG5","direction":"Flat","filtered":136896,"unfiltered":132128,"noise":1,"rssi":100},{"mgdl":125,"mills":1521901473582,"device":"xDrip-DexcomG5","direction":"Flat","filtered":134624,"unfiltered":129696,"noise":1,"rssi":100},{"mgdl":120,"mills":1521901773597,"device":"xDrip-DexcomG5","direction":"Flat","filtered":130704.00000000001,"unfiltered":123376,"noise":1,"rssi":100},{"mgdl":116,"mills":1521902075855,"device":"xDrip-DexcomG5","direction":"Flat","filtered":126272,"unfiltered":118448,"noise":1,"rssi":100}]
    private fun generateValidBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 216.0, timestamp = 1514766600000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 219.0, timestamp = 1514766300000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 223.0, timestamp = 1514766000000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 222.0, timestamp = 1514765700000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 224.0, timestamp = 1514765400000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 226.0, timestamp = 1514765100000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        list.add(InMemoryGlucoseValue(value = 228.0, timestamp = 1514764800000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        return list
    }

    private fun generateInsufficientBgData(): MutableList<InMemoryGlucoseValue> {
        return ArrayList()
    }

    private fun generateOldBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 228.0, timestamp = 1514764800000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        return list
    }

    private fun generateOneCurrentRecordBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = TrendArrow.FLAT, sourceSensor = SourceSensor.UNKNOWN))
        return list
    }
}
