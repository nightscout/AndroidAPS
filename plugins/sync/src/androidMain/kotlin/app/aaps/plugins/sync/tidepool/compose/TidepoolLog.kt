package app.aaps.plugins.sync.tidepool.compose

import java.util.concurrent.atomic.AtomicLong

class TidepoolLog(
    val status: String
) {

    val date: Long = System.currentTimeMillis()
    val id: Long = idCounter.getAndIncrement()

    companion object {

        private val idCounter = AtomicLong(0)
    }
}
