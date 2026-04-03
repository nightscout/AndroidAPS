package app.aaps.plugins.sync.nsShared.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientLog
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for NSClient UI state management.
 *
 * Holds reactive state flows for queue size, connection status, URL,
 * and log entries that are collected by the ViewModel and displayed in NSClientScreen.
 *
 * Note: Interface [app.aaps.core.interfaces.nsclient.NSClientRepository] is in core:interfaces module
 * to allow cross-module dependency injection.
 */
@Singleton
class NSClientRepositoryImpl @Inject constructor(
    private val rxBus: RxBus,
    private val aapsLogger: AAPSLogger
) : NSClientRepository {

    companion object {

        private const val MAX_LOG_ENTRIES = 100
    }

    private val _queueSize = MutableStateFlow(-1L)
    override val queueSize: StateFlow<Long> = _queueSize.asStateFlow()

    private val _statusUpdate = MutableStateFlow("")
    override val statusUpdate: StateFlow<String> = _statusUpdate.asStateFlow()

    private val _urlUpdate = MutableStateFlow("")
    override val urlUpdate: StateFlow<String> = _urlUpdate.asStateFlow()

    private val _logList = MutableStateFlow<List<NSClientLog>>(emptyList())
    override val logList: StateFlow<List<NSClientLog>> = _logList.asStateFlow()

    override fun updateQueueSize(size: Long) {
        _queueSize.value = size
    }

    override fun updateStatus(status: String) {
        _statusUpdate.value = status
        rxBus.send(EventSWSyncStatus(status))
    }

    override fun updateUrl(url: String) {
        _urlUpdate.value = url
    }

    override fun addLog(action: String, logText: String?, json: JsonElement?) {
        _logList.update { currentList ->
            aapsLogger.debug(LTag.NSCLIENT, "$action $logText")
            val newLog = NSClientLog(action = action, logText = logText, json = json)
            listOf(newLog) + currentList.take(MAX_LOG_ENTRIES - 1)
        }
    }

    override fun clearLog() {
        _logList.value = emptyList()
    }
}