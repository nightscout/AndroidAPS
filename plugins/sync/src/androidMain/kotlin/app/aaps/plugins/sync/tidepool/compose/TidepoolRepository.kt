package app.aaps.plugins.sync.tidepool.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.plugins.sync.log.SyncLogBuffer
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

/**
 * Repository for Tidepool UI state management.
 *
 * Holds reactive state flows for connection status and log entries
 * that are collected by the ViewModel and displayed in TidepoolScreen.
 * The Tidepool classes write here directly; there is no RxBus event for it any more.
 *
 * Nothing goes to the setup wizard: its sync step shows the Nightscout status only, so a Tidepool line
 * there replaced the Nightscout status with a Tidepool one.
 */
@SingleIn(AppScope::class)
@Inject
class TidepoolRepository(
    aapsLogger: AAPSLogger
) {

    private val _connectionStatus = MutableStateFlow(AuthFlowOut.ConnectionStatus.NONE)

    /** Current Tidepool connection status */
    val connectionStatus: StateFlow<AuthFlowOut.ConnectionStatus> = _connectionStatus.asStateFlow()

    private val log = SyncLogBuffer(aapsLogger, LTag.TIDEPOOL)

    /** Log entries displayed in the UI, newest first. A Tidepool line is all action, it has no text. */
    val logList: StateFlow<List<SyncLogEntry>> = log.entries

    private val _uploadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** "Upload now" from the screen. Several taps before the plugin reacts count as one. */
    val uploadRequests: SharedFlow<Unit> = _uploadRequests.asSharedFlow()

    /** Update the connection status */
    fun updateConnectionStatus(status: AuthFlowOut.ConnectionStatus) {
        _connectionStatus.value = status
    }

    /** Add a new log entry */
    fun addLog(status: String) = log.add(status)

    /** Clear all log entries */
    fun clearLog() = log.clear()

    /** Ask the plugin to upload now */
    fun requestUpload() {
        _uploadRequests.tryEmit(Unit)
    }
}
