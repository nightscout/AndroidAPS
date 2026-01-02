package app.aaps.plugins.main.iob

import androidx.collection.LongSparseArray
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.implementation.iob.AutosensDataObject
import app.aaps.plugins.main.iob.iobCobCalculator.data.AutosensDataStoreObject
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.whenever

class AutosensDataStoreTest : TestBaseWithProfile() {

    private val autosensDataStore = AutosensDataStoreObject()

    @BeforeEach
    fun mock() {
        whenever(iobCobCalculator.ads).thenReturn(autosensDataStore)
    }

    @Test
    fun isAbout5minDataTest() {
        val bgReadingList: MutableList<GV> = ArrayList()

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()

        // too much shifted data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(9).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()

        // too much shifted and missing data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(9).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()

        // too much shifted and missing data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(83).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(78).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(73).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(68).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(63).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(58).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(53).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(43).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(38).plus(T.secs(40)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(33).plus(T.secs(1)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(28).plus(T.secs(0)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(23).plus(T.secs(0)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(16).plus(T.secs(36)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()

        // slightly shifted data should return true
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()

        // slightly shifted and missing data should return true
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
    }

    @Test
    fun createBucketedData5minTest1() {
        val bgReadingList: MutableList<GV> = ArrayList()

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(bgReadingList[0].timestamp)
        assertThat(autosensDataStore.bucketedData!![3].timestamp).isEqualTo(bgReadingList[3].timestamp)
        assertThat(autosensDataStore.bucketedData!!).hasSize(bgReadingList.size)

        // Missing value should be replaced
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(bgReadingList[0].timestamp)
        assertThat(autosensDataStore.bucketedData!![3].timestamp).isEqualTo(bgReadingList[2].timestamp)
        assertThat(autosensDataStore.bucketedData!!).hasSize(bgReadingList.size + 1)

        // drift should be cleared
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs() + T.secs(10).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs() + T.secs(10).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs() + T.secs(10).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(0).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(T.mins(20).msecs())
        assertThat(autosensDataStore.bucketedData!![1].timestamp).isEqualTo(T.mins(15).msecs())
        assertThat(autosensDataStore.bucketedData!![2].timestamp).isEqualTo(T.mins(10).msecs())
        assertThat(autosensDataStore.bucketedData!![3].timestamp).isEqualTo(T.mins(5).msecs())
        assertThat(autosensDataStore.bucketedData!!).hasSize(bgReadingList.size)

        // bucketed data should return null if not enough bg data
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(30).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData).isNull()

        // data should be reconstructed
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(50).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(45).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(T.mins(50).msecs())
        assertThat(autosensDataStore.bucketedData!![6].timestamp).isEqualTo(T.mins(20).msecs())
        assertThat(autosensDataStore.bucketedData!!).hasSize(7)
        assertThat(autosensDataStore.bucketedData!![0].value).isWithin(1.0).of(100.0)
        assertThat(autosensDataStore.bucketedData!![1].value).isWithin(1.0).of(90.0)
        assertThat(autosensDataStore.bucketedData!![5].value).isWithin(1.0).of(50.0)
        assertThat(autosensDataStore.bucketedData!![6].value).isWithin(1.0).of(40.0)

