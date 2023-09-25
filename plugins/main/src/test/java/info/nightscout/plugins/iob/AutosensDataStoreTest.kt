package info.nightscout.plugins.iob

import android.content.Context
import androidx.collection.LongSparseArray
import app.aaps.core.interfaces.aps.AutosensData
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.database.entities.GlucoseValue
import app.aaps.shared.impl.utils.DateUtilImpl
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.iob.iobCobCalculator.data.AutosensDataObject
import info.nightscout.plugins.iob.iobCobCalculator.data.AutosensDataStoreObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito

class AutosensDataStoreTest : TestBase() {

    @Mock lateinit var context: Context
    @Mock lateinit var sp: SP
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var dateUtilMocked: DateUtil

    private lateinit var dateUtil: DateUtil

    private val autosensDataStore = AutosensDataStoreObject()

    private val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is AutosensDataObject) {
                it.aapsLogger = aapsLogger
                it.sp = sp
                it.rh = rh
                it.profileFunction = profileFunction
                it.dateUtil = dateUtilMocked
            }
        }
    }

    @BeforeEach
    fun mock() {
        dateUtil = DateUtilImpl(context)
    }

    @Test
    fun isAbout5minDataTest() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()

        // too much shifted data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(9).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()

        // too much shifted and missing data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(9).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()

        // too much shifted and missing data should return false
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(83).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(78).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(73).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(68).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(63).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(58).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(53).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(43).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(38).plus(T.secs(40)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(33).plus(T.secs(1)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(28).plus(T.secs(0)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(23).plus(T.secs(0)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(16).plus(T.secs(36)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()

        // slightly shifted data should return true
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()

        // slightly shifted and missing data should return true
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
    }

    @Test
    fun createBucketedData5minTest1() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).plus(T.secs(10)).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs() + T.secs(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs() + T.secs(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs() + T.secs(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(0).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(30).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isTrue()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData).isNull()

        // data should be reconstructed
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(50).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(45).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(50).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 96.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()

        //bucketed data should be null if no bg data available
        autosensDataStore.bgReadings = ArrayList()
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.bucketedData).isNull()

        // real data gap test
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:34:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:14:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:09:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T13:04:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:59:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:54:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:49:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:44:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:39:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:34:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:29:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:24:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:19:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:14:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:09:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T12:04:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T11:59:55Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )

        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:29:57Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:24:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:19:57Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:14:57Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:10:03Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T04:04:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:59:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:54:56Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:50:03Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-09-05T03:44:57Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:33:40Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:28:36Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:23:32Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:18:28Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:13:24Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:08:19Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T06:03:16Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:58:11Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:53:07Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:48:03Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:42:58Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:37:54Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:32:51Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:27:46Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:22:42Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:17:38Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:12:33Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:07:29Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T05:02:26Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T04:57:21Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = dateUtil.fromISODateString("2018-10-05T04:52:17Z"),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.isAbout5minData(aapsLogger)).isFalse()
    }

    @Test
    fun createBucketedData5minTest3() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()

        // non 5min data not aligned to referenceTime should be recalculated to referenceTime
        autosensDataStore.referenceTime = T.mins(5).msecs()
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(46).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(48).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 98.0,
                timestamp = T.mins(42).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 90.0,
                timestamp = T.mins(40).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 40.0,
                timestamp = T.mins(18).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
        val bgReadingList: List<GlucoseValue> = ArrayList()
        autosensDataStore.bgReadings = bgReadingList
        assertThat(autosensDataStore.bgReadings).isEmpty()
    }

    @Test
    fun roundUpTimeTest() {
        assertThat(autosensDataStore.roundUpTime(T.secs(155).msecs())).isEqualTo(T.mins(3).msecs())
    }

    @Test
    fun findNewerTest() {
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
        val bgReadingList: MutableList<GlucoseValue> = ArrayList()
        autosensDataStore.bgReadings = bgReadingList
        autosensDataStore.createBucketedData(aapsLogger, dateUtil)
        assertThat(autosensDataStore.findPreviousTimeFromBucketedData(1000)).isNull()

        // Super data should not be touched
        bgReadingList.clear()
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(20).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(15).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(10).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
            )
        )
        bgReadingList.add(
            GlucoseValue(
                raw = 0.0,
                noise = 0.0,
                value = 100.0,
                timestamp = T.mins(5).msecs(),
                sourceSensor = GlucoseValue.SourceSensor.UNKNOWN,
                trendArrow = GlucoseValue.TrendArrow.FLAT
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
    fun getLastAutosensDataTest() {
        val now = 10000000L
        Mockito.`when`(dateUtilMocked.now()).thenReturn(now)
        val ads = AutosensDataStoreObject()
        ads.storedLastAutosensResult = AutosensDataObject(injector).apply { time = now - 10 }
        // empty array, return last stored
        ads.autosensDataTable = LongSparseArray<AutosensData>()
        assertThat(ads.getLastAutosensData("test", aapsLogger, dateUtilMocked)?.time).isEqualTo(now - 10)

        // data is there, return it
        ads.autosensDataTable.append(now - 1, AutosensDataObject(injector).apply { time = now - 1 })
        assertThat(ads.getLastAutosensData("test", aapsLogger, dateUtilMocked)?.time).isEqualTo(now - 1)
        // and latest value should be saved
        assertThat(ads.storedLastAutosensResult?.time).isEqualTo(now - 1)

        // data is old, return last stored
        ads.storedLastAutosensResult = AutosensDataObject(injector).apply { time = now - 1 }
        ads.autosensDataTable = LongSparseArray<AutosensData>()
        ads.autosensDataTable.append(now - T.mins(20).msecs(), AutosensDataObject(injector).apply { time = now - T.mins(20).msecs() })
        assertThat(ads.getLastAutosensData("test", aapsLogger, dateUtilMocked)?.time).isEqualTo(now - 1)
    }
}
