package app.aaps.plugins.main.general.nfcCommands

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.preference.PreferenceManager
import app.aaps.plugins.main.R
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class NfcCommandTemplate(
    @StringRes val labelResId: Int,
    val commandPrefix: String,
    val fixedCommand: String? = null,
    @StringRes val argumentsHintResId: Int = 0,
    val requiresArguments: Boolean = false,
)

data class NfcCreatedTag(
    val id: String,
    val name: String,
    val commands: List<String>,
    val token: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis

    fun isExpiringSoon(nowMillis: Long): Boolean =
        !isExpired(nowMillis) && (expiresAtMillis - nowMillis) <= NfcTokenSupport.THIRTY_DAYS_MILLIS
}

data class NfcBlacklistEntry(
    val tokenId: String,
    val expiresAtMillis: Long,
)

data class NfcLogEntry(
    val timestamp: Long,
    val tagName: String,
    val action: String,
    val success: Boolean,
    val message: String,
)

data class NfcIssuedToken(
    val token: String,
    val tokenId: String,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
)

sealed class NfcTokenVerificationResult {
    data class Success(
        val commands: List<String>,
        val tokenId: String,
        val issuedAtMillis: Long,
        val expiresAtMillis: Long,
    ) : NfcTokenVerificationResult()

    data class Failure(
        val reason: String,
    ) : NfcTokenVerificationResult()
}

object NfcTokenSupport {
    const val MIME_TYPE: String = "application/vnd.app.aaps.command"
    const val ONE_YEAR_MILLIS: Long = 365L * 24L * 60L * 60L * 1000L
    const val THIRTY_DAYS_MILLIS: Long = 30L * 24L * 60L * 60L * 1000L

    private const val PREFS_SECRET = "nfccommunicator_jwt_secret_v1"
    private const val PREFS_TAGS = "nfccommunicator_created_tags_v1"
    private const val PREFS_BLACKLIST = "nfccommunicator_blacklisted_tokens_v1"
    private const val PREFS_LOG = "nfccommunicator_log_v1"
    private const val LOG_MAX_ENTRIES = 100

