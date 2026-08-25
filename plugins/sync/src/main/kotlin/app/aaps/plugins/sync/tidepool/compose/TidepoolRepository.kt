package app.aaps.plugins.sync.tidepool.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for Tidepool UI state management.
 *
 * Holds reactive state flows for connection status and log entries
 * that are collected by the ViewModel and displayed in TidepoolScreen.
 */
@Singleton
class TidepoolRepository @Inject constructor(
    private val aapsLogger: AAPSLogger
) {

    companion object {

        private const val MAX_LOG_ENTRIES = 100
    }

    private val _connectionStatus = MutableStateFlow(AuthFlowOut.ConnectionStatus.NONE)

    /** Current Tidepool connection status */
    val connectionStatus: StateFlow<AuthFlowOut.ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _logList = MutableStateFlow<List<TidepoolLog>>(emptyList())

    /** Log entries displayed in the UI, newest first */
    val logList: StateFlow<List<TidepoolLog>> = _logList.asStateFlow()

    /** Update the connection status */
    fun updateConnectionStatus(status: AuthFlowOut.ConnectionStatus) {
        _connectionStatus.value = status
    }

    /** Add a new log entry */
    fun addLog(status: String) {
        aapsLogger.debug(LTag.TIDEPOOL, status)
        _logList.update { currentList ->
            val newLog = TidepoolLog(status = status)
            listOf(newLog) + currentList.take(MAX_LOG_ENTRIES - 1)
        }
    }

    /** Clear all log entries */
    fun clearLog() {
        _logList.value = emptyList()
    }
}
