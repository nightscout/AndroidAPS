package app.aaps.plugins.main.general.nfcCommands

import app.aaps.shared.tests.SharedPreferencesMock
import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

class NfcTokenSupportTest {
    private lateinit var prefs: SharedPreferencesMock
    private val now = 1_700_000_000_000L
    private val secret = "0123456789abcdef0123456789abcdef".toByteArray()

    @BeforeEach
    fun setup() {
        prefs = SharedPreferencesMock()
    }

    private fun makeTag(
        id: String = "tag-id-1",
        expiresAtMillis: Long = now + NfcTokenSupport.ONE_YEAR_MILLIS,
    ): NfcCreatedTag =
        NfcCreatedTag(
            id = id,
            name = "Test Tag",
            commands = listOf("LOOP STOP"),
            token = "dummy.token.value",
            createdAtMillis = now,
            expiresAtMillis = expiresAtMillis,
        )

    // ── blacklist tests ────────────────────────────────────────────────────────

    @Test
    fun `blacklistTag removes tag from created list and adds to blacklist`() {
        val tag = makeTag()
        NfcTokenSupport.saveCreatedTag(prefs, tag)
        assertThat(NfcTokenSupport.loadCreatedTags(prefs)).hasSize(1)

        NfcTokenSupport.blacklistTag(prefs, tag)

        assertThat(NfcTokenSupport.loadCreatedTags(prefs)).isEmpty()
        assertThat(NfcTokenSupport.loadBlacklistedTokens(prefs, now + 1_000L)).hasSize(1)
        assertThat(NfcTokenSupport.loadBlacklistedTokens(prefs, now + 1_000L).first().tokenId).isEqualTo(tag.id)
    }

    @Test
    fun `isBlacklisted returns true immediately after blacklisting`() {
        val tag = makeTag()
        NfcTokenSupport.blacklistTag(prefs, tag)

        assertThat(NfcTokenSupport.isBlacklisted(prefs, tag.id, now + 1_000L)).isTrue()
    }

    @Test
    fun `isBlacklisted returns false for unknown tokenId`() {
        assertThat(NfcTokenSupport.isBlacklisted(prefs, "unknown-id", now)).isFalse()
    }

    @Test
    fun `isBlacklisted returns false and prunes expired blacklist entries`() {
        val pastExpiry = now - 1_000L
        val expiredTag = makeTag(id = "expired-id", expiresAtMillis = pastExpiry)
        NfcTokenSupport.blacklistTag(prefs, expiredTag)

        val result = NfcTokenSupport.isBlacklisted(prefs, "expired-id", now)

        assertThat(result).isFalse()
        assertThat(NfcTokenSupport.loadBlacklistedTokens(prefs, now)).isEmpty()
    }

    @Test
    fun `blacklistTag does not remove other tags from created list`() {
        val tag1 = makeTag(id = "id-1")
        val tag2 = makeTag(id = "id-2")
        NfcTokenSupport.saveCreatedTag(prefs, tag1)
        NfcTokenSupport.saveCreatedTag(prefs, tag2)

        NfcTokenSupport.blacklistTag(prefs, tag1)

        val remaining = NfcTokenSupport.loadCreatedTags(prefs)
        assertThat(remaining).hasSize(1)
        assertThat(remaining.first().id).isEqualTo("id-2")
    }

    // ── multi-command token encoding/decoding tests ────────────────────────────

    @Test
    fun `issueToken with list encodes cmds array in JWT payload`() {
        val commands = listOf("TARGET MEAL", "BOLUS 2.0 MEAL")
        val issued = NfcTokenSupport.issueToken(secret, commands, now)

        val parts = issued.token.split(".")
        assertThat(parts).hasSize(3)
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
        assertThat(payload.has("cmds")).isTrue()
        val cmds = payload.getJSONArray("cmds")
        assertThat(cmds.length()).isEqualTo(2)
        assertThat(cmds.getString(0)).isEqualTo("TARGET MEAL")
        assertThat(cmds.getString(1)).isEqualTo("BOLUS 2.0 MEAL")
    }

    @Test
    fun `verifyToken with cmds array returns multi-command Success`() {
        val commands = listOf("TARGET MEAL", "BOLUS 2.0 MEAL")
        val issued = NfcTokenSupport.issueToken(secret, commands, now)

        val result = NfcTokenSupport.verifyToken(secret, issued.token, now + 1_000L)

        assertThat(result).isInstanceOf(NfcTokenVerificationResult.Success::class.java)
        result as NfcTokenVerificationResult.Success
        assertThat(result.commands).isEqualTo(commands)
    }

