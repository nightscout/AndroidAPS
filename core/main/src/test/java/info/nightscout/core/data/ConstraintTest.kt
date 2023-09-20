package info.nightscout.core.data

import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.sharedtests.TestBase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Created by mike on 19.03.2018.
 */
class ConstraintTest : TestBase() {

    @Test fun doTests() {
        val b = ConstraintObject(true, aapsLogger)
        Assertions.assertEquals(true, b.value())
        Assertions.assertEquals("", b.getReasons())
        Assertions.assertEquals("", b.getMostLimitedReasons())
        b.set(false)
        Assertions.assertEquals(false, b.value())
        Assertions.assertEquals("", b.getReasons())
        Assertions.assertEquals("", b.getMostLimitedReasons())
        b.set(true, "Set true", this)
        Assertions.assertEquals(true, b.value())
        Assertions.assertEquals("ConstraintTest: Set true", b.getReasons())
        Assertions.assertEquals("ConstraintTest: Set true", b.getMostLimitedReasons())
        b.set(false, "Set false", this)
        Assertions.assertEquals(false, b.value())
        Assertions.assertEquals("ConstraintTest: Set true\nConstraintTest: Set false", b.getReasons())
        Assertions.assertEquals("ConstraintTest: Set true\nConstraintTest: Set false", b.getMostLimitedReasons())
        val d = ConstraintObject(10.0, aapsLogger)
        d.set(5.0, "Set 5d", this)
        Assertions.assertEquals(5.0, d.value(), 0.01)
        Assertions.assertEquals("ConstraintTest: Set 5d", d.getReasons())
        Assertions.assertEquals("ConstraintTest: Set 5d", d.getMostLimitedReasons())
        d.setIfSmaller(6.0, "Set 6d", this)
        Assertions.assertEquals(5.0, d.value(), 0.01)
        Assertions.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d", d.getReasons())
        Assertions.assertEquals("ConstraintTest: Set 5d", d.getMostLimitedReasons())
        d.setIfSmaller(4.0, "Set 4d", this)
        Assertions.assertEquals(4.0, d.value(), 0.01)
        Assertions.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d\nConstraintTest: Set 4d", d.getReasons())
        Assertions.assertEquals("ConstraintTest: Set 4d", d.getMostLimitedReasons())
        Assertions.assertEquals(10.0, d.originalValue(), 0.01)
        d.setIfDifferent(7.0, "Set 7d", this)
        Assertions.assertEquals(7.0, d.value(), 0.01)
        Assertions.assertEquals("ConstraintTest: Set 5d\nConstraintTest: Set 6d\nConstraintTest: Set 4d\nConstraintTest: Set 7d", d.getReasons())
        Assertions.assertEquals("ConstraintTest: Set 4d\nConstraintTest: Set 7d", d.getMostLimitedReasons())
        Assertions.assertEquals(10.0, d.originalValue(), 0.01)
    }

    @BeforeEach
    fun prepareMock() {
    }
}