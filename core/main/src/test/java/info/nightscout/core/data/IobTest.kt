package info.nightscout.core.data

import com.google.common.truth.Truth.assertThat
import info.nightscout.interfaces.iob.Iob
import org.junit.jupiter.api.Test

class IobTest {

    @Test fun plusTest() {
        val a = Iob().iobContrib(1.0).activityContrib(2.0)
        val b = Iob().iobContrib(3.0).activityContrib(4.0)
        a.plus(b)
        assertThat(a.iobContrib).isWithin(0.01).of(4.0)
        assertThat(a.activityContrib).isWithin(0.01).of(6.0)
    }

    @Test fun equalTest() {
        val a1 = Iob().iobContrib(1.0).activityContrib(2.0)
        val a2 = Iob().iobContrib(1.0).activityContrib(2.0)
        val b = Iob().iobContrib(3.0).activityContrib(4.0)
        assertThat(a1).isEqualTo(a1)
        assertThat(a1).isEqualTo(a2)
        assertThat(a1).isNotEqualTo(b)
        assertThat(a1).isNotNull()
        assertThat(a1).isNotEqualTo(Any())
    }

    @Test fun hashCodeTest() {
        val a = Iob().iobContrib(1.0).activityContrib(2.0)
        assertThat(a.hashCode().toLong()).isNotEqualTo(0L)
    }
}
