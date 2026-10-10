package app.aaps.plugins.sync.tidepool.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.plugins.sync.tidepool.auth.AuthFlowOut
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Repository for Tidepool UI state management.
 *
 * Holds reactive state flows for connection status and log entries
 * that are collected by the ViewModel and displayed in TidepoolScreen.
 * The Tidepool classes write here directly; there is no RxBus event for it any more.
 */
@SingleIn(AppScope::class)
@Inject
class TidepoolRepository(
    private val aapsLogger: AAPSLogger,
    private val rxBus: RxBus
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

    private val _uploadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** "Upload now" from the screen. Several taps before the plugin reacts count as one. */
    val uploadRequests: SharedFlow<Unit> = _uploadRequests.asSharedFlow()

    /** Update the connection status */
    fun updateConnectionStatus(status: AuthFlowOut.ConnectionStatus) {
        _connectionStatus.value = status
    }

    /** Add a new log entry. The setup wizard shows it as the sync status too. */
    fun addLog(status: String) {
        aapsLogger.debug(LTag.TIDEPOOL, status)
        _logList.update { currentList ->
            val newLog = TidepoolLog(status = status)
            listOf(newLog) + currentList.take(MAX_LOG_ENTRIES - 1)
        }
        rxBus.send(EventSWSyncStatus(status))
    }

    /** Clear all log entries */
    fun clearLog() {
        _logList.value = emptyList()
    }

    /** Ask the plugin to upload now */
    fun requestUpload() {
        _uploadRequests.tryEmit(Unit)
    }
}
