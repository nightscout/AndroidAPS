package info.nightscout.androidaps.db

import android.content.Context
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.interfaces.DatabaseHelperInterface
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*
import java.util.logging.Logger

@RunWith(PowerMockRunner::class)
@PrepareForTest(MainApp::class, Logger::class, L::class, GlucoseStatus::class)
class BgReadingTest : TestBase() {

    @Mock lateinit var defaultValueHelper: DefaultValueHelper
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var resourceHelper: ResourceHelper
    @Mock lateinit var databaseHelper: DatabaseHelperInterface

    var injector: HasAndroidInjector = HasAndroidInjector {
        AndroidInjector {
            if (it is BgReading) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
                it.defaultValueHelper = defaultValueHelper
                it.profileFunction = profileFunction
            }
        }
    }

    @Test
    fun valueToUnits() {
        val bgReading = BgReading(injector)
        bgReading.value = 18.0
        Assert.assertEquals(18.0, bgReading.valueToUnits(Constants.MGDL) * 1, 0.01)
        Assert.assertEquals(1.0, bgReading.valueToUnits(Constants.MMOL) * 1, 0.01)
    }

    @Test
    fun directionToSymbol() {
        val bgReading = BgReading(injector)
        bgReading.direction = "DoubleDown"
        Assert.assertEquals("\u21ca", bgReading.directionToSymbol(databaseHelper))
        bgReading.direction = "SingleDown"
        Assert.assertEquals("\u2193", bgReading.directionToSymbol(databaseHelper))
        bgReading.direction = "FortyFiveDown"
        Assert.assertEquals("\u2198", bgReading.directionToSymbol(databaseHelper))
        bgReading.direction = "Flat"
        Assert.assertEquals("\u2192", bgReading.directionToSymbol(databaseHelper))
        bgReading.direction = "FortyFiveUp"
        Assert.assertEquals("\u2197", bgReading.directionToSymbol(databaseHelper))
        bgReading.direction = "SingleUp"
        Assert.assertEquals("\u2191", bgReading.directionToSymbol(databaseHelper))
        bgReading.direction = "DoubleUp"
        Assert.assertEquals("\u21c8", bgReading.directionToSymbol(databaseHelper))
        bgReading.direction = "OUT OF RANGE"
        Assert.assertEquals("??", bgReading.directionToSymbol(databaseHelper))
    }

    @Test
    fun directionToIcon() {
        val bgReading = BgReading(injector)
        bgReading.direction = "DoubleDown"
        Assert.assertEquals(R.drawable.ic_doubledown, bgReading.directionToIcon(databaseHelper))
        bgReading.direction = "SingleDown"
        Assert.assertEquals(R.drawable.ic_singledown, bgReading.directionToIcon(databaseHelper))
        bgReading.direction = "FortyFiveDown"
        Assert.assertEquals(R.drawable.ic_fortyfivedown, bgReading.directionToIcon(databaseHelper))
        bgReading.direction = "Flat"
        Assert.assertEquals(R.drawable.ic_flat, bgReading.directionToIcon(databaseHelper))
        bgReading.direction = "FortyFiveUp"
        Assert.assertEquals(R.drawable.ic_fortyfiveup, bgReading.directionToIcon(databaseHelper))
        bgReading.direction = "SingleUp"
        Assert.assertEquals(R.drawable.ic_singleup, bgReading.directionToIcon(databaseHelper))
        bgReading.direction = "DoubleUp"
        Assert.assertEquals(R.drawable.ic_doubleup, bgReading.directionToIcon(databaseHelper))
        bgReading.direction = "OUT OF RANGE"
        Assert.assertEquals(R.drawable.ic_invalid, bgReading.directionToIcon(databaseHelper))
    }


    @Test fun dateTest() {
        val bgReading = BgReading(injector)
        val now = System.currentTimeMillis()
        bgReading.date = now
        val nowDate = Date(now)
        Assert.assertEquals(now, bgReading.date(now).date)
        Assert.assertEquals(now, bgReading.date(nowDate).date)
    }

    @Test fun valueTest() {
        val bgReading = BgReading(injector)
        val valueToSet = 81.0 // 4.5 mmol
        Assert.assertEquals(81.0, bgReading.value(valueToSet).value, 0.01)
    }

    @Test fun copyFromTest() {
//        val databaseHelper = Mockito.mock(DatabaseHelper::class.java)
//        `when`(MainApp.getDbHelper()).thenReturn(databaseHelper)
        setReadings(72, 0)
        val bgReading = BgReading(injector)
        val copy = BgReading(injector)
        bgReading.value = 81.0
        val now = System.currentTimeMillis()
        bgReading.date = now
        copy.date = now
        copy.copyFrom(bgReading)
        Assert.assertEquals(81.0, copy.value, 0.1)
        Assert.assertEquals(now, copy.date)
        Assert.assertEquals(bgReading.directionToSymbol(databaseHelper), copy.directionToSymbol(databaseHelper))
    }

    @Test
    fun isEqualTest() {
        val bgReading = BgReading(injector)
        val copy = BgReading(injector)
        bgReading.value = 81.0
        val now = System.currentTimeMillis()
        bgReading.date = now
        copy.date = now
        copy.copyFrom(bgReading)
        Assert.assertTrue(copy.isEqual(bgReading))
        Assert.assertFalse(copy.isEqual(BgReading(injector)))
    }

    @Test fun calculateDirection() {
        val bgReading = BgReading(injector)
        val bgReadingsList: List<BgReading>? = null
//        val databaseHelper = Mockito.mock(DatabaseHelper::class.java)
//        `when`(MainApp.getDbHelper()).thenReturn(databaseHelper)
        `when`(databaseHelper.getAllBgreadingsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(bgReadingsList)
        Assert.assertEquals("NONE", bgReading.calculateDirection(databaseHelper))
        setReadings(72, 0)
        Assert.assertEquals("DoubleUp", bgReading.calculateDirection(databaseHelper))
        setReadings(76, 60)
        Assert.assertEquals("SingleUp", bgReading.calculateDirection(databaseHelper))
        setReadings(74, 65)
        Assert.assertEquals("FortyFiveUp", bgReading.calculateDirection(databaseHelper))
        setReadings(72, 72)
        Assert.assertEquals("Flat", bgReading.calculateDirection(databaseHelper))
        setReadings(0, 72)
        Assert.assertEquals("DoubleDown", bgReading.calculateDirection(databaseHelper))
        setReadings(60, 76)
        Assert.assertEquals("SingleDown", bgReading.calculateDirection(databaseHelper))
        setReadings(65, 74)
        Assert.assertEquals("FortyFiveDown", bgReading.calculateDirection(databaseHelper))
    }

    @Before
    fun prepareMock() {
    }

    fun setReadings(current_value: Int, previous_value: Int) {
        val now = BgReading(injector)
        now.value = current_value.toDouble()
        now.date = System.currentTimeMillis()
        val previous = BgReading(injector)
        previous.value = previous_value.toDouble()
        previous.date = System.currentTimeMillis() - 6 * 60 * 1000L
        val bgReadings: MutableList<BgReading> = mutableListOf()
        bgReadings.add(now)
        bgReadings.add(previous)
        `when`(databaseHelper.getAllBgreadingsDataFromTime(ArgumentMatchers.anyLong(), ArgumentMatchers.anyBoolean())).thenReturn(bgReadings)
    }
}