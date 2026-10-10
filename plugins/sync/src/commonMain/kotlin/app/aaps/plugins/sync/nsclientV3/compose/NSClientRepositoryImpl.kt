package app.aaps.plugins.sync.nsclientV3.compose

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventSWSyncStatus
import app.aaps.core.interfaces.sync.SyncLogEntry
import app.aaps.plugins.sync.log.SyncLogBuffer
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement

/**
 * Repository for NSClient UI state management.
 *
 * Holds reactive state flows for queue size, connection status, URL,
 * and log entries that are collected by the ViewModel and displayed in NSClientScreen.
 *
 * Note: Interface [NSClientRepository] is in core:interfaces module
 * to allow cross-module dependency injection.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class NSClientRepositoryImpl(
    private val rxBus: RxBus,
    aapsLogger: AAPSLogger
) : NSClientRepository {

    private val log = SyncLogBuffer(aapsLogger, LTag.NSCLIENT)

    private val _queueSize = MutableStateFlow(-1L)
    override val queueSize: StateFlow<Long> = _queueSize.asStateFlow()

    private val _statusUpdate = MutableStateFlow("")
    override val statusUpdate: StateFlow<String> = _statusUpdate.asStateFlow()

    private val _urlUpdate = MutableStateFlow("")
    override val urlUpdate: StateFlow<String> = _urlUpdate.asStateFlow()

    override val logList: StateFlow<List<SyncLogEntry>> = log.entries

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

    override fun addLog(action: String, logText: String?, json: JsonElement?) = log.add(action, logText, json)

    override fun clearLog() = log.clear()
}
