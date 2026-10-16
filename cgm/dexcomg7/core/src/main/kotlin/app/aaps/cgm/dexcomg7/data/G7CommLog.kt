package app.aaps.cgm.dexcomg7.data

import android.content.Context
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The last 24 hours of what happened between the phone and the sensor, for the log screen.
 *
 * A tester who says "it got stuck" can only be helped when these lines are visible outside a
 * debugger, and a problem at night is only seen in the morning. So the lines are also kept in a file
 * in the app's own storage, and survive a restart. Lines older than 24 hours are dropped. Everything
 * also goes to the AAPS log. Lines never carry the pairing code or a key.
 */
@SingleIn(AppScope::class)
@Inject
class G7CommLog(
    private val context: Context,
    private val aapsLogger: AAPSLogger
) {

    class Entry(val time: Long, val text: String)

    private val lock = Any()
    private val file: File by lazy { File(context.filesDir, FILE_NAME) }
    private var lastCompactAt = 0L
    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    private var loaded = false

    val entries: StateFlow<List<Entry>>
        get() {
            ensureLoaded()
            return _entries.asStateFlow()
        }

    fun add(text: String) {
        aapsLogger.debug(LTag.BGSOURCE, "DexcomG7: $text")
        ensureLoaded()
        val now = System.currentTimeMillis()
        // One line of text per entry: the file format is one line each.
        val entry = Entry(now, text.replace('\n', ' '))
        synchronized(lock) {
            _entries.value = keep(_entries.value + entry, now)
            runCatching { file.appendText(format(entry)) }
                .onFailure { aapsLogger.error(LTag.BGSOURCE, "DexcomG7: could not write the log file", it) }
            // The file only grows between compactions; rewrite it now and then without the old lines.
            if (now - lastCompactAt > COMPACT_INTERVAL_MS) compact(now)
        }
    }

    fun clear() = synchronized(lock) {
        _entries.value = emptyList()
        runCatching { file.delete() }
    }

    /** All lines as text, oldest first, with the date and time on each. */
    fun asText(formatTime: (Long) -> String): String = entries.value.joinToString("\n") { "${formatTime(it.time)}  ${it.text}" }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val now = System.currentTimeMillis()
            val stored = runCatching { if (file.exists()) file.readLines().mapNotNull(::parse) else emptyList() }
                .onFailure { aapsLogger.error(LTag.BGSOURCE, "DexcomG7: could not read the log file", it) }
                .getOrDefault(emptyList())
            _entries.value = keep(stored + _entries.value, now)
            loaded = true
            compact(now)
        }
    }

    private fun compact(now: Long) {
        lastCompactAt = now
        runCatching { file.writeText(_entries.value.joinToString("") { format(it) }) }
            .onFailure { aapsLogger.error(LTag.BGSOURCE, "DexcomG7: could not write the log file", it) }
    }

    companion object {

        const val FILE_NAME = "dexcom_g7_comm.log"
        const val KEEP_MS = 24 * 60 * 60 * 1000L
        const val COMPACT_INTERVAL_MS = 60 * 60 * 1000L

        /** A hard limit, in case something logs far more than normal. */
        const val MAX_ENTRIES = 50_000

        fun keep(entries: List<Entry>, now: Long): List<Entry> =
            entries.filter { it.time >= now - KEEP_MS }.takeLast(MAX_ENTRIES)

        fun format(entry: Entry): String = "${entry.time}\t${entry.text}\n"

        fun parse(line: String): Entry? {
            val tab = line.indexOf('\t')
            if (tab <= 0) return null
            val time = line.substring(0, tab).toLongOrNull() ?: return null
            return Entry(time, line.substring(tab + 1))
        }
    }
}
