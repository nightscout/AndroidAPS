package app.aaps.core.interfaces.utils

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import kotlin.math.abs

@Suppress("SpellCheckingInspection")
class TTest {

    @Test fun toUnits() {
        assertThat(T.msecs(1000).secs()).isEqualTo(1)
        assertThat(T.secs(60).mins()).isEqualTo(1)
        assertThat(T.mins(60).hours()).isEqualTo(1)
        assertThat(T.hours(24).days()).isEqualTo(1)
        assertThat(T.days(1).hours()).isEqualTo(24)
        assertThat(T.mins(1).msecs()).isEqualTo(60000)
    }

    @Test fun now() {
        assertThat(abs(T.now().msecs() - System.currentTimeMillis())).isLessThan(5_000L)
    }

    @Test fun additions() {
        val nowMsecs = System.currentTimeMillis()
        val now = T.msecs(nowMsecs)
        assertThat(nowMsecs + 5 * 1000).isEqualTo(now.plus(T.secs(5)).msecs())
        assertThat(nowMsecs + 5 * 60 * 1000).isEqualTo(now.plus(T.mins(5)).msecs())
        assertThat(nowMsecs + 5 * 60 * 60 * 1000).isEqualTo(now.plus(T.hours(5)).msecs())
        assertThat(nowMsecs + 5 * 24 * 60 * 60 * 1000).isEqualTo(now.plus(T.days(5)).msecs())
    }

    @Test fun subtractions() {
        val nowMsecs = System.currentTimeMillis()
        val now = T.msecs(nowMsecs)
        assertThat(nowMsecs - 5 * 1000).isEqualTo(now.minus(T.secs(5)).msecs())
        assertThat(nowMsecs - 5 * 60 * 1000).isEqualTo(now.minus(T.mins(5)).msecs())
        assertThat(nowMsecs - 5 * 60 * 60 * 1000).isEqualTo(now.minus(T.hours(5)).msecs())
        assertThat(nowMsecs - 5 * 24 * 60 * 60 * 1000).isEqualTo(now.minus(T.days(5)).msecs())
    }
}
