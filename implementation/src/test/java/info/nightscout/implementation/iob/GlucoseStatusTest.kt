package info.nightscout.implementation.iob

import info.nightscout.androidaps.TestBase
import info.nightscout.core.iob.asRounded
import info.nightscout.core.iob.log
import info.nightscout.database.entities.GlucoseValue
import info.nightscout.interfaces.aps.AutosensDataStore
import info.nightscout.interfaces.iob.GlucoseStatus
import info.nightscout.interfaces.iob.InMemoryGlucoseValue
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

/**
 * Created by mike on 26.03.2018.
 */
class GlucoseStatusTest : TestBase() {

    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var iobCobCalculatorPlugin: IobCobCalculator
    @Mock lateinit var autosensDataStore: AutosensDataStore

    @BeforeEach
    fun prepare() {
        Mockito.`when`(iobCobCalculatorPlugin.ads).thenReturn(autosensDataStore)
    }

    @Test fun toStringShouldBeOverloaded() {
        val glucoseStatus = GlucoseStatus(glucose = 0.0, noise = 0.0, delta = 0.0, shortAvgDelta = 0.0, longAvgDelta = 0.0, date = 0)
        Assertions.assertEquals(true, glucoseStatus.log().contains("Delta"))
    }

    @Test fun roundTest() {
        val glucoseStatus = GlucoseStatus(glucose = 100.11111, noise = 0.0, delta = 0.0, shortAvgDelta = 0.0, longAvgDelta = 0.0, date = 0)
        Assertions.assertEquals(100.1, glucoseStatus.asRounded().glucose, 0.0001)
    }

    @Test fun calculateValidGlucoseStatus() {
        Mockito.`when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateValidBgData())
        val glucoseStatus = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculatorPlugin, dateUtil).glucoseStatusData!!
        Assertions.assertEquals(214.0, glucoseStatus.glucose, 0.001)
        Assertions.assertEquals(-2.0, glucoseStatus.delta, 0.001)
        Assertions.assertEquals(-2.5, glucoseStatus.shortAvgDelta, 0.001) // -2 -2.5 -3 deltas are relative to current value
        Assertions.assertEquals(-2.0, glucoseStatus.longAvgDelta, 0.001) // -2 -2 -2 -2
        Assertions.assertEquals(1514766900000L, glucoseStatus.date) // latest date
    }
/*
    Not testing anymore, not valid for bucketed data

    @Test fun calculateMostRecentGlucoseStatus() {
        Mockito.`when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateMostRecentBgData())
        val glucoseStatus: GlucoseStatus = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculatorPlugin, dateUtil).glucoseStatusData!!
        Assertions.assertEquals(215.0, glucoseStatus.glucose, 0.001) // (214+216) / 2
        Assertions.assertEquals(-1.0, glucoseStatus.delta, 0.001)
        Assertions.assertEquals(-1.0, glucoseStatus.shortAvgDelta, 0.001)
        Assertions.assertEquals(0.0, glucoseStatus.longAvgDelta, 0.001)
        Assertions.assertEquals(1514766900000L, glucoseStatus.date) // latest date, even when averaging
    }

    private fun generateMostRecentBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 216.0, timestamp = 1514766800000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 216.0, timestamp = 1514766600000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        return list
    }
*/

    @Test fun oneRecordShouldProduceZeroDeltas() {
        Mockito.`when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateOneCurrentRecordBgData())
        val glucoseStatus: GlucoseStatus = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculatorPlugin, dateUtil).glucoseStatusData!!
        Assertions.assertEquals(214.0, glucoseStatus.glucose, 0.001)
        Assertions.assertEquals(0.0, glucoseStatus.delta, 0.001)
        Assertions.assertEquals(0.0, glucoseStatus.shortAvgDelta, 0.001) // -2 -2.5 -3 deltas are relative to current value
        Assertions.assertEquals(0.0, glucoseStatus.longAvgDelta, 0.001) // -2 -2 -2 -2
        Assertions.assertEquals(1514766900000L, glucoseStatus.date) // latest date
    }

    @Test fun insufficientDataShouldReturnNull() {
        Mockito.`when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateInsufficientBgData())
        val glucoseStatus: GlucoseStatus? = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculatorPlugin, dateUtil).glucoseStatusData
        Assertions.assertEquals(null, glucoseStatus)
    }

    @Test fun oldDataShouldReturnNull() {
        Mockito.`when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateOldBgData())
        val glucoseStatus: GlucoseStatus? = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculatorPlugin, dateUtil).glucoseStatusData
        Assertions.assertEquals(null, glucoseStatus)
    }

    @Test fun returnOldDataIfAllowed() {
        Mockito.`when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateOldBgData())
        val glucoseStatus: GlucoseStatus? = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculatorPlugin, dateUtil).getGlucoseStatusData(true)
        Assertions.assertNotEquals(null, glucoseStatus)
    }

    @Test fun averageShouldNotFailOnEmptyArray() {
        Assertions.assertEquals(0.0, GlucoseStatusProviderImpl.average(ArrayList()), 0.001)
    }

