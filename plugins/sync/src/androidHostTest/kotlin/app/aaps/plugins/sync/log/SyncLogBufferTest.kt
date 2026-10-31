package app.aaps.plugins.sync.log

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

/** Tests for [SyncLogBuffer], the log the Nightscout, xDrip and Tidepool screens show. */
class SyncLogBufferTest {

    private val aapsLogger: AAPSLogger = mock()
    private val sut = SyncLogBuffer(aapsLogger, LTag.TIDEPOOL, maxEntries = 3)

    @Test
    fun `newest line is first`() {
        sut.add("first")
        sut.add("second")

        assertThat(sut.entries.value.map { it.action }).containsExactly("second", "first").inOrder()
    }

    @Test
    fun `only the newest lines are kept`() {
        (1..5).forEach { sut.add("line $it") }

        assertThat(sut.entries.value.map { it.action }).containsExactly("line 5", "line 4", "line 3").inOrder()
    }

    @Test
    fun `a line keeps its text and json`() {
        sut.add("UPLOAD", "3 treatments", JsonPrimitive(3))

        val entry = sut.entries.value.single()
        assertThat(entry.text).isEqualTo("3 treatments")
        assertThat(entry.json).isEqualTo(JsonPrimitive(3))
    }

    @Test
    fun `every line has its own id, used as the list key`() {
        sut.add("a")
        sut.add("b")

        assertThat(sut.entries.value.map { it.id }.toSet()).hasSize(2)
    }

    @Test
    fun `the debug log gets the line, without the word null when there is no text`() {
        sut.add("Uploading")
        sut.add("UPLOAD", "3 treatments")

        verify(aapsLogger).debug(LTag.TIDEPOOL, "Uploading")
        verify(aapsLogger).debug(LTag.TIDEPOOL, "UPLOAD 3 treatments")
    }

    @Test
    fun `clear empties the log`() {
        sut.add("something")

        sut.clear()

        assertThat(sut.entries.value).isEmpty()
    }
}
