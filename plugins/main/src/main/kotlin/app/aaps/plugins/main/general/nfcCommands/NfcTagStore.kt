package app.aaps.plugins.main.general.nfcCommands

import androidx.annotation.StringRes
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.plugins.main.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

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
    val lastScannedAtMillis: Long? = null,
)

data class NfcLogEntry(
    val timestamp: Long,
    val tagName: String,
    val action: String,
    val success: Boolean,
    val message: String,
)

@Singleton
class NfcTagStore @Inject constructor(private val sp: SP) {

    companion object {
        const val MIME_TYPE: String = "application/vnd.app.aaps.command"
        private const val PREFS_TAGS = "nfccommunicator_created_tags_v1"
        private const val PREFS_LOG = "nfccommunicator_log_v1"
        private const val LOG_MAX_ENTRIES = 100

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

        fun tagUidHex(id: ByteArray?): String? = id?.joinToString("") { "%02x".format(it) }

        fun availableCommands(): List<NfcCommandTemplate> = commandTemplates

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
    }

    private val _logUpdates = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val logUpdates: Flow<Unit> = _logUpdates

    // uid (lowercase) → System.currentTimeMillis() at write time; cleared implicitly by expiry
    private val recentlyWrittenUids = mutableMapOf<String, Long>()

    fun markJustWritten(uid: String) {
        recentlyWrittenUids[uid.lowercase()] = System.currentTimeMillis()
    }

    fun isJustWritten(uid: String, cooldownMs: Long = 5_000L): Boolean {
        val writtenAt = recentlyWrittenUids[uid.lowercase()] ?: return false
        return System.currentTimeMillis() - writtenAt < cooldownMs
    }

    internal fun clearJustWrittenForTest() {
        recentlyWrittenUids.clear()
    }

    fun findTagByUid(uid: String): NfcCreatedTag? =
        loadCreatedTags().find { it.tagUid.equals(uid, ignoreCase = true) }

    fun loadCreatedTags(): List<NfcCreatedTag> {
        val raw = sp.getString(PREFS_TAGS, "[]")
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
                    lastScannedAtMillis = item.optLong("lastScannedAtMillis", 0L).takeIf { it > 0 },
                ),
            )
        }
        return tags.sortedByDescending { it.createdAtMillis }
    }

    fun saveCreatedTag(tag: NfcCreatedTag) {
        val updated = loadCreatedTags().filterNot { it.tagUid.equals(tag.tagUid, ignoreCase = true) }.toMutableList()
        updated.add(0, tag)
        saveCreatedTagList(updated)
    }

    fun deleteCreatedTag(tagUid: String) {
        val updated = loadCreatedTags().filterNot { it.tagUid.equals(tagUid, ignoreCase = true) }
        saveCreatedTagList(updated)
    }

    fun updateLastScanned(tagUid: String, millis: Long = System.currentTimeMillis()) {
        val tag = findTagByUid(tagUid) ?: return
        saveCreatedTag(tag.copy(lastScannedAtMillis = millis))
    }

    private fun saveCreatedTagList(tags: List<NfcCreatedTag>) {
        val array = JSONArray()
        tags.forEach { current ->
            val cmdsArray = JSONArray()
            current.commands.forEach { cmdsArray.put(it) }
            val obj = JSONObject()
                .put("tagUid", current.tagUid)
                .put("name", current.name)
                .put("commands", cmdsArray)
                .put("createdAtMillis", current.createdAtMillis)
            current.lastScannedAtMillis?.let { obj.put("lastScannedAtMillis", it) }
            array.put(obj)
        }
        sp.edit { putString(PREFS_TAGS, array.toString()) }
    }

    fun appendLogEntry(entry: NfcLogEntry) {
        val existing = loadLog().toMutableList()
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
        sp.edit { putString(PREFS_LOG, array.toString()) }
        _logUpdates.tryEmit(Unit)
    }

    fun loadLog(): List<NfcLogEntry> =
        try {
            val array = JSONArray(sp.getString(PREFS_LOG, "[]"))
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

    fun clearLog() {
        sp.edit { remove(PREFS_LOG) }
        _logUpdates.tryEmit(Unit)
    }

}
