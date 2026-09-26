package app.aaps.cgm.dexcomg7.data

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The last few hundred lines of what happened between the phone and the sensor, for the log screen.
 *
 * A tester who says "it got stuck" can only be helped when these lines are visible outside a
 * debugger. Everything also goes to the AAPS log. Lines never carry the pairing code or a key.
 */
@SingleIn(AppScope::class)
@Inject
class G7CommLog(private val aapsLogger: AAPSLogger) {

    class Entry(val time: Long, val text: String)

    private val _entries = MutableStateFlow<List<Entry>>(emptyList())
    val entries: StateFlow<List<Entry>> = _entries.asStateFlow()

    fun add(text: String) {
        aapsLogger.debug(LTag.BGSOURCE, "DexcomG7: $text")
        _entries.update { (it + Entry(System.currentTimeMillis(), text)).takeLast(MAX_ENTRIES) }
    }

    fun clear() = _entries.update { emptyList() }

    companion object {

        const val MAX_ENTRIES = 500
    }
}
