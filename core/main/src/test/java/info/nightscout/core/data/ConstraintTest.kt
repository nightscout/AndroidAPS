package info.nightscout.core.data

import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Created by mike on 19.03.2018.
 */
class ConstraintTest : TestBase() {

    @Test fun doTests() {
        val b = Constraint(true)
        Assertions.assertEquals(true, b.value())
        Assertions.assertEquals("", b.getReasons(aapsLogger))
        Assertions.assertEquals("", b.getMostLimitedReasons(aapsLogger))
        b.set(aapsLogger, false)
        Assertions.assertEquals(false, b.value())
        Assertions.assertEquals("", b.getReasons(aapsLogger))
        Assertions.assertEquals("", b.getMostLimitedReasons(aapsLogger))
        b.set(aapsLogger, true, "Set true", this)
        Assertions.assertEquals(true, b.value())
        Assertions.assertEquals("ConstraintTest: Set true", b.getReasons(aapsLogger))
        Assertions.assertEquals("ConstraintTest: Set true", b.getMostLimitedReasons(aapsLogger))
        b.set(aapsLogger, false, "Set false", this)
        Assertions.assertEquals(false, b.value())
        Assertions.assertEquals("ConstraintTest: Set true\nConstraintTest: Set false", b.getReasons(aapsLogger))
        Assertions.assertEquals("ConstraintTest: Set true\nConstraintTest: Set false", b.getMostLimitedReasons(aapsLogger))
        val d = Constraint(10.0)
        d.set(aapsLogger, 5.0, "Set 5d", this)
        Assertions.assertEquals(5.0, d.value(), 0.01)
        Assertions.assertEquals("ConstraintTest: Set 5d", d.getReasons(aapsLogger))
        Assertions.assertEquals("ConstraintTest: Set 5d", d.getMostLimitedReasons(aapsLogger))
        d.setIfSmaller(aapsLogger, 6.0, "Set 6d", this)
        Assertions.assertEquals(5.0, d.value(), 0.01)
        Assertions.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d", d.getReasons(aapsLogger))
        Assertions.assertEquals("ConstraintTest: Set 5d", d.getMostLimitedReasons(aapsLogger))
        d.setIfSmaller(aapsLogger, 4.0, "Set 4d", this)
        Assertions.assertEquals(4.0, d.value(), 0.01)
        Assertions.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d\nConstraintTest: Set 4d", d.getReasons(aapsLogger))
        Assertions.assertEquals("ConstraintTest: Set 4d", d.getMostLimitedReasons(aapsLogger))
        Assertions.assertEquals(10.0, d.originalValue(), 0.01)
        d.setIfDifferent(aapsLogger, 7.0, "Set 7d", this)
        Assertions.assertEquals(7.0, d.value(), 0.01)
        Assertions.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d\nConstraintTest: Set 4d\nConstraintTest: Set 7d", d.getReasons(aapsLogger))
        Assertions.assertEquals("ConstraintTest: Set 4d\nConstraintTest: Set 7d", d.getMostLimitedReasons(aapsLogger))
        Assertions.assertEquals(10.0, d.originalValue(), 0.01)
    }

    @BeforeEach
    fun prepareMock() {
    }
}