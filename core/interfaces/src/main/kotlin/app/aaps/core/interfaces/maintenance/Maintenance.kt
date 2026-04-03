package app.aaps.core.interfaces.maintenance

/**
 * Interface for maintenance functionality.
 * Allows access to maintenance plugin through interface lookup.
 */
interface Maintenance {

    fun sendLogs()
    suspend fun executeSendLogs(): ExportResult
    fun deleteLogs(keep: Int)
}
