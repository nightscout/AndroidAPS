package app.aaps.plugins.sync.xdrip.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for xDrip UI state management.
 *
 * Holds reactive state flows for queue size and log entries
 * that are collected by the ViewModel and displayed in XdripScreen.
 */
@Singleton
class XdripMvvmRepository @Inject constructor(
    private val aapsLogger: AAPSLogger
) {

    companion object {

        private const val MAX_LOG_ENTRIES = 100
    }

    private val _queueSize = MutableStateFlow(-1L)

    /** Current sync queue size */
    val queueSize: StateFlow<Long> = _queueSize.asStateFlow()

    private val _logList = MutableStateFlow<List<XdripLog>>(emptyList())

    /** Log entries displayed in the UI, newest first */
    val logList: StateFlow<List<XdripLog>> = _logList.asStateFlow()

    /** Update the queue size */
    fun updateQueueSize(size: Long) {
        _queueSize.value = size
    }

    /** Add a new log entry */
    fun addLog(action: String, logText: String?) {
        aapsLogger.debug(LTag.XDRIP, "$action $logText")
        _logList.update { currentList ->
            val newLog = XdripLog(action = action, logText = logText)
            listOf(newLog) + currentList.take(MAX_LOG_ENTRIES - 1)
        }
    }

    /** Clear all log entries */
    fun clearLog() {
        _logList.value = emptyList()
    }
}
