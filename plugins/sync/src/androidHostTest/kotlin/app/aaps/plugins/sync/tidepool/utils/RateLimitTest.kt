package app.aaps.plugins.sync.tidepool.utils

import app.aaps.core.data.time.T
import app.aaps.core.interfaces.utils.DateUtil
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.kotlin.whenever
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitTest {

    @Mock lateinit var dateUtil: DateUtil

    private lateinit var sut: RateLimit

    // Controllable virtual clock backing dateUtil.now()
    private var clock: Long = 0L

    @BeforeEach
    fun setup() {
        clock = 1_000_000L
        whenever(dateUtil.now()).thenAnswer { clock }
        sut = RateLimit(dateUtil)
    }

    @Test
    fun `first call for a key is allowed`() {
        assertThat(sut.rateLimit("test", 30)).isTrue()
    }

    @Test
    fun `second call within the interval is rate limited`() {
        // first call records the timestamp and is allowed
        assertThat(sut.rateLimit("test", 30)).isTrue()
        // immediate second call (no time elapsed) is within the window -> blocked
        assertThat(sut.rateLimit("test", 30)).isFalse()
    }

    @Test
    fun `call just before the interval elapses is still rate limited`() {
        assertThat(sut.rateLimit("test", 30)).isTrue()
        // advance to 1ms before the interval boundary
        clock += T.secs(30L).msecs() - 1
        assertThat(sut.rateLimit("test", 30)).isFalse()
    }

    @Test
    fun `call after the interval elapses is allowed again`() {
        assertThat(sut.rateLimit("test", 30)).isTrue()
        // advance exactly to the interval boundary (now - then == interval, not < interval)
        clock += T.secs(30L).msecs()
        assertThat(sut.rateLimit("test", 30)).isTrue()
    }

    @Test
    fun `independent keys are tracked independently`() {
        // key A used up its allowance
        assertThat(sut.rateLimit("A", 30)).isTrue()
        assertThat(sut.rateLimit("A", 30)).isFalse()
        // key B is unaffected by key A
        assertThat(sut.rateLimit("B", 30)).isTrue()
        // and B then enforces its own window
        assertThat(sut.rateLimit("B", 30)).isFalse()
    }
}
