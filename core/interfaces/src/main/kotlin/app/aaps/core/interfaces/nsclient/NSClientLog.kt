package app.aaps.core.interfaces.nsclient

import kotlinx.serialization.json.JsonElement
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndIncrement
import kotlin.time.Clock

@OptIn(ExperimentalAtomicApi::class)
class NSClientLog(
    val action: String,
    val logText: String? = null,
    val json: JsonElement? = null
) {

    val date: Long = Clock.System.now().toEpochMilliseconds()
    val id: Long = idCounter.fetchAndIncrement()

    companion object {

        // kotlin.concurrent.atomics rather than java.util.concurrent: same semantics, but it exists on
        // every target, so this class can move to commonMain.
        private val idCounter = AtomicLong(0)
    }
}