    @Test
    fun `verifyToken with legacy cmd string returns single-command Success`() {
        // Build a legacy token with "cmd" field (not "cmds")
        val legacyToken = buildLegacyToken(secret, "LOOP STOP", now)

        val result = NfcTokenSupport.verifyToken(secret, legacyToken, now + 1_000L)

        assertThat(result).isInstanceOf(NfcTokenVerificationResult.Success::class.java)
        result as NfcTokenVerificationResult.Success
        assertThat(result.commands).isEqualTo(listOf("LOOP STOP"))
    }

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
    fun `loadCreatedTags migrates legacy single command field`() {
        // Write old-format JSON with "command" field
        val legacyJson = JSONArray()
        legacyJson.put(
            JSONObject()
                .put("id", "legacy-id")
                .put("name", "Legacy Tag")
                .put("command", "LOOP STOP")
                .put("token", "dummy.token.val")
                .put("createdAtMillis", now)
                .put("expiresAtMillis", now + NfcTokenSupport.ONE_YEAR_MILLIS),
        )
        prefs.edit().putString("nfccommunicator_created_tags_v1", legacyJson.toString()).apply()

        val tags = NfcTokenSupport.loadCreatedTags(prefs)

        assertThat(tags).hasSize(1)
        assertThat(tags.first().commands).isEqualTo(listOf("LOOP STOP"))
    }

    // ── isExpiringSoon tests ───────────────────────────────────────────────────

    @Test
    fun `isExpiringSoon returns true when delta equals THIRTY_DAYS_MILLIS`() {
        val tag =
            NfcCreatedTag(
                "id",
                "n",
                listOf("LOOP STOP"),
                "tok",
                now,
                now + NfcTokenSupport.THIRTY_DAYS_MILLIS,
            )

        assertThat(tag.isExpiringSoon(now)).isTrue()
    }

    @Test
    fun `isExpiringSoon returns false when delta is one ms more than THIRTY_DAYS_MILLIS`() {
        val tag =
            NfcCreatedTag(
                "id",
                "n",
                listOf("LOOP STOP"),
                "tok",
                now,
                now + NfcTokenSupport.THIRTY_DAYS_MILLIS + 1L,
            )

        assertThat(tag.isExpiringSoon(now)).isFalse()
    }

    @Test
    fun `isExpiringSoon returns false when already expired`() {
        // expiresAtMillis == now  →  isExpired returns true  →  isExpiringSoon false
        val tag = NfcCreatedTag("id", "n", listOf("LOOP STOP"), "tok", now - 1000L, now)

        assertThat(tag.isExpiringSoon(now)).isFalse()
    }

    @Test
    fun `isExpiringSoon returns false when well past expiry`() {
        val tag = NfcCreatedTag("id", "n", listOf("LOOP STOP"), "tok", now - 2000L, now - 1000L)

        assertThat(tag.isExpiringSoon(now)).isFalse()
    }

    @Test
    fun `isExpiringSoon returns true with one ms remaining`() {
        val tag = NfcCreatedTag("id", "n", listOf("LOOP STOP"), "tok", now, now + 1L)

        assertThat(tag.isExpiringSoon(now)).isTrue()
    }

    // ── replaceTag tests ───────────────────────────────────────────────────────

    @Test
    fun `replaceTag removes old entry and prepends new entry preserving others`() {
        val oldTag = makeTag(id = "old-id")
        val other = makeTag(id = "other-id")
        NfcTokenSupport.saveCreatedTag(prefs, oldTag)
        NfcTokenSupport.saveCreatedTag(prefs, other)
        val newTag = makeTag(id = "new-id")

        NfcTokenSupport.replaceTag(prefs, "old-id", newTag)

        val tags = NfcTokenSupport.loadCreatedTags(prefs)
        assertThat(tags).hasSize(2)
        assertThat(tags.first().id).isEqualTo("new-id") // prepended
        assertThat(tags.none { it.id == "old-id" }).isTrue() // old removed
        assertThat(tags.any { it.id == "other-id" }).isTrue() // other kept
    }

