package info.nightscout.core.data

import info.nightscout.interfaces.iob.Iob
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class IobTest {

    @Test fun plusTest() {
        val a = Iob().iobContrib(1.0).activityContrib(2.0)
        val b = Iob().iobContrib(3.0).activityContrib(4.0)
        a.plus(b)
        Assertions.assertEquals(4.0, a.iobContrib, 0.01)
        Assertions.assertEquals(6.0, a.activityContrib, 0.01)
    }

    @Test fun equalTest() {
        val a1 = Iob().iobContrib(1.0).activityContrib(2.0)
        val a2 = Iob().iobContrib(1.0).activityContrib(2.0)
        val b = Iob().iobContrib(3.0).activityContrib(4.0)
        Assertions.assertTrue(a1 == a2)
        Assertions.assertFalse(a1 == b)
        @Suppress("SENSELESS_COMPARISON")
        Assertions.assertFalse(a1 == null)
        Assertions.assertFalse(a1 == Any())
    }

    @Test fun hashCodeTest() {
        val a = Iob().iobContrib(1.0).activityContrib(2.0)
        Assertions.assertNotEquals(0, a.hashCode().toLong())
    }
}