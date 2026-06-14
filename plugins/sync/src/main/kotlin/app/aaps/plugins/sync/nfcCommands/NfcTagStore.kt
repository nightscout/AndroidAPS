package app.aaps.plugins.sync.nfcCommands

import app.aaps.core.interfaces.sharedPreferences.SP
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

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
            code: NfcCommandCode,
            params: JSONObject = JSONObject(),
        ): String {
            return JSONObject()
                .put(NfcJsonKeys.CODE, code.name)
                .put(NfcJsonKeys.PARAMS, params)
                .toString()
        }

        fun tagUidHex(id: ByteArray?): String? = id?.joinToString("") { "%02x".format(it) }
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
            val commandsJson = item.optJSONArray("commands") ?: JSONArray()
            val commands = (0 until commandsJson.length())
                .asSequence()
                .map { commandsJson.optString(it) }
                .filter { it.isNotBlank() }
                .toList()
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