    @Test
    fun `replaceTag does not modify the blacklist`() {
        NfcTokenSupport.saveCreatedTag(prefs, makeTag(id = "old-id"))
        // Add a blacklist entry
        NfcTokenSupport.blacklistTag(
            prefs,
            makeTag(id = "bl-id", expiresAtMillis = now + 1_000L),
        )

        NfcTokenSupport.replaceTag(prefs, "old-id", makeTag(id = "new-id"))

        val blacklisted = NfcTokenSupport.loadBlacklistedTokens(prefs, now)
        assertThat(blacklisted).hasSize(1)
        assertThat(blacklisted.first().tokenId).isEqualTo("bl-id")
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

    // ── UID binding tests ──────────────────────────────────────────────────────

    @Test
    fun `issueToken with tagUid includes tid claim in JWT payload`() {
        val issued = NfcTokenSupport.issueToken(secret, listOf("LOOP STOP"), now, tagUid = "aabbccdd")

        val parts = issued.token.split(".")
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
        assertThat(payload.optString("tid")).isEqualTo("aabbccdd")
    }

    @Test
    fun `issueToken without tagUid omits tid claim`() {
        val issued = NfcTokenSupport.issueToken(secret, listOf("LOOP STOP"), now)

        val parts = issued.token.split(".")
        val payloadBytes = Base64.getUrlDecoder().decode(parts[1])
        val payload = JSONObject(String(payloadBytes, Charsets.UTF_8))
        assertThat(payload.has("tid")).isFalse()
    }

    @Test
    fun `verifyToken accepts token when tagUid matches tid claim`() {
        val issued = NfcTokenSupport.issueToken(secret, listOf("LOOP STOP"), now, tagUid = "aabbccdd")

        val result = NfcTokenSupport.verifyToken(secret, issued.token, now + 1_000L, tagUid = "aabbccdd")

        assertThat(result).isInstanceOf(NfcTokenVerificationResult.Success::class.java)
    }

    @Test
    fun `verifyToken rejects token when tagUid does not match tid claim`() {
        val issued = NfcTokenSupport.issueToken(secret, listOf("LOOP STOP"), now, tagUid = "aabbccdd")

        val result = NfcTokenSupport.verifyToken(secret, issued.token, now + 1_000L, tagUid = "deadbeef")

        assertThat(result).isInstanceOf(NfcTokenVerificationResult.Failure::class.java)
        result as NfcTokenVerificationResult.Failure
        assertThat(result.reason).isEqualTo("Tag UID mismatch")
    }

    @Test
    fun `verifyToken rejects token with tid claim when tagUid is null`() {
        val issued = NfcTokenSupport.issueToken(secret, listOf("LOOP STOP"), now, tagUid = "aabbccdd")

        val result = NfcTokenSupport.verifyToken(secret, issued.token, now + 1_000L, tagUid = null)

        assertThat(result).isInstanceOf(NfcTokenVerificationResult.Failure::class.java)
        result as NfcTokenVerificationResult.Failure
        assertThat(result.reason).isEqualTo("Tag UID mismatch")
    }

    @Test
    fun `verifyToken accepts legacy token with no tid claim even when tagUid is provided`() {
        // Legacy tokens (no tid claim) pass through unconditionally to preserve backward compatibility.
        val issued = NfcTokenSupport.issueToken(secret, listOf("LOOP STOP"), now, tagUid = null)

        val result = NfcTokenSupport.verifyToken(secret, issued.token, now + 1_000L, tagUid = "anyuid")

        assertThat(result).isInstanceOf(NfcTokenVerificationResult.Success::class.java)
    }

    @Test
    fun `verifyToken tid matching is case-insensitive`() {
        val issued = NfcTokenSupport.issueToken(secret, listOf("LOOP STOP"), now, tagUid = "AABBCCDD")

        val result = NfcTokenSupport.verifyToken(secret, issued.token, now + 1_000L, tagUid = "aabbccdd")

        assertThat(result).isInstanceOf(NfcTokenVerificationResult.Success::class.java)
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private fun buildLegacyToken(
        secret: ByteArray,
        command: String,
        nowMillis: Long,
    ): String {
        val expiresAtMillis = nowMillis + NfcTokenSupport.ONE_YEAR_MILLIS
        val header =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    JSONObject()
                        .put("alg", "HS256")
                        .put("typ", "JWT")
                        .toString()
                        .toByteArray(),
                )
        val payload =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    JSONObject()
                        .put("jti", "legacy-token-id")
                        .put("cmd", command)
                        .put("iat", nowMillis / 1000L)
                        .put("exp", expiresAtMillis / 1000L)
                        .toString()
                        .toByteArray(),
                )
        val signingInput = "$header.$payload"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(secret, "HmacSHA256"))
        val sig = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.toByteArray()))
        return "$signingInput.$sig"
    }
}
