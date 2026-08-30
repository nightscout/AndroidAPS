package app.aaps.plugins.sync.nfcCommands

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.SingleIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

/**
 * A tag the user has registered, and the commands it runs.
 *
 * [commands] are encoded [NfcCommand]s, kept as strings here because this is storage - the store does
 * not care what a command means, and a command that no longer decodes must not stop the tag itself
 * from being listed and edited.
 */
@Serializable
data class NfcCreatedTag(
    val tagUid: String,
    val name: String,
    val commands: List<String>,
    val createdAtMillis: Long,
    val lastScannedAtMillis: Long? = null,
)

@Serializable
data class NfcLogEntry(
    val timestamp: Long,
    val tagName: String,
    val action: String,
    val success: Boolean,
    val message: String,
)

@SingleIn(AppScope::class)
class NfcTagStore @Inject constructor(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) {

    companion object {

        const val MIME_TYPE: String = "application/vnd.app.aaps.command"
        private const val LOG_MAX_ENTRIES = 100

        fun tagUidHex(id: ByteArray?): String? = id?.joinToString("") { "%02x".format(it) }

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
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

    /**
     * Reads a stored list one entry at a time.
     *
     * Decoding the list in one go would mean a single bad entry throwing away every other entry, which
     * is what the previous `catch (Exception) { emptyList() }` did - a user's whole tag list or history
     * disappearing with no error and no log line. Each element is decoded on its own instead, so a bad
     * one is dropped and named while the rest survive.
     */
    private fun <T> loadList(raw: String, serializer: KSerializer<T>, what: String): List<T> {
        if (raw.isBlank()) return emptyList()
        val elements = runCatching { json.parseToJsonElement(raw).jsonArray }.getOrElse {
            aapsLogger.error(LTag.NFC, "Stored $what is not a JSON array, ignoring it: ${it.message}")
            return emptyList()
        }
        return elements.mapIndexedNotNull { index, element ->
            runCatching { json.decodeFromJsonElement(serializer, element) }.getOrElse {
                aapsLogger.error(LTag.NFC, "Dropping $what entry $index, it did not decode: ${it.message}")
                null
            }
        }
    }

    private fun <T> saveList(key: StringNonKey, values: List<T>, serializer: KSerializer<T>) {
        preferences.put(key, json.encodeToString(ListSerializer(serializer), values))
    }

    fun findTagByUid(uid: String): NfcCreatedTag? =
        loadCreatedTags().find { it.tagUid.equals(uid, ignoreCase = true) }

    fun loadCreatedTags(): List<NfcCreatedTag> =
        loadList(preferences.get(StringNonKey.NfcCreatedTags), NfcCreatedTag.serializer(), "tag list")
            .filter { it.tagUid.isNotBlank() && it.commands.any { command -> command.isNotBlank() } }
            .sortedByDescending { it.createdAtMillis }

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
        saveList(StringNonKey.NfcCreatedTags, tags, NfcCreatedTag.serializer())
    }

    fun appendLogEntry(entry: NfcLogEntry) {
        val pruned = (listOf(entry) + loadLog()).take(LOG_MAX_ENTRIES)
        saveList(StringNonKey.NfcLog, pruned, NfcLogEntry.serializer())
        _logUpdates.tryEmit(Unit)
    }

    fun loadLog(): List<NfcLogEntry> =
        loadList(preferences.get(StringNonKey.NfcLog), NfcLogEntry.serializer(), "log")

    fun clearLog() {
        preferences.remove(StringNonKey.NfcLog)
        _logUpdates.tryEmit(Unit)
    }
}