    private val commandTemplates =
        listOf(
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_loop_stop, commandPrefix = "LOOP STOP"),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_loop_resume, commandPrefix = "LOOP RESUME"),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_loop_closed, commandPrefix = "LOOP CLOSED"),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_loop_lgs, commandPrefix = "LOOP LGS"),
            NfcCommandTemplate(
                labelResId = R.string.nfccommands_cmd_loop_suspend,
                commandPrefix = "LOOP SUSPEND",
                argumentsHintResId = R.string.nfccommands_hint_minutes,
                requiresArguments = true,
            ),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_aapsclient_restart, commandPrefix = "AAPSCLIENT RESTART"),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_pump_connect, commandPrefix = "PUMP CONNECT"),
            NfcCommandTemplate(
                labelResId = R.string.nfccommands_cmd_pump_disconnect,
                commandPrefix = "PUMP DISCONNECT",
                argumentsHintResId = R.string.nfccommands_hint_minutes,
                requiresArguments = true,
            ),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_basal_stop, commandPrefix = "BASAL STOP"),
            NfcCommandTemplate(
                labelResId = R.string.nfccommands_cmd_basal_absolute,
                commandPrefix = "BASAL",
                argumentsHintResId = R.string.nfccommands_hint_basal_abs,
                requiresArguments = true,
            ),
            NfcCommandTemplate(
                labelResId = R.string.nfccommands_cmd_basal_percent,
                commandPrefix = "BASAL",
                argumentsHintResId = R.string.nfccommands_hint_basal_pct,
                requiresArguments = true,
            ),
            NfcCommandTemplate(
                labelResId = R.string.nfccommands_cmd_bolus,
                commandPrefix = "BOLUS",
                argumentsHintResId = R.string.nfccommands_hint_bolus,
                requiresArguments = true,
            ),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_extended_stop, commandPrefix = "EXTENDED STOP"),
            NfcCommandTemplate(
                labelResId = R.string.nfccommands_cmd_extended_bolus,
                commandPrefix = "EXTENDED",
                argumentsHintResId = R.string.nfccommands_hint_extended,
                requiresArguments = true,
            ),
            NfcCommandTemplate(
                labelResId = R.string.nfccommands_cmd_profile_switch,
                commandPrefix = "PROFILE",
                argumentsHintResId = R.string.nfccommands_hint_profile,
                requiresArguments = true,
            ),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_target_meal, commandPrefix = "TARGET MEAL"),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_target_activity, commandPrefix = "TARGET ACTIVITY"),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_target_hypo, commandPrefix = "TARGET HYPO"),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_target_stop, commandPrefix = "TARGET STOP"),
            NfcCommandTemplate(
                labelResId = R.string.nfccommands_cmd_carbs,
                commandPrefix = "CARBS",
                argumentsHintResId = R.string.nfccommands_hint_grams,
                requiresArguments = true,
            ),
            NfcCommandTemplate(labelResId = R.string.nfccommands_cmd_restart_aaps, commandPrefix = "RESTART"),
        )

    fun availableCommands(): List<NfcCommandTemplate> = commandTemplates

    fun buildCommand(
        template: NfcCommandTemplate,
        args: String,
    ): String? {
        template.fixedCommand?.let { return it }
        val cleanArgs = args.trim()
        if (template.requiresArguments && cleanArgs.isEmpty()) return null
        return if (template.requiresArguments) "${template.commandPrefix} $cleanArgs" else template.commandPrefix
    }

    fun buildCascade(steps: List<Pair<NfcCommandTemplate, String>>): List<String>? {
        if (steps.isEmpty()) return null
        val result = mutableListOf<String>()
        for ((template, args) in steps) {
            val cmd = buildCommand(template, args) ?: return null
            result.add(cmd)
        }
        return result
    }

    /**
     * Encodes a raw NFC tag UID byte array as a lowercase hex string, or returns null if [id] is null.
     * Used to produce and verify the `tid` claim in issued tokens.
     */
    fun tagUidHex(id: ByteArray?): String? = id?.joinToString("") { "%02x".format(it) }

    fun issueToken(
        context: Context,
        command: String,
        nowMillis: Long = System.currentTimeMillis(),
        tagUid: String? = null,
    ): NfcIssuedToken = issueToken(secretBytes(context), listOf(command), nowMillis, tagUid)

    fun issueToken(
        context: Context,
        commands: List<String>,
        nowMillis: Long = System.currentTimeMillis(),
        tagUid: String? = null,
    ): NfcIssuedToken = issueToken(secretBytes(context), commands, nowMillis, tagUid)

    internal fun issueToken(
        secret: ByteArray,
        command: String,
        nowMillis: Long,
        tagUid: String? = null,
    ): NfcIssuedToken = issueToken(secret, listOf(command), nowMillis, tagUid)

    internal fun issueToken(
        secret: ByteArray,
        commands: List<String>,
        nowMillis: Long,
        tagUid: String? = null,
    ): NfcIssuedToken {
        val tokenId = UUID.randomUUID().toString()
        val expiresAtMillis = nowMillis + ONE_YEAR_MILLIS
        val headerJson =
            JSONObject()
                .put("alg", "HS256")
                .put("typ", "JWT")
        val cmdsArray = JSONArray()
        commands.forEach { cmdsArray.put(it) }
        val payloadJson =
            JSONObject()
                .put("jti", tokenId)
                .put("cmds", cmdsArray)
                .put("iat", nowMillis / 1000L)
                .put("exp", expiresAtMillis / 1000L)
        if (tagUid != null) payloadJson.put("tid", tagUid)
        val encodedHeader = encodeSegment(headerJson.toString().toByteArray(StandardCharsets.UTF_8))
        val encodedPayload = encodeSegment(payloadJson.toString().toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$encodedHeader.$encodedPayload"
        val signature = encodeSegment(sign(signingInput, secret))
        return NfcIssuedToken(
            token = "$signingInput.$signature",
            tokenId = tokenId,
            issuedAtMillis = nowMillis,
            expiresAtMillis = expiresAtMillis,
        )
    }

    fun verifyToken(
        context: Context,
        token: String,
        nowMillis: Long = System.currentTimeMillis(),
        tagUid: String? = null,
    ): NfcTokenVerificationResult = verifyToken(secretBytes(context), token, nowMillis, tagUid)

    internal fun verifyToken(
        secret: ByteArray,
        token: String,
        nowMillis: Long,
        tagUid: String? = null,
    ): NfcTokenVerificationResult {
        val parts = token.split(".")
        if (parts.size != 3) return NfcTokenVerificationResult.Failure("Malformed token")

        val signingInput = "${parts[0]}.${parts[1]}"
        val expectedSignature = sign(signingInput, secret)
        val actualSignature = decodeSegment(parts[2]) ?: return NfcTokenVerificationResult.Failure("Malformed signature")
        if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
            return NfcTokenVerificationResult.Failure("Invalid token signature")
        }

        val payloadBytes = decodeSegment(parts[1]) ?: return NfcTokenVerificationResult.Failure("Malformed payload")
        val payload =
            runCatching { JSONObject(String(payloadBytes, StandardCharsets.UTF_8)) }.getOrNull()
                ?: return NfcTokenVerificationResult.Failure("Malformed payload")

        val tokenId = payload.optString("jti")
        val issuedAtMillis = payload.optLong("iat") * 1000L
        val expiresAtMillis = payload.optLong("exp") * 1000L

        // Decode commands: try "cmds" (JSONArray) first, fall back to "cmd" (String) for legacy tokens
        val cmdsArray = payload.optJSONArray("cmds")
        val commands: List<String>
        if (cmdsArray != null && cmdsArray.length() > 0) {
            commands = (0 until cmdsArray.length()).map { cmdsArray.optString(it) }.filter { it.isNotBlank() }
            if (commands.isEmpty()) return NfcTokenVerificationResult.Failure("Missing token claims")
        } else {
            val cmd = payload.optString("cmd")
            if (cmd.isBlank()) return NfcTokenVerificationResult.Failure("Missing token claims")
            commands = listOf(cmd)
        }

        if (tokenId.isBlank() || expiresAtMillis == 0L) {
            return NfcTokenVerificationResult.Failure("Missing token claims")
        }
        if (issuedAtMillis == 0L || issuedAtMillis > expiresAtMillis) {
            return NfcTokenVerificationResult.Failure("Invalid token timestamps")
        }
        if (nowMillis >= expiresAtMillis) {
            return NfcTokenVerificationResult.Failure("Token expired")
        }

        // UID binding: if the token carries a tid claim the physical tag UID must match.
        // Tokens written without a tid claim (legacy) pass through unconditionally.
        val claimedUid = payload.optString("tid").takeIf { it.isNotEmpty() }
        if (claimedUid != null) {
            if (tagUid == null || !claimedUid.equals(tagUid, ignoreCase = true)) {
                return NfcTokenVerificationResult.Failure("Tag UID mismatch")
            }
        }

        return NfcTokenVerificationResult.Success(
            commands = commands,
            tokenId = tokenId,
            issuedAtMillis = issuedAtMillis,
            expiresAtMillis = expiresAtMillis,
        )
    }

    fun loadCreatedTags(context: Context): List<NfcCreatedTag> = loadCreatedTags(PreferenceManager.getDefaultSharedPreferences(context))

    internal fun loadCreatedTags(prefs: SharedPreferences): List<NfcCreatedTag> {
        val raw = prefs.getString(PREFS_TAGS, "[]").orEmpty()
        val tags = mutableListOf<NfcCreatedTag>()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            // Check "commands" (JSONArray) first; fall back to legacy "command" (String)
            val commandsJson = item.optJSONArray("commands")
            val commands =
                if (commandsJson != null && commandsJson.length() > 0) {
                    (0 until commandsJson.length()).map { commandsJson.optString(it) }
                } else {
                    val cmd = item.optString("command")
                    if (cmd.isNotBlank()) listOf(cmd) else emptyList()
                }
            tags.add(
                NfcCreatedTag(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    commands = commands,
                    token = item.optString("token"),
                    createdAtMillis = item.optLong("createdAtMillis"),
                    expiresAtMillis = item.optLong("expiresAtMillis"),
                ),
            )
        }
        return tags.sortedByDescending { it.createdAtMillis }
    }

    fun saveCreatedTag(
        context: Context,
        tag: NfcCreatedTag,
    ) {
        saveCreatedTag(PreferenceManager.getDefaultSharedPreferences(context), tag)
    }

    internal fun saveCreatedTag(
        prefs: SharedPreferences,
        tag: NfcCreatedTag,
    ) {
        val updated = loadCreatedTags(prefs).filterNot { it.id == tag.id }.toMutableList()
        updated.add(0, tag)
        saveCreatedTagList(prefs, updated)
    }

    fun replaceTag(
        context: Context,
        oldId: String,
        newTag: NfcCreatedTag,
    ) {
        replaceTag(PreferenceManager.getDefaultSharedPreferences(context), oldId, newTag)
    }

    internal fun replaceTag(
        prefs: SharedPreferences,
        oldId: String,
        newTag: NfcCreatedTag,
    ) {
        val updated = loadCreatedTags(prefs).filterNot { it.id == oldId }.toMutableList()
        updated.add(0, newTag)
        saveCreatedTagList(prefs, updated)
    }

    private fun saveCreatedTagList(
        prefs: SharedPreferences,
        tags: List<NfcCreatedTag>,
    ) {
        val array = JSONArray()
        tags.forEach { current ->
            val cmdsArray = JSONArray()
            current.commands.forEach { cmdsArray.put(it) }
            array.put(
                JSONObject()
                    .put("id", current.id)
                    .put("name", current.name)
                    .put("commands", cmdsArray)
                    .put("token", current.token)
                    .put("createdAtMillis", current.createdAtMillis)
                    .put("expiresAtMillis", current.expiresAtMillis),
            )
        }
        prefs.edit().putString(PREFS_TAGS, array.toString()).apply()
    }

    fun loadBlacklistedTokens(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<NfcBlacklistEntry> = loadBlacklistedTokens(PreferenceManager.getDefaultSharedPreferences(context), nowMillis)

    internal fun loadBlacklistedTokens(
        prefs: SharedPreferences,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<NfcBlacklistEntry> {
        val raw = prefs.getString(PREFS_BLACKLIST, "[]").orEmpty()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        val entries = mutableListOf<NfcBlacklistEntry>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val entry =
                NfcBlacklistEntry(
                    tokenId = item.optString("tokenId"),
                    expiresAtMillis = item.optLong("expiresAtMillis"),
                )
            if (entry.expiresAtMillis > nowMillis) entries.add(entry)
        }
        return entries
    }

    fun blacklistTag(
        context: Context,
        tag: NfcCreatedTag,
    ) {
        blacklistTag(PreferenceManager.getDefaultSharedPreferences(context), tag)
    }

    internal fun blacklistTag(
        prefs: SharedPreferences,
        tag: NfcCreatedTag,
    ) {
        val updatedTags = loadCreatedTags(prefs).filterNot { it.id == tag.id }
        saveCreatedTagList(prefs, updatedTags)

        val existing = loadBlacklistedTokens(prefs).toMutableList()
        existing.add(NfcBlacklistEntry(tag.id, tag.expiresAtMillis))
        val array = JSONArray()
        existing.forEach { entry ->
            array.put(
                JSONObject()
                    .put("tokenId", entry.tokenId)
                    .put("expiresAtMillis", entry.expiresAtMillis),
            )
        }
        prefs.edit().putString(PREFS_BLACKLIST, array.toString()).apply()
    }

    fun clearBlacklist(context: Context) {
        clearBlacklist(PreferenceManager.getDefaultSharedPreferences(context))
    }

    internal fun clearBlacklist(prefs: SharedPreferences) {
        prefs.edit().putString(PREFS_BLACKLIST, "[]").apply()
    }

    fun isBlacklisted(
        context: Context,
        tokenId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean = isBlacklisted(PreferenceManager.getDefaultSharedPreferences(context), tokenId, nowMillis)

    internal fun isBlacklisted(
        prefs: SharedPreferences,
        tokenId: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val active = loadBlacklistedTokens(prefs, nowMillis)
        val pruned = JSONArray()
        active.forEach { entry ->
            pruned.put(
                JSONObject()
                    .put("tokenId", entry.tokenId)
                    .put("expiresAtMillis", entry.expiresAtMillis),
            )
        }
        prefs.edit().putString(PREFS_BLACKLIST, pruned.toString()).apply()
        return active.any { it.tokenId == tokenId }
    }

    fun appendLogEntry(
        context: Context,
        entry: NfcLogEntry,
    ) {
        appendLogEntry(PreferenceManager.getDefaultSharedPreferences(context), entry)
    }

    internal fun appendLogEntry(
        prefs: SharedPreferences,
        entry: NfcLogEntry,
    ) {
        val existing = loadLog(prefs).toMutableList()
        existing.add(0, entry)
        val pruned = existing.take(LOG_MAX_ENTRIES)
        val array = JSONArray()
        pruned.forEach { e ->
            array.put(
                JSONObject()
                    .put("timestamp", e.timestamp)
                    .put("tagName", e.tagName)
                    .put("action", e.action)
                    .put("success", e.success)
                    .put("message", e.message),
            )
        }
        prefs.edit().putString(PREFS_LOG, array.toString()).apply()
    }

    fun loadLog(context: Context): List<NfcLogEntry> = loadLog(PreferenceManager.getDefaultSharedPreferences(context))

    internal fun loadLog(prefs: SharedPreferences): List<NfcLogEntry> =
        try {
            val array = JSONArray(prefs.getString(PREFS_LOG, "[]") ?: "[]")
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                NfcLogEntry(
                    timestamp = o.getLong("timestamp"),
                    tagName = o.getString("tagName"),
                    action = o.getString("action"),
                    success = o.getBoolean("success"),
                    message = o.getString("message"),
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

    private fun secretBytes(context: Context): ByteArray {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val existing = prefs.getString(PREFS_SECRET, null)
        if (!existing.isNullOrBlank()) {
            decodeSegment(existing)?.let { return it }
        }
        val secret = ByteArray(32)
        SecureRandom().nextBytes(secret)
        prefs.edit().putString(PREFS_SECRET, encodeSegment(secret)).apply()
        return secret
    }

    private fun sign(
        signingInput: String,
        secret: ByteArray,
    ): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(signingInput.toByteArray(StandardCharsets.UTF_8))
    }

    private fun encodeSegment(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decodeSegment(value: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(value) }.getOrNull()
}
