package app.aaps.plugins.main.general.nfcCommands

import app.aaps.shared.tests.SharedPreferencesMock
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NfcTagStoreTest {
    private lateinit var prefs: SharedPreferencesMock
    private lateinit var store: NfcTagStore
    private val now = 1_700_000_000_000L
    private val tagUid = "aabbccdd"

    @BeforeEach
    fun setup() {
        prefs = SharedPreferencesMock()
        store = NfcTagStore(TestSp(prefs))
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
        store.saveCreatedTag(tag)

        val loaded = store.loadCreatedTags()
        assertThat(loaded).hasSize(1)
        assertThat(loaded.first().tagUid).isEqualTo(tag.tagUid)
        assertThat(loaded.first().name).isEqualTo(tag.name)
        assertThat(loaded.first().commands).isEqualTo(tag.commands)
        assertThat(loaded.first().createdAtMillis).isEqualTo(tag.createdAtMillis)
        assertThat(loaded.first().lastScannedAtMillis).isNull()
    }

    @Test
    fun `saveCreatedTag replaces existing tag with same uid`() {
        val original = makeTag(uid = "same-uid")
        val updated = original.copy(name = "Updated Name")
        store.saveCreatedTag(original)

        store.saveCreatedTag(updated)

        val tags = store.loadCreatedTags()
        assertThat(tags).hasSize(1)
        assertThat(tags.first().tagUid).isEqualTo("same-uid")
        assertThat(tags.first().name).isEqualTo("Updated Name")
    }

    @Test
    fun `saveCreatedTag uid matching is case-insensitive`() {
        val original = makeTag(uid = "AABBCCDD")
        store.saveCreatedTag(original)
        val updated = original.copy(tagUid = "aabbccdd", name = "Lower")
        store.saveCreatedTag(updated)

        val tags = store.loadCreatedTags()
        assertThat(tags).hasSize(1)
        assertThat(tags.first().name).isEqualTo("Lower")
    }

    // ── updateLastScanned ─────────────────────────────────────────────────────

    @Test
    fun `updateLastScanned sets lastScannedAtMillis on matching tag`() {
        val tag = makeTag(uid = tagUid)
        store.saveCreatedTag(tag)
        val scannedAt = now + 5000L

        store.updateLastScanned(tagUid, scannedAt)

        val loaded = store.loadCreatedTags().first()
        assertThat(loaded.lastScannedAtMillis).isEqualTo(scannedAt)
    }

    @Test
    fun `updateLastScanned does nothing for unknown uid`() {
        val tag = makeTag(uid = tagUid)
        store.saveCreatedTag(tag)

        store.updateLastScanned("unknown-uid", now + 1000L)

        assertThat(store.loadCreatedTags().first().lastScannedAtMillis).isNull()
    }

    @Test
    fun `lastScannedAtMillis is null when loaded from JSON without the field`() {
        prefs.edit().putString(
            "nfccommunicator_created_tags_v1",
            """[{"tagUid":"abc","name":"x","commands":["LOOP STOP"],"createdAtMillis":1000}]""",
        ).apply()

        val tag = store.loadCreatedTags().first()
        assertThat(tag.lastScannedAtMillis).isNull()
    }

    // ── deleteCreatedTag ──────────────────────────────────────────────────────

    @Test
    fun `deleteCreatedTag removes tag from list`() {
        val tag = makeTag(uid = "uid-to-delete")
        store.saveCreatedTag(tag)
        assertThat(store.loadCreatedTags()).hasSize(1)

        store.deleteCreatedTag("uid-to-delete")

        assertThat(store.loadCreatedTags()).isEmpty()
    }

    @Test
    fun `deleteCreatedTag does not remove other tags`() {
        val tag1 = makeTag(uid = "uid-1")
        val tag2 = makeTag(uid = "uid-2")
        store.saveCreatedTag(tag1)
        store.saveCreatedTag(tag2)

        store.deleteCreatedTag("uid-1")

        val remaining = store.loadCreatedTags()
        assertThat(remaining).hasSize(1)
        assertThat(remaining.first().tagUid).isEqualTo("uid-2")
    }

    // ── findTagByUid ──────────────────────────────────────────────────────────

    @Test
    fun `findTagByUid returns tag when uid matches`() {
        val tag = makeTag(uid = tagUid)
        store.saveCreatedTag(tag)

        val found = store.findTagByUid(tagUid)

        assertThat(found).isNotNull()
        assertThat(found!!.tagUid).isEqualTo(tagUid)
    }

    @Test
    fun `findTagByUid returns null for unknown uid`() {
        val found = store.findTagByUid("unknown-uid")

        assertThat(found).isNull()
    }

    @Test
    fun `findTagByUid is case-insensitive`() {
        val tag = makeTag(uid = "AABBCCDD")
        store.saveCreatedTag(tag)

        val found = store.findTagByUid("aabbccdd")

        assertThat(found).isNotNull()
    }

    // ── loadCreatedTags edge cases ────────────────────────────────────────────

    @Test
    fun `loadCreatedTags ignores entries without commands array`() {
        prefs.edit().putString("nfccommunicator_created_tags_v1", """[{"tagUid":"abc","name":"x","createdAtMillis":0}]""").apply()

        val tags = store.loadCreatedTags()

        assertThat(tags).isEmpty()
    }

    @Test
    fun `loadCreatedTags ignores entries with blank tagUid`() {
        prefs.edit().putString("nfccommunicator_created_tags_v1", """[{"tagUid":"","name":"x","commands":["LOOP STOP"],"createdAtMillis":0}]""").apply()

        val tags = store.loadCreatedTags()

        assertThat(tags).isEmpty()
    }

    @Test
    fun `loadCreatedTags returns empty list for malformed json`() {
        prefs.edit().putString("nfccommunicator_created_tags_v1", "{not-valid-json").apply()

        val tags = store.loadCreatedTags()

        assertThat(tags).isEmpty()
    }

    // ── buildCascade tests ────────────────────────────────────────────────────

    @Test
    fun `buildCascade returns null if any step has missing required args`() {
        val suspendTemplate =
            NfcTagStore.availableCommands().first {
                it.labelResId ==
                    app.aaps.plugins.main.R.string.nfccommands_cmd_loop_suspend
            }
        val stopTemplate =
            NfcTagStore.availableCommands().first {
                it.labelResId ==
                    app.aaps.plugins.main.R.string.nfccommands_cmd_loop_stop
            }
        val steps = listOf(stopTemplate to "", suspendTemplate to "") // suspend requires args

        val result = NfcTagStore.buildCascade(steps)

        assertThat(result).isNull()
    }

    @Test
    fun `buildCascade returns list of commands for valid steps`() {
        val stopTemplate =
            NfcTagStore.availableCommands().first {
                it.labelResId ==
                    app.aaps.plugins.main.R.string.nfccommands_cmd_loop_stop
            }
        val resumeTemplate =
            NfcTagStore.availableCommands().first {
                it.labelResId ==
                    app.aaps.plugins.main.R.string.nfccommands_cmd_loop_resume
            }
        val steps = listOf(stopTemplate to "", resumeTemplate to "")

        val result = NfcTagStore.buildCascade(steps)

        assertThat(result).isEqualTo(listOf("LOOP STOP", "LOOP RESUME"))
    }

    @Test
    fun `buildCascade returns null for empty list`() {
        val result = NfcTagStore.buildCascade(emptyList())

        assertThat(result).isNull()
    }

    @Test
    fun `tagUidHex returns lowercase hex and null for null input`() {
        assertThat(NfcTagStore.tagUidHex(byteArrayOf(0x0A, 0x1B, 0x2C))).isEqualTo("0a1b2c")
        assertThat(NfcTagStore.tagUidHex(null)).isNull()
    }

    // ── markJustWritten / isJustWritten ───────────────────────────────────────

    @Test
    fun `isJustWritten returns true immediately after markJustWritten`() {
        store.markJustWritten("uid-fresh")

        assertThat(store.isJustWritten("uid-fresh")).isTrue()
    }

    @Test
    fun `isJustWritten returns false when cooldown has expired`() {
        store.markJustWritten("uid-expired")

        // cooldownMs = -1 means any real elapsed time exceeds the window
        assertThat(store.isJustWritten("uid-expired", cooldownMs = -1L)).isFalse()
    }

    @Test
    fun `isJustWritten returns false for uid never written`() {
        assertThat(store.isJustWritten("uid-never-written")).isFalse()
    }

    @Test
    fun `isJustWritten is case-insensitive`() {
        store.markJustWritten("CASE-WRITTEN")

        assertThat(store.isJustWritten("case-written")).isTrue()
    }

    // ── log tests ─────────────────────────────────────────────────────────────

    @Test
    fun `appendLogEntry persists an entry`() {
        val entry = NfcLogEntry(timestamp = now, tagName = "MyTag", action = "READ", success = true, message = "ok")
        store.appendLogEntry(entry)

        val loaded = store.loadLog()
        assertThat(loaded).hasSize(1)
        assertThat(loaded.first().tagName).isEqualTo("MyTag")
        assertThat(loaded.first().action).isEqualTo("READ")
        assertThat(loaded.first().success).isTrue()
    }

    @Test
    fun `loadLog returns entries newest first`() {
        val entry1 = NfcLogEntry(timestamp = now, tagName = "Tag1", action = "READ", success = true, message = "ok")
        val entry2 = NfcLogEntry(timestamp = now + 1000L, tagName = "Tag2", action = "WRITE", success = true, message = "written")
        store.appendLogEntry(entry1)
        store.appendLogEntry(entry2)

        val loaded = store.loadLog()
        assertThat(loaded).hasSize(2)
        assertThat(loaded[0].tagName).isEqualTo("Tag2")
        assertThat(loaded[1].tagName).isEqualTo("Tag1")
    }

    @Test
    fun `appendLogEntry prunes to 100 when exceeding max`() {
        repeat(105) { i ->
            store.appendLogEntry(NfcLogEntry(now + i, "Tag$i", "READ", true, "msg"))
        }

        val loaded = store.loadLog()
        assertThat(loaded).hasSize(100)
    }

    @Test
    fun `loadLog handles malformed JSON gracefully`() {
        prefs.edit().putString("nfccommunicator_log_v1", "not-valid-json").apply()

        val loaded = store.loadLog()
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
        store.appendLogEntry(entry)

        val loaded = store.loadLog()
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
        store.appendLogEntry(entry)

        val loaded = store.loadLog()
        assertThat(loaded.first().success).isFalse()
        assertThat(loaded.first().message).isEqualTo("Remote command not allowed")
    }

    @Test
    fun `clearLog removes all entries`() {
        store.appendLogEntry(NfcLogEntry(1L, "Tag", "READ", true, "ok"))
        store.clearLog()
        assertThat(store.loadLog()).isEmpty()
    }

    @Test
    fun `clearLog on empty log is a no-op`() {
        store.clearLog()
        assertThat(store.loadLog()).isEmpty()
    }
}
