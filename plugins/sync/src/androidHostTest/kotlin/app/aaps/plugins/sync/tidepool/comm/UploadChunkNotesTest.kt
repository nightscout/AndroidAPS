package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.tidepool.elements.NoteElement
import app.aaps.plugins.sync.tidepool.utils.GsonInstance
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Tests for the Tidepool notes (#2834): which copy of a note is uploaded ([UploadChunk.pickNotes]) and how a
 * note looks in Tidepool ([NoteElement]). The bolus wizard writes one note to the bolus and to the carbs,
 * and the carbs can be later.
 */
class UploadChunkNotesTest {

    private val minute = 60_000L

    private fun note(minutes: Long, text: String, source: String) = UploadChunk.Companion.NoteCandidate(minutes * minute, text, source)

    @Test
    fun `a carbs copy of a bolus note within an hour is not uploaded twice`() {
        val picked = UploadChunk.pickNotes(
            bolus = listOf(note(0, "pizza", "bolus")),
            carbs = listOf(note(30, "pizza", "carbs"), note(30, "pasta", "carbs"), note(90, "pizza", "carbs")),
            events = emptyList()
        )

        // The copy 30 min later goes; a different text and the same text 90 min later are other notes
        assertThat(picked.map { it.text to it.timestamp / minute }).containsExactly("pizza" to 0L, "pasta" to 30L, "pizza" to 90L)
    }

    @Test
    fun `an event copy of a carbs note is not uploaded twice`() {
        val picked = UploadChunk.pickNotes(
            bolus = emptyList(),
            carbs = listOf(note(0, "late snack", "carbs")),
            events = listOf(note(5, "late snack ", "event"), note(5, "site sore", "event"))
        )

        assertThat(picked.map { it.text }).containsExactly("late snack", "site sore")
    }

    @Test
    fun `two bolus notes with the same text are both kept`() {
        // The bolus is the source the user chose to trust, so even a repeated bolus note stays
        val picked = UploadChunk.pickNotes(listOf(note(0, "pizza", "bolus"), note(20, "pizza", "bolus")), emptyList(), emptyList())

        assertThat(picked).hasSize(2)
    }

    @Test
    fun `a note is a reportedState record with a stable origin id`() {
        val dateUtil: DateUtil = mock()
        val json = JsonParser.parseString(GsonInstance.defaultGsonInstance().toJson(NoteElement(1000L, " pizza ", "bolus", dateUtil))).asJsonObject

        // Only reportedState records show their notes in Tidepool
        assertThat(json["type"].asString).isEqualTo("reportedState")
        assertThat(json["notes"].asJsonArray.single().asString).isEqualTo("pizza")
        // The same note gives the same id, so a re-upload replaces it; another source at the same time does not
        val id = json["origin"].asJsonObject["id"].asString
        assertThat(NoteElement(1000L, "pizza", "bolus", dateUtil).origin?.id).isEqualTo(id)
        assertThat(NoteElement(1000L, "pizza", "carbs", dateUtil).origin?.id).isNotEqualTo(id)
    }

    @Test
    fun `a long note is cut to what Tidepool accepts`() {
        val note = NoteElement(1000L, "x".repeat(NoteElement.MAX_NOTE_LENGTH + 50), "event", mock())

        assertThat(note.notes.single()).hasLength(NoteElement.MAX_NOTE_LENGTH)
    }
}
