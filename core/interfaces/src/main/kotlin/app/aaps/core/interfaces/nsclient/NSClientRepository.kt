package app.aaps.core.interfaces.nsclient

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.json.JSONObject

/**
 * Repository interface for NSClient UI state management.
 *
 * Provides reactive state flows for queue size, connection status, URL,
 * and log entries that are collected by the ViewModel and displayed in NSClientScreen.
 *
 * This interface is in core:interfaces to allow cross-module dependency injection.
 * Implementation is in plugins:sync module.
 */
interface NSClientRepository {

    /** Current sync queue size */
    val queueSize: StateFlow<Long>

    /** Current connection status */
    val statusUpdate: StateFlow<String>

    /** Current Nightscout URL */
    val urlUpdate: StateFlow<String>

    /** Log entries displayed in the UI, newest first */
    val logList: StateFlow<List<NSClientLog>>

    /** Update the queue size */
    fun updateQueueSize(size: Long)

    /** Update the connection status */
    fun updateStatus(status: String)

    /** Update the Nightscout URL */
    fun updateUrl(url: String)

    /** Add a new log entry with optional JSON payload */
    fun addLog(action: String, logText: String?, json: JsonElement?)

    /** Add a new log entry without JSON payload */
    fun addLog(action: String, logText: String?) {
        addLog(action, logText, null as JsonElement?)
    }

    /** Add a new log entry with JSONObject payload */
    @Deprecated("Migrate to kotlin's JsonObject")
    fun addLog(action: String, logText: String?, json: JSONObject) {
        val jsonObject = json.let { Json.parseToJsonElement(it.toString()) as JsonObject }
        addLog(action, logText, jsonObject)
    }

    /** Clear all log entries */
    fun clearLog()
}
