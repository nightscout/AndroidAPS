package app.aaps.core.interfaces.nsclient

import kotlinx.serialization.json.JsonElement
import java.util.concurrent.atomic.AtomicLong

class NSClientLog(
    val action: String,
    val logText: String? = null,
    val json: JsonElement? = null
) {

    val date: Long = System.currentTimeMillis()
    val id: Long = idCounter.getAndIncrement()

    companion object {

        private val idCounter = AtomicLong(0)
    }
}