        // non 5min data should be reconstructed
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(50).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 96.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(T.mins(50).msecs())
        assertThat(autosensDataStore.bucketedData!![6].timestamp).isEqualTo(T.mins(20).msecs())
        assertThat(autosensDataStore.bucketedData!!).hasSize(7)
        assertThat(autosensDataStore.bucketedData!![0].value).isWithin(1.0).of(100.0)
        assertThat(autosensDataStore.bucketedData!![1].value).isWithin(1.0).of(90.0)
        assertThat(autosensDataStore.bucketedData!![5].value).isWithin(1.0).of(50.0)
        assertThat(autosensDataStore.bucketedData!![6].value).isWithin(1.0).of(40.0)
    }

    @Test
    fun createBucketedData5minTest2() {
        val bgReadingList: MutableList<GV> = ArrayList()

        //bucketed data should be null if no bg data available
        autosensDataStore.bgReadings = ArrayList()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData).isNull()

        // real data gap test
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:34:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:14:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:09:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:04:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:59:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:54:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:49:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:44:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:39:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:34:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:29:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:24:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:19:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:14:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:09:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:04:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T11:59:55Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )

        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:29:57Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:24:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:19:57Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:14:57Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:10:03Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:04:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:59:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:54:56Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:50:03Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:44:57Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        autosensDataStore.referenceTime = -1
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(dateUtil.fromISODateString("2018-09-05T13:34:57Z"))
        assertThat(autosensDataStore.bucketedData!!.last().timestamp).isEqualTo(dateUtil.fromISODateString("2018-09-05T03:44:57Z"))

        // 5min 4sec data
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:33:40Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:28:36Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:23:32Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:18:28Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:13:24Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:08:19Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:03:16Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:58:11Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:53:07Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:48:03Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:42:58Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:37:54Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:32:51Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:27:46Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:22:42Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:17:38Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:12:33Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:07:29Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:02:26Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T04:57:21Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T04:52:17Z"),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()
    }

    @Test
    fun createBucketedData5minTest3() {
        val bgReadingList: MutableList<GV> = ArrayList()

        // non 5min data not aligned to referenceTime should be recalculated to referenceTime
        autosensDataStore.referenceTime = T.mins(5).msecs()
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(T.mins(45).msecs())
        assertThat(autosensDataStore.bucketedData!![2].timestamp).isEqualTo(T.mins(35).msecs())
        assertThat(autosensDataStore.bucketedData!![5].timestamp).isEqualTo(T.mins(20).msecs())
        assertThat(autosensDataStore.bucketedData!!).hasSize(6)
        assertThat(autosensDataStore.bucketedData!![0].value).isWithin(1.0).of(99.0) // Recalculated data to 45min
        assertThat(autosensDataStore.bucketedData!![1].value).isWithin(1.0).of(90.0) // Recalculated data to 40min
        assertThat(autosensDataStore.bucketedData!![3].value).isWithin(1.0).of(67.0) // Recalculated data to 30min
        assertThat(autosensDataStore.bucketedData!![5].value).isWithin(1.0).of(45.0) // Recalculated data to 20min

        // non 5min data not aligned to referenceTime should be recalculated to referenceTime
        autosensDataStore.referenceTime = T.mins(5).msecs()
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(46).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(T.mins(45).msecs())
        assertThat(autosensDataStore.bucketedData!![2].timestamp).isEqualTo(T.mins(35).msecs())
        assertThat(autosensDataStore.bucketedData!![5].timestamp).isEqualTo(T.mins(20).msecs())
        assertThat(autosensDataStore.bucketedData!!).hasSize(6)
        assertThat(autosensDataStore.bucketedData!![0].value).isWithin(1.0).of(99.0) // Recalculated data to 45min
        assertThat(autosensDataStore.bucketedData!![1].value).isWithin(1.0).of(90.0) // Recalculated data to 40min
        assertThat(autosensDataStore.bucketedData!![3].value).isWithin(1.0).of(67.0) // Recalculated data to 30min
        assertThat(autosensDataStore.bucketedData!![5].value).isWithin(1.0).of(45.0) // Recalculated data to 20min

        // non 5min data without referenceTime set, should align the data to the time of the last reading
        autosensDataStore.referenceTime = -1
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData!![0].timestamp).isEqualTo(T.mins(48).msecs())
        assertThat(autosensDataStore.bucketedData!![1].timestamp).isEqualTo(T.mins(43).msecs())
        assertThat(autosensDataStore.bucketedData!![3].timestamp).isEqualTo(T.mins(33).msecs())
        assertThat(autosensDataStore.bucketedData!![6].timestamp).isEqualTo(T.mins(18).msecs())
        assertThat(autosensDataStore.bucketedData!!).hasSize(7)
        assertThat(autosensDataStore.bucketedData!![0].value).isWithin(1.0).of(100.0) // Recalculated data to 48min
        assertThat(autosensDataStore.bucketedData!![1].value).isWithin(1.0).of(98.0) // Recalculated data to 43min
        assertThat(autosensDataStore.bucketedData!![3].value).isWithin(1.0).of(74.0) // Recalculated data to 33min
        assertThat(autosensDataStore.bucketedData!![6].value).isWithin(1.0).of(40.0) // Recalculated data to 18min
    }

    @Test
    fun bgReadingsTest() {
        val bgReadingList: List<GV> = ArrayList()
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.bgReadings).isEmpty()
    }

    @Test
    fun roundUpTimeTest() {
        assertThat(autosensDataStore.roundUpTime(T.secs(155).msecs())).isEqualTo(T.mins(3).msecs())
    }

    @Test
    fun findNewerTest() {
        val bgReadingList: MutableList<GV> = ArrayList()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.findNewer(T.mins(8).msecs())!!.timestamp).isEqualTo(T.mins(10).msecs())
        assertThat(autosensDataStore.findNewer(T.mins(5).msecs())!!.timestamp).isEqualTo(T.mins(5).msecs())
        assertThat(autosensDataStore.findNewer(T.mins(10).msecs())!!.timestamp).isEqualTo(T.mins(10).msecs())
        assertThat(autosensDataStore.findNewer(T.mins(20).msecs())!!.timestamp).isEqualTo(T.mins(20).msecs())
        assertThat(autosensDataStore.findNewer(T.mins(22).msecs())).isNull()
    }

    @Test
    fun findOlderTest() {
        val bgReadingList: MutableList<GV> = ArrayList()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.findOlder(T.mins(8).msecs())!!.timestamp).isEqualTo(T.mins(5).msecs())
        assertThat(autosensDataStore.findOlder(T.mins(5).msecs())!!.timestamp).isEqualTo(T.mins(5).msecs())
        assertThat(autosensDataStore.findOlder(T.mins(10).msecs())!!.timestamp).isEqualTo(T.mins(10).msecs())
        assertThat(autosensDataStore.findOlder(T.mins(20).msecs())!!.timestamp).isEqualTo(T.mins(20).msecs())
        assertThat(autosensDataStore.findOlder(T.mins(4).msecs())).isNull()
    }

    @Test
    fun findPreviousTimeFromBucketedDataTest() {
        val bgReadingList: MutableList<GV> = ArrayList()
        autosensDataStore.bgReadings = bgReadingList
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.findPreviousTimeFromBucketedData(1000)).isNull()

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GV(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = SourceSensor.UNKNOWN,
                trendArrow = TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.findPreviousTimeFromBucketedData(T.mins(4).msecs())).isNull()
        assertThat(autosensDataStore.findPreviousTimeFromBucketedData(T.mins(6).msecs())).isEqualTo(T.mins(5).msecs())
        assertThat(autosensDataStore.findPreviousTimeFromBucketedData(T.mins(20).msecs())).isEqualTo(T.mins(20).msecs())
        assertThat(autosensDataStore.findPreviousTimeFromBucketedData(T.mins(25).msecs())).isEqualTo(T.mins(20).msecs())
    }

    @Test
    fun threeMinDataWithRecalculation() {
        val list = mutableListOf<GV>()
        list.add(GV(timestamp = now, value = 198.0, raw = 0.0, trendArrow = TrendArrow.FLAT, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(3).msecs(), value = 197.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(6).msecs(), value = 196.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(9).msecs(), value = 195.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(12).msecs(), value = 194.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(15).msecs(), value = 193.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(18).msecs(), value = 192.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(21).msecs(), value = 191.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(24).msecs(), value = 190.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(27).msecs(), value = 189.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        list.add(GV(timestamp = now - T.mins(30).msecs(), value = 188.0, raw = 0.0, trendArrow = TrendArrow.NONE, noise = 0.0, sourceSensor = SourceSensor.UNKNOWN))
        autosensDataStore.bgReadings = list
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        val glucoseStatus = glucoseStatusCalculatorSMB.glucoseStatusData!!
        assertThat(glucoseStatus.delta).isWithin(0.01).of(2.0)
        assertThat(glucoseStatus.shortAvgDelta).isWithin(0.01).of(1.72)
        assertThat(glucoseStatus.longAvgDelta).isWithin(0.01).of(1.67)
    }

    @Test
    fun getLastAutosensDataTest() {
        val ads = AutosensDataStoreObject()
        ads.storedLastAutosensResult = AutosensDataObject(aapsLogger, preferences, dateUtil).apply { time = now - 10 }
        // empty array, return last stored
        ads.autosensDataTable = LongSparseArray<AutosensData>()
        assertThat(ads.getLastAutosensData("test", aapsLogger, dateUtil)?.time).isEqualTo(now - 10)

        // data is there, return it
        ads.autosensDataTable.append(now - 1, AutosensDataObject(aapsLogger, preferences, dateUtil).apply { time = now - 1 })
        assertThat(ads.getLastAutosensData("test", aapsLogger, dateUtil)?.time).isEqualTo(now - 1)
        // and latest value should be saved
        assertThat(ads.storedLastAutosensResult?.time).isEqualTo(now - 1)

        // data is old, return last stored
        ads.storedLastAutosensResult = AutosensDataObject(aapsLogger, preferences, dateUtil).apply { time = now - 1 }
        ads.autosensDataTable = LongSparseArray<AutosensData>()
        ads.autosensDataTable.append(now - T.mins(20).msecs(), AutosensDataObject(aapsLogger, preferences, dateUtil).apply { time = now - T.mins(20).msecs() })
        assertThat(ads.getLastAutosensData("test", aapsLogger, dateUtil)?.time).isEqualTo(now - 1)
    }
}
