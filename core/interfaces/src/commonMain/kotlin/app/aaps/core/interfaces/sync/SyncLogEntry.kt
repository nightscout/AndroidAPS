package app.aaps.core.interfaces.sync

import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.time.Clock

/**
 * One line of the log a sync plugin (Nightscout, xDrip, Tidepool) shows on its screen.
 *
 * @param action shown in bold, e.g. "UPLOAD", or the whole message when there is no [text]
 * @param text the rest of the line
 * @param json an optional payload the user can open on the line
 */
@OptIn(ExperimentalAtomicApi::class)
class SyncLogEntry(
    val action: String,
    val text: String? = null,
    val json: JsonElement? = null
) {

    val date: Long = Clock.System.now().toEpochMilliseconds()
    val id: Long = idCounter.fetchAndIncrement()

    companion object {

        // kotlin.concurrent.atomics rather than java.util.concurrent: same semantics, but it exists on
        // every target, so this class can live in commonMain.
        private val idCounter = AtomicLong(0)
    }
}
