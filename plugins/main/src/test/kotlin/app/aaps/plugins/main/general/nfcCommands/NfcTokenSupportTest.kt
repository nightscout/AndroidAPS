package app.aaps.plugins.main.general.nfcCommands

import app.aaps.shared.tests.SharedPreferencesMock
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NfcTokenSupportTest {
    private lateinit var prefs: SharedPreferencesMock
    private val now = 1_700_000_000_000L
    private val tagUid = "aabbccdd"

    @BeforeEach
    fun setup() {
        prefs = SharedPreferencesMock()
    }

    private fun makeTag(
        uid: String = "tag-uid-1",
        name: String = "Test Tag",
    ): NfcCreatedTag =
        NfcCreatedTag(
            tagUid = uid,
            name = name,
            commands = listOf("LOOP STOP"),
            createdAtMillis = now,
        )

    // ── saveCreatedTag / loadCreatedTags ──────────────────────────────────────

    @Test
    fun `saveCreatedTag and loadCreatedTags round-trips correctly`() {
        val tag = makeTag()
        NfcTokenSupport.saveCreatedTag(prefs, tag)

        val loaded = NfcTokenSupport.loadCreatedTags(prefs)
        assertThat(loaded).hasSize(1)
        assertThat(loaded.first().tagUid).isEqualTo(tag.tagUid)
        assertThat(loaded.first().name).isEqualTo(tag.name)
        assertThat(loaded.first().commands).isEqualTo(tag.commands)
        assertThat(loaded.first().createdAtMillis).isEqualTo(tag.createdAtMillis)
    }

    @Test
    fun `saveCreatedTag replaces existing tag with same uid`() {
        val original = makeTag(uid = "same-uid")
        val updated = original.copy(name = "Updated Name")
        NfcTokenSupport.saveCreatedTag(prefs, original)

        NfcTokenSupport.saveCreatedTag(prefs, updated)

        val tags = NfcTokenSupport.loadCreatedTags(prefs)
        assertThat(tags).hasSize(1)
        assertThat(tags.first().tagUid).isEqualTo("same-uid")
        assertThat(tags.first().name).isEqualTo("Updated Name")
    }

    @Test
    fun `saveCreatedTag uid matching is case-insensitive`() {
        val original = makeTag(uid = "AABBCCDD")
        NfcTokenSupport.saveCreatedTag(prefs, original)
        val updated = original.copy(tagUid = "aabbccdd", name = "Lower")
        NfcTokenSupport.saveCreatedTag(prefs, updated)

        val tags = NfcTokenSupport.loadCreatedTags(prefs)
        assertThat(tags).hasSize(1)
        assertThat(tags.first().name).isEqualTo("Lower")
    }

    // ── deleteCreatedTag ──────────────────────────────────────────────────────

    @Test
    fun `deleteCreatedTag removes tag from list`() {
        val tag = makeTag(uid = "uid-to-delete")
        NfcTokenSupport.saveCreatedTag(prefs, tag)
        assertThat(NfcTokenSupport.loadCreatedTags(prefs)).hasSize(1)

        NfcTokenSupport.deleteCreatedTag(prefs, "uid-to-delete")

        assertThat(NfcTokenSupport.loadCreatedTags(prefs)).isEmpty()
    }

    @Test
    fun `deleteCreatedTag does not remove other tags`() {
        val tag1 = makeTag(uid = "uid-1")
        val tag2 = makeTag(uid = "uid-2")
        NfcTokenSupport.saveCreatedTag(prefs, tag1)
        NfcTokenSupport.saveCreatedTag(prefs, tag2)

        NfcTokenSupport.deleteCreatedTag(prefs, "uid-1")

        val remaining = NfcTokenSupport.loadCreatedTags(prefs)
        assertThat(remaining).hasSize(1)
        assertThat(remaining.first().tagUid).isEqualTo("uid-2")
    }

    // ── findTagByUid ──────────────────────────────────────────────────────────

    @Test
    fun `findTagByUid returns tag when uid matches`() {
        val tag = makeTag(uid = tagUid)
        NfcTokenSupport.saveCreatedTag(prefs, tag)

        val found = NfcTokenSupport.findTagByUid(prefs, tagUid)

        assertThat(found).isNotNull()
        assertThat(found!!.tagUid).isEqualTo(tagUid)
    }

    @Test
    fun `findTagByUid returns null for unknown uid`() {
        val found = NfcTokenSupport.findTagByUid(prefs, "unknown-uid")

        assertThat(found).isNull()
    }

    @Test
    fun `findTagByUid is case-insensitive`() {
        val tag = makeTag(uid = "AABBCCDD")
        NfcTokenSupport.saveCreatedTag(prefs, tag)

        val found = NfcTokenSupport.findTagByUid(prefs, "aabbccdd")

        assertThat(found).isNotNull()
    }

    // ── loadCreatedTags edge cases ────────────────────────────────────────────

    @Test
    fun `loadCreatedTags ignores entries without commands array`() {
        prefs.edit().putString("nfccommunicator_created_tags_v1", """[{"tagUid":"abc","name":"x","createdAtMillis":0}]""").apply()

        val tags = NfcTokenSupport.loadCreatedTags(prefs)

        assertThat(tags).isEmpty()
    }

    @Test
    fun `loadCreatedTags ignores entries with blank tagUid`() {
        prefs.edit().putString("nfccommunicator_created_tags_v1", """[{"tagUid":"","name":"x","commands":["LOOP STOP"],"createdAtMillis":0}]""").apply()

        val tags = NfcTokenSupport.loadCreatedTags(prefs)

        assertThat(tags).isEmpty()
    }

    @Test
    fun `loadCreatedTags returns empty list for malformed json`() {
        prefs.edit().putString("nfccommunicator_created_tags_v1", "{not-valid-json").apply()

        val tags = NfcTokenSupport.loadCreatedTags(prefs)

        assertThat(tags).isEmpty()
    }

    // ── buildCascade tests ────────────────────────────────────────────────────

    @Test
    fun `buildCascade returns null if any step has missing required args`() {
        val suspendTemplate =
            NfcTokenSupport.availableCommands().first {
                it.labelResId ==
                    app.aaps.plugins.main.R.string.nfccommands_cmd_loop_suspend
            }
        val stopTemplate =
            NfcTokenSupport.availableCommands().first {
                it.labelResId ==
                    app.aaps.plugins.main.R.string.nfccommands_cmd_loop_stop
            }
        val steps = listOf(stopTemplate to "", suspendTemplate to "") // suspend requires args

        val result = NfcTokenSupport.buildCascade(steps)

        assertThat(result).isNull()
    }

    @Test
    fun `buildCascade returns list of commands for valid steps`() {
        val stopTemplate =
            NfcTokenSupport.availableCommands().first {
                it.labelResId ==
                    app.aaps.plugins.main.R.string.nfccommands_cmd_loop_stop
            }
        val resumeTemplate =
            NfcTokenSupport.availableCommands().first {
                it.labelResId ==
                    app.aaps.plugins.main.R.string.nfccommands_cmd_loop_resume
            }
        val steps = listOf(stopTemplate to "", resumeTemplate to "")

        val result = NfcTokenSupport.buildCascade(steps)

        assertThat(result).isEqualTo(listOf("LOOP STOP", "LOOP RESUME"))
    }

    @Test
    fun `buildCascade returns null for empty list`() {
        val result = NfcTokenSupport.buildCascade(emptyList())

        assertThat(result).isNull()
    }

    @Test
    fun `tagUidHex returns lowercase hex and null for null input`() {
        assertThat(NfcTokenSupport.tagUidHex(byteArrayOf(0x0A, 0x1B, 0x2C))).isEqualTo("0a1b2c")
        assertThat(NfcTokenSupport.tagUidHex(null)).isNull()
    }

    // ── log tests ─────────────────────────────────────────────────────────────

    @Test
    fun `appendLogEntry persists an entry`() {
        val entry = NfcLogEntry(timestamp = now, tagName = "MyTag", action = "READ", success = true, message = "ok")
        NfcTokenSupport.appendLogEntry(prefs, entry)

        val loaded = NfcTokenSupport.loadLog(prefs)
        assertThat(loaded).hasSize(1)
        assertThat(loaded.first().tagName).isEqualTo("MyTag")
        assertThat(loaded.first().action).isEqualTo("READ")
        assertThat(loaded.first().success).isTrue()
    }

    @Test
    fun `loadLog returns entries newest first`() {
        val entry1 = NfcLogEntry(timestamp = now, tagName = "Tag1", action = "READ", success = true, message = "ok")
        val entry2 = NfcLogEntry(timestamp = now + 1000L, tagName = "Tag2", action = "WRITE", success = true, message = "written")
        NfcTokenSupport.appendLogEntry(prefs, entry1)
        NfcTokenSupport.appendLogEntry(prefs, entry2)

        val loaded = NfcTokenSupport.loadLog(prefs)
        assertThat(loaded).hasSize(2)
        assertThat(loaded[0].tagName).isEqualTo("Tag2")
        assertThat(loaded[1].tagName).isEqualTo("Tag1")
    }

    @Test
    fun `appendLogEntry prunes to 100 when exceeding max`() {
        repeat(105) { i ->
            NfcTokenSupport.appendLogEntry(prefs, NfcLogEntry(now + i, "Tag$i", "READ", true, "msg"))
        }

        val loaded = NfcTokenSupport.loadLog(prefs)
        assertThat(loaded).hasSize(100)
    }

    @Test
    fun `loadLog handles malformed JSON gracefully`() {
        prefs.edit().putString("nfccommunicator_log_v1", "not-valid-json").apply()

        val loaded = NfcTokenSupport.loadLog(prefs)
        assertThat(loaded).isEmpty()
    }

    @Test
    fun `appendLogEntry for manual execution records READ action with correct fields`() {
        val entry = NfcLogEntry(
            timestamp = now,
            tagName = "Morning Routine",
            action = "READ",
            success = true,
            message = "Loop disabled\nTemp basal canceled",
        )
        NfcTokenSupport.appendLogEntry(prefs, entry)

        val loaded = NfcTokenSupport.loadLog(prefs)
        assertThat(loaded).hasSize(1)
        with(loaded.first()) {
            assertThat(tagName).isEqualTo("Morning Routine")
            assertThat(action).isEqualTo("READ")
            assertThat(success).isTrue()
            assertThat(message).contains("Loop disabled")
            assertThat(message).contains("Temp basal canceled")
        }
    }

    @Test
    fun `appendLogEntry for failed manual execution records failure`() {
        val entry = NfcLogEntry(
            timestamp = now,
            tagName = "Evening",
            action = "READ",
            success = false,
            message = "Remote command not allowed",
        )
        NfcTokenSupport.appendLogEntry(prefs, entry)

        val loaded = NfcTokenSupport.loadLog(prefs)
        assertThat(loaded.first().success).isFalse()
        assertThat(loaded.first().message).isEqualTo("Remote command not allowed")
    }
}
