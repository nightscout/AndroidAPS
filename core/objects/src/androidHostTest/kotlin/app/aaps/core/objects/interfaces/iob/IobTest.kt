package app.aaps.core.objects.interfaces.iob

import app.aaps.core.data.iob.Iob
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class IobTest {

    private fun Iob.iobContrib(iobContrib: Double): Iob {
        this.iobContrib = iobContrib
        return this
    }

    private fun Iob.activityContrib(activityContrib: Double): Iob {
        this.activityContrib = activityContrib
        return this
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
