package info.nightscout.androidaps.data

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.T
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
class NonOverlappingIntervalsTest : TestBase() {

    private val startDate = DateUtil.now()
    var list = NonOverlappingIntervals<TemporaryBasal>()

    val injector = HasAndroidInjector { AndroidInjector {} }

    @Test
    fun doTests() {
        // create one 10h interval and test value in and out
        list.add(TemporaryBasal(injector).date(startDate).duration(T.hours(10).mins().toInt()).absolute(1.0))
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()))
        Assert.assertEquals(1.0, list.getValueByInterval(startDate)!!.absoluteRate, 0.01)
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1))

        // stop temp  after 5h
        list.add(TemporaryBasal(injector).date(startDate + T.hours(5).msecs()).duration(0))
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()))
        Assert.assertEquals(1.0, list.getValueByInterval(startDate)!!.absoluteRate, 0.01)
        Assert.assertEquals(1.0, list.getValueByInterval(startDate + T.hours(5).msecs() - 1)!!.absoluteRate, 0.01)
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(5).msecs() + 1))
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1))

        // insert 1h interval inside
        list.add(TemporaryBasal(injector).date(startDate + T.hours(3).msecs()).duration(T.hours(1).mins().toInt()).absolute(2.0))
        Assert.assertEquals(null, list.getValueByInterval(startDate - T.secs(1).msecs()))
        Assert.assertEquals(1.0, list.getValueByInterval(startDate)!!.absoluteRate, 0.01)
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(5).msecs() - 1))
        Assert.assertEquals(2.0, list.getValueByInterval(startDate + T.hours(3).msecs())!!.absoluteRate, 0.01)
        Assert.assertEquals(2.0, list.getValueByInterval(startDate + T.hours(4).msecs() - 1)!!.absoluteRate, 0.01)
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(4).msecs() + 1))
        Assert.assertEquals(null, list.getValueByInterval(startDate + T.hours(10).msecs() + 1))
    }

    @Test
    fun testCopyConstructor() {
        list.reset()
        list.add(TemporaryBasal(injector).date(startDate).duration(T.hours(10).mins().toInt()).absolute(1.0))
        val list2 = NonOverlappingIntervals(list)
        Assert.assertEquals(1, list2.list.size.toLong())
    }
}