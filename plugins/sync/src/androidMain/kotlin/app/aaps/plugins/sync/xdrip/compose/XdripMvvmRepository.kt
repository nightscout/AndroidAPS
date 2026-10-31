package app.aaps.plugins.sync.xdrip.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.plugins.sync.log.SyncLogBuffer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repository for xDrip UI state management.
 *
 * Holds reactive state flows for queue size and log entries
 * that are collected by the ViewModel and displayed in XdripScreen.
 */
@SingleIn(AppScope::class)
@Inject
class XdripMvvmRepository(
    aapsLogger: AAPSLogger
) {

    private val log = SyncLogBuffer(aapsLogger, LTag.XDRIP)

    private val _queueSize = MutableStateFlow(-1L)

    /** Current sync queue size */
    val queueSize: StateFlow<Long> = _queueSize.asStateFlow()

    /** Log entries displayed in the UI, newest first */
    val logList: StateFlow<List<SyncLogEntry>> = log.entries

    /** Update the queue size */
    fun updateQueueSize(size: Long) {
        _queueSize.value = size
    }

    /** Add a new log entry */
    fun addLog(action: String, logText: String?) = log.add(action, logText)

    /** Clear all log entries */
    fun clearLog() = log.clear()
}
