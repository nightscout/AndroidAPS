package app.aaps.plugins.sync.tidepool.compose

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TidepoolLogTest {

    @Test
    fun `stores the status text verbatim`() {
        assertThat(TidepoolLog("uploading...").status).isEqualTo("uploading...")
    }

    @Test
    fun `assigns strictly increasing ids across instances`() {
        val first = TidepoolLog("a")
        val second = TidepoolLog("b")
        assertThat(second.id).isGreaterThan(first.id)
    }

    @Test
    fun `captures a creation timestamp within the surrounding wall-clock window`() {
        val before = System.currentTimeMillis()
        val log = TidepoolLog("t")
        val after = System.currentTimeMillis()
        assertThat(log.date).isAtLeast(before)
        assertThat(log.date).isAtMost(after)
    }
}
