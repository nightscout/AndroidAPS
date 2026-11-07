package app.aaps.plugins.sync.log

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sync.SyncLogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement

/**
 * The log a sync plugin shows on its screen, newest first, at most [maxEntries] lines. Nightscout, xDrip
 * and Tidepool each own one, and [SyncLogList] shows it. Every line also goes to the debug log under [tag].
 */
class SyncLogBuffer(
    private val aapsLogger: AAPSLogger,
    private val tag: LTag,
    private val maxEntries: Int = 100
) {

    private val _entries = MutableStateFlow<List<SyncLogEntry>>(emptyList())

    /** The lines, newest first */
    val entries: StateFlow<List<SyncLogEntry>> = _entries.asStateFlow()

    fun add(action: String, text: String? = null, json: JsonElement? = null) {
        // Outside update(): its block can run more than once when two threads add at the same time
        aapsLogger.debug(tag, if (text == null) action else "$action $text")
        val entry = SyncLogEntry(action = action, text = text, json = json)
        _entries.update { current -> listOf(entry) + current.take(maxEntries - 1) }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
