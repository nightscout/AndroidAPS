package app.aaps.plugins.main.general.nfcCommands

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import android.preference.PreferenceManager
import app.aaps.plugins.main.R
import org.json.JSONArray
import org.json.JSONObject

data class NfcCommandTemplate(
    @StringRes val labelResId: Int,
    val commandPrefix: String,
    val fixedCommand: String? = null,
    @StringRes val argumentsHintResId: Int = 0,
    val requiresArguments: Boolean = false,
)

data class NfcCreatedTag(
    val tagUid: String,
    val name: String,
    val commands: List<String>,
    val createdAtMillis: Long,
)

data class NfcLogEntry(
    val timestamp: Long,
    val tagName: String,
    val action: String,
    val success: Boolean,
    val message: String,
)

object NfcTokenSupport {
    const val MIME_TYPE: String = "application/vnd.app.aaps.command"

    private const val PREFS_TAGS = "nfccommunicator_created_tags_v1"
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
     */
    fun tagUidHex(id: ByteArray?): String? = id?.joinToString("") { "%02x".format(it) }

    fun findTagByUid(context: Context, uid: String): NfcCreatedTag? =
        loadCreatedTags(context).find { it.tagUid.equals(uid, ignoreCase = true) }

    internal fun findTagByUid(prefs: SharedPreferences, uid: String): NfcCreatedTag? =
        loadCreatedTags(prefs).find { it.tagUid.equals(uid, ignoreCase = true) }

    fun loadCreatedTags(context: Context): List<NfcCreatedTag> = loadCreatedTags(PreferenceManager.getDefaultSharedPreferences(context))

    internal fun loadCreatedTags(prefs: SharedPreferences): List<NfcCreatedTag> {
        val raw = prefs.getString(PREFS_TAGS, "[]").orEmpty()
        val tags = mutableListOf<NfcCreatedTag>()
        val array = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val commandsJson = item.optJSONArray("commands")
            val commands = (0 until (commandsJson?.length() ?: 0)).map { commandsJson!!.optString(it) }.filter { it.isNotBlank() }
            if (commands.isEmpty()) continue
            val tagUid = item.optString("tagUid")
            if (tagUid.isBlank()) continue
            tags.add(
                NfcCreatedTag(
                    tagUid = tagUid,
                    name = item.optString("name"),
                    commands = commands,
                    createdAtMillis = item.optLong("createdAtMillis"),
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
        val updated = loadCreatedTags(prefs).filterNot { it.tagUid.equals(tag.tagUid, ignoreCase = true) }.toMutableList()
        updated.add(0, tag)
        saveCreatedTagList(prefs, updated)
    }

    fun deleteCreatedTag(
        context: Context,
        tagUid: String,
    ) {
        deleteCreatedTag(PreferenceManager.getDefaultSharedPreferences(context), tagUid)
    }

    internal fun deleteCreatedTag(
        prefs: SharedPreferences,
        tagUid: String,
    ) {
        val updated = loadCreatedTags(prefs).filterNot { it.tagUid.equals(tagUid, ignoreCase = true) }
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
                    .put("tagUid", current.tagUid)
                    .put("name", current.name)
                    .put("commands", cmdsArray)
                    .put("createdAtMillis", current.createdAtMillis),
            )
        }
        prefs.edit().putString(PREFS_TAGS, array.toString()).apply()
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
}
