package app.aaps.plugins.sync.xdrip.compose

import java.util.concurrent.atomic.AtomicLong

class XdripLog(
    val action: String,
    val logText: String? = null
) {

    var date = System.currentTimeMillis()
    val id: Long = idCounter.getAndIncrement()

    companion object {

        private val idCounter = AtomicLong(0)
    }
}