/*
    Not testing anymore, not valid for bucketed data

    @Test fun calculateGlucoseStatusForLibreTestBgData() {
        Mockito.`when`(autosensDataStore.getBucketedDataTableCopy()).thenReturn(generateLibreTestData())
        val glucoseStatus: GlucoseStatus = GlucoseStatusProviderImpl(aapsLogger, iobCobCalculatorPlugin, dateUtil).glucoseStatusData!!
        Assertions.assertEquals(100.0, glucoseStatus.glucose, 0.001) //
        Assertions.assertEquals(-10.0, glucoseStatus.delta, 0.001)
        Assertions.assertEquals(-10.0, glucoseStatus.shortAvgDelta, 0.001)
        Assertions.assertEquals(-10.0, glucoseStatus.longAvgDelta, 0.001)
        Assertions.assertEquals(1514766900000L, glucoseStatus.date) // latest date
    }

    private fun generateLibreTestData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        val endTime = 1514766900000L
        val latestReading = 100.0
        // Now
        list.add(InMemoryGlucoseValue(value = latestReading, timestamp = endTime, trendArrow = GlucoseValue.TrendArrow.FLAT))
        // One minute ago
        list.add(InMemoryGlucoseValue(value = latestReading, timestamp = endTime - 1000 * 60 * 1, trendArrow = GlucoseValue.TrendArrow.FLAT))
        // Two minutes ago
        list.add(InMemoryGlucoseValue(value = latestReading, timestamp = endTime - 1000 * 60 * 2, trendArrow = GlucoseValue.TrendArrow.FLAT))
        // Three minutes and beyond at constant rate
        for (i in 3..49)
            list.add(InMemoryGlucoseValue(value = latestReading + i * 2, timestamp = endTime - 1000 * 60 * i, trendArrow = GlucoseValue.TrendArrow.FLAT))
        return list
    }
*/

    @BeforeEach
    fun initMocking() {
        Mockito.`when`(dateUtil.now()).thenReturn(1514766900000L + T.mins(1).msecs())
        Mockito.`when`(iobCobCalculatorPlugin.ads).thenReturn(autosensDataStore)
    }

    // [{"mgdl":214,"mills":1521895773113,"device":"xDrip-DexcomG5","direction":"Flat","filtered":191040,"unfiltered":205024,"noise":1,"rssi":100},{"mgdl":219,"mills":1521896073352,"device":"xDrip-DexcomG5","direction":"Flat","filtered":200160,"unfiltered":209760,"noise":1,"rssi":100},{"mgdl":222,"mills":1521896372890,"device":"xDrip-DexcomG5","direction":"Flat","filtered":207360,"unfiltered":212512,"noise":1,"rssi":100},{"mgdl":220,"mills":1521896673062,"device":"xDrip-DexcomG5","direction":"Flat","filtered":211488,"unfiltered":210688,"noise":1,"rssi":100},{"mgdl":193,"mills":1521896972933,"device":"xDrip-DexcomG5","direction":"Flat","filtered":212384,"unfiltered":208960,"noise":1,"rssi":100},{"mgdl":181,"mills":1521897273336,"device":"xDrip-DexcomG5","direction":"SingleDown","filtered":210592,"unfiltered":204320,"noise":1,"rssi":100},{"mgdl":176,"mills":1521897572875,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":206720,"unfiltered":197440,"noise":1,"rssi":100},{"mgdl":168,"mills":1521897872929,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":201024,"unfiltered":187904,"noise":1,"rssi":100},{"mgdl":161,"mills":1521898172814,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":193376,"unfiltered":178144,"noise":1,"rssi":100},{"mgdl":148,"mills":1521898472879,"device":"xDrip-DexcomG5","direction":"SingleDown","filtered":183264,"unfiltered":161216,"noise":1,"rssi":100},{"mgdl":139,"mills":1521898772862,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":170784,"unfiltered":148928,"noise":1,"rssi":100},{"mgdl":132,"mills":1521899072896,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":157248,"unfiltered":139552,"noise":1,"rssi":100},{"mgdl":125,"mills":1521899372834,"device":"xDrip-DexcomG5","direction":"FortyFiveDown","filtered":144416,"unfiltered":129616.00000000001,"noise":1,"rssi":100},{"mgdl":128,"mills":1521899973456,"device":"xDrip-DexcomG5","direction":"Flat","filtered":130240.00000000001,"unfiltered":133536,"noise":1,"rssi":100},{"mgdl":132,"mills":1521900573287,"device":"xDrip-DexcomG5","direction":"Flat","filtered":133504,"unfiltered":138720,"noise":1,"rssi":100},{"mgdl":127,"mills":1521900873711,"device":"xDrip-DexcomG5","direction":"Flat","filtered":136480,"unfiltered":132992,"noise":1,"rssi":100},{"mgdl":127,"mills":1521901180151,"device":"xDrip-DexcomG5","direction":"Flat","filtered":136896,"unfiltered":132128,"noise":1,"rssi":100},{"mgdl":125,"mills":1521901473582,"device":"xDrip-DexcomG5","direction":"Flat","filtered":134624,"unfiltered":129696,"noise":1,"rssi":100},{"mgdl":120,"mills":1521901773597,"device":"xDrip-DexcomG5","direction":"Flat","filtered":130704.00000000001,"unfiltered":123376,"noise":1,"rssi":100},{"mgdl":116,"mills":1521902075855,"device":"xDrip-DexcomG5","direction":"Flat","filtered":126272,"unfiltered":118448,"noise":1,"rssi":100}]
    private fun generateValidBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 216.0, timestamp = 1514766600000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 219.0, timestamp = 1514766300000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 223.0, timestamp = 1514766000000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 222.0, timestamp = 1514765700000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 224.0, timestamp = 1514765400000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 226.0, timestamp = 1514765100000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        list.add(InMemoryGlucoseValue(value = 228.0, timestamp = 1514764800000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        return list
    }

    private fun generateInsufficientBgData(): MutableList<InMemoryGlucoseValue> {
        return ArrayList()
    }

    private fun generateOldBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 228.0, timestamp = 1514764800000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        return list
    }

    private fun generateOneCurrentRecordBgData(): MutableList<InMemoryGlucoseValue> {
        val list: MutableList<InMemoryGlucoseValue> = ArrayList()
        list.add(InMemoryGlucoseValue(value = 214.0, timestamp = 1514766900000, trendArrow = GlucoseValue.TrendArrow.FLAT))
        return list
    }
}