package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

/**
 * Created by mike on 19.03.2018.
 */
@RunWith(PowerMockRunner::class)
@PrepareForTest(MainApp::class, SP::class)
class ConstraintTest : TestBase() {

    @Test fun doTests() {
        val b = Constraint(true)
        Assert.assertEquals(true, b.value())
        Assert.assertEquals("", b.getReasons(aapsLogger))
        Assert.assertEquals("", b.getMostLimitedReasons(aapsLogger))
        b.set(aapsLogger, false)
        Assert.assertEquals(false, b.value())
        Assert.assertEquals("", b.getReasons(aapsLogger))
        Assert.assertEquals("", b.getMostLimitedReasons(aapsLogger))
        b.set(aapsLogger, true, "Set true", this)
        Assert.assertEquals(true, b.value())
        Assert.assertEquals("ConstraintTest: Set true", b.getReasons(aapsLogger))
        Assert.assertEquals("ConstraintTest: Set true", b.getMostLimitedReasons(aapsLogger))
        b.set(aapsLogger, false, "Set false", this)
        Assert.assertEquals(false, b.value())
        Assert.assertEquals("ConstraintTest: Set true\nConstraintTest: Set false", b.getReasons(aapsLogger))
        Assert.assertEquals("ConstraintTest: Set true\nConstraintTest: Set false", b.getMostLimitedReasons(aapsLogger))
        val d = Constraint(10.0)
        d.set(aapsLogger, 5.0, "Set 5d", this)
        Assert.assertEquals(5.0, d.value(), 0.01)
        Assert.assertEquals("ConstraintTest: Set 5d", d.getReasons(aapsLogger))
        Assert.assertEquals("ConstraintTest: Set 5d", d.getMostLimitedReasons(aapsLogger))
        d.setIfSmaller(aapsLogger, 6.0, "Set 6d", this)
        Assert.assertEquals(5.0, d.value(), 0.01)
        Assert.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d", d.getReasons(aapsLogger))
        Assert.assertEquals("ConstraintTest: Set 5d", d.getMostLimitedReasons(aapsLogger))
        d.setIfSmaller(aapsLogger, 4.0, "Set 4d", this)
        Assert.assertEquals(4.0, d.value(), 0.01)
        Assert.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d\nConstraintTest: Set 4d", d.getReasons(aapsLogger))
        Assert.assertEquals("ConstraintTest: Set 4d", d.getMostLimitedReasons(aapsLogger))
        Assert.assertEquals(10.0, d.originalValue(), 0.01)
        d.setIfDifferent(aapsLogger, 7.0, "Set 7d", this)
        Assert.assertEquals(7.0, d.value(), 0.01)
        Assert.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d\nConstraintTest: Set 4d\nConstraintTest: Set 7d", d.getReasons(aapsLogger))
        Assert.assertEquals("ConstraintTest: Set 4d\nConstraintTest: Set 7d", d.getMostLimitedReasons(aapsLogger))
        Assert.assertEquals(10.0, d.originalValue(), 0.01)
    }

    @Before
    fun prepareMock() {
    }
}