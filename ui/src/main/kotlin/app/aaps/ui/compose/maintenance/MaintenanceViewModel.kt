package app.aaps.ui.compose.maintenance

import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.L
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.LogElement
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.CloudDirectoryInfo
import app.aaps.core.interfaces.maintenance.CloudDirectoryManager
import app.aaps.core.interfaces.maintenance.ExportConfig
import app.aaps.core.interfaces.maintenance.ExportDestination
import app.aaps.core.interfaces.maintenance.ExportResult
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.maintenance.Maintenance
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import app.aaps.core.ui.R as CoreUiR

sealed interface MaintenanceEvent {
    data object RecreateActivity : MaintenanceEvent
    data class CleanupResult(val result: String) : MaintenanceEvent
    data class Snackbar(val message: String) : MaintenanceEvent
    data class Error(val message: String) : MaintenanceEvent
    data class LaunchBrowser(val url: String) : MaintenanceEvent
    data object BringToForeground : MaintenanceEvent
}

@HiltViewModel
@Stable
class MaintenanceViewModel @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val l: L,
    private val maintenance: Maintenance,
    private val importExportPrefs: ImportExportPrefs,
    private val cloudDirectoryManager: CloudDirectoryManager,
    private val activePlugin: ActivePlugin,
    private val persistenceLayer: PersistenceLayer,
    private val fabricPrivacy: FabricPrivacy,
    private val uel: UserEntryLogger,
    private val dataSyncSelectorXdrip: DataSyncSelectorXdrip,
    private val pumpSync: PumpSync,
    private val iobCobCalculator: IobCobCalculator,
    private val overviewData: OverviewData,
    private val overviewDataCache: OverviewDataCache
) : ViewModel() {

    private val _events = MutableSharedFlow<MaintenanceEvent>()
    val events: SharedFlow<MaintenanceEvent> = _events

    // Export configuration (cloud awareness)
    val exportConfig: StateFlow<ExportConfig?>
        field = MutableStateFlow<ExportConfig?>(null)

    init {
        refreshExportConfig()
    }

    fun refreshExportConfig() {
        viewModelScope.launch(Dispatchers.IO) {
            exportConfig.value = importExportPrefs.getExportConfig()
        }
    }

    fun toggleSettingsLocal(enabled: Boolean) {
        importExportPrefs.setSettingsLocalEnabled(enabled)
        refreshExportConfig()
    }

    fun toggleSettingsCloud(enabled: Boolean) {
        importExportPrefs.setSettingsCloudEnabled(enabled)
        refreshExportConfig()
    }

    fun toggleLogEmail(enabled: Boolean) {
        importExportPrefs.setLogEmailEnabled(enabled)
        refreshExportConfig()
    }

    fun toggleLogCloud(enabled: Boolean) {
        importExportPrefs.setLogCloudEnabled(enabled)
        refreshExportConfig()
    }

    fun toggleCsvLocal(enabled: Boolean) {
        importExportPrefs.setCsvLocalEnabled(enabled)
        refreshExportConfig()
    }

    fun toggleCsvCloud(enabled: Boolean) {
        importExportPrefs.setCsvCloudEnabled(enabled)
        refreshExportConfig()
    }

    // Log elements for LogSettingBottomSheet
    val logElements: List<LogElement> get() = l.logElements()

    fun toggleLogElement(element: LogElement, enabled: Boolean) {
        element.enable(enabled)
    }

    fun resetLogDefaults() {
        l.resetToDefaults()
    }

    // Log actions

    fun sendLogs() {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                maintenance.executeSendLogs()
            }
            val message = buildResultMessage(
                result,
                localSuccess = CoreUiR.string.logs_sent,
                localFailed = CoreUiR.string.logs_send_failed
            )
            _events.emit(MaintenanceEvent.Snackbar(message))
        }
    }

    fun deleteLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                maintenance.deleteLogs(5)
                uel.log(Action.DELETE_LOGS, Sources.Maintenance)
                _events.emit(MaintenanceEvent.Snackbar(rh.gs(CoreUiR.string.logs_deleted)))
            } catch (e: Exception) {
                aapsLogger.error("Error deleting logs", e)
                fabricPrivacy.logException(e)
            }
        }
    }

    // Database actions

    fun resetApsResults() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                persistenceLayer.clearApsResults()
                aapsLogger.debug("Aps results cleared")
            } catch (e: Exception) {
                aapsLogger.error("Error clearing aps results", e)
            }
        }
        uel.log(Action.RESET_APS_RESULTS, Sources.Maintenance)
    }

    fun cleanupDatabases() {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    persistenceLayer.cleanupDatabase(93, deleteTrackedChanges = true)
                }
                if (result.isNotEmpty()) {
                    _events.emit(MaintenanceEvent.CleanupResult(result))
                }
                aapsLogger.info(LTag.CORE, "Cleaned up databases with result: $result")
            } catch (e: Exception) {
                aapsLogger.error("Error cleaning up databases", e)
            }
        }
        uel.log(Action.CLEANUP_DATABASES, Sources.Maintenance)
    }

    fun resetDatabases() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    persistenceLayer.clearDatabases()
                    for (plugin in activePlugin.getSpecificPluginsListByInterface(OwnDatabasePlugin::class.java)) {
                        (plugin as OwnDatabasePlugin).clearAllTables()
                    }
                    activePlugin.activeNsClient?.dataSyncSelector?.resetToNextFullSync()
                    dataSyncSelectorXdrip.resetToNextFullSync()
                    pumpSync.connectNewPump()
                    overviewData.reset()
                    overviewDataCache.reset()
                    iobCobCalculator.ads.reset()
                    iobCobCalculator.clearCache()
                }
                _events.emit(MaintenanceEvent.RecreateActivity)
            } catch (e: Exception) {
                aapsLogger.error("Error clearing databases", e)
            }
        }
        uel.log(Action.RESET_DATABASES, Sources.Maintenance)
    }

    // Export/Import

    fun logSelectDirectory() {
        uel.log(Action.SELECT_DIRECTORY, Sources.Maintenance)
    }

    fun emitError(message: String) {
        viewModelScope.launch {
            _events.emit(MaintenanceEvent.Error(message))
        }
    }

    fun logImportSettings() {
        uel.log(Action.IMPORT_SETTINGS, Sources.Maintenance)
    }

    fun exportCsv() {
        uel.log(Action.EXPORT_CSV, Sources.Maintenance)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                importExportPrefs.executeCsvExport()
            }
            val message = buildResultMessage(
                result,
                localSuccess = CoreUiR.string.csv_exported,
                localFailed = CoreUiR.string.csv_export_failed
            )
            _events.emit(MaintenanceEvent.Snackbar(message))
        }
    }

    // Cloud directory flow

    sealed interface CloudDirectoryState {
        data object Hidden : CloudDirectoryState
        data class Shown(val info: CloudDirectoryInfo) : CloudDirectoryState
        data object ConfirmClear : CloudDirectoryState
        data object Reauthorize : CloudDirectoryState
    }

    val cloudDirectoryState: StateFlow<CloudDirectoryState>
        field = MutableStateFlow<CloudDirectoryState>(CloudDirectoryState.Hidden)

    fun showCloudDirectory() {
        val info = cloudDirectoryManager.getCloudDirectoryInfo()
        cloudDirectoryState.value = CloudDirectoryState.Shown(info)
    }

    fun dismissCloudDirectory() {
        cloudDirectoryState.value = CloudDirectoryState.Hidden
    }

    fun connectGoogleDrive() {
        val info = cloudDirectoryManager.getCloudDirectoryInfo()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (info.hasCredentials) {
                    if (cloudDirectoryManager.testConnection()) {
                        cloudDirectoryManager.setupCloudStorage()
                        refreshExportConfig()
                        cloudDirectoryState.value = CloudDirectoryState.Hidden
                    } else {
                        cloudDirectoryState.value = CloudDirectoryState.Reauthorize
                    }
                } else {
                    startAuthAndComplete()
                }
            } catch (e: Exception) {
                aapsLogger.error("Cloud directory connection error", e)
                _events.emit(MaintenanceEvent.Error(e.message ?: rh.gs(CoreUiR.string.error)))
                cloudDirectoryState.value = CloudDirectoryState.Hidden
            }
        }
    }

    fun requestClearCloud() {
        val info = cloudDirectoryManager.getCloudDirectoryInfo()
        if (info.isCloudActive) {
            cloudDirectoryState.value = CloudDirectoryState.ConfirmClear
        } else {
            cloudDirectoryManager.clearCloudSettings()
            refreshExportConfig()
            cloudDirectoryState.value = CloudDirectoryState.Hidden
        }
    }

    fun confirmClearCloud() {
        cloudDirectoryManager.clearCloudSettings()
        refreshExportConfig()
        cloudDirectoryState.value = CloudDirectoryState.Hidden
    }

    fun cancelClearCloud() {
        showCloudDirectory()
    }

    fun reauthorize() {
        cloudDirectoryManager.clearCloudSettings()
        viewModelScope.launch(Dispatchers.IO) {
            startAuthAndComplete()
        }
    }

    private suspend fun startAuthAndComplete() {
        try {
            val authUrl = cloudDirectoryManager.startAuth()
            if (authUrl != null) {
                cloudDirectoryState.value = CloudDirectoryState.Hidden
                _events.emit(MaintenanceEvent.LaunchBrowser(authUrl))
                val authCode = cloudDirectoryManager.waitForAuthCode()
                if (authCode != null && cloudDirectoryManager.completeAuth(authCode)) {
                    cloudDirectoryManager.setupCloudStorage()
                    cloudDirectoryManager.enableAllCloudExport()
                    refreshExportConfig()
                    _events.emit(MaintenanceEvent.BringToForeground)
                    _events.emit(MaintenanceEvent.Snackbar(rh.gs(CoreUiR.string.cloud_auth_success)))
                    cloudDirectoryState.value = CloudDirectoryState.Hidden
                } else {
                    _events.emit(MaintenanceEvent.BringToForeground)
                    _events.emit(MaintenanceEvent.Error(rh.gs(CoreUiR.string.error)))
                    cloudDirectoryState.value = CloudDirectoryState.Hidden
                }
            } else {
                _events.emit(MaintenanceEvent.Error(rh.gs(CoreUiR.string.error)))
            }
        } catch (e: Exception) {
            aapsLogger.error("Auth flow error", e)
            _events.emit(MaintenanceEvent.BringToForeground)
            _events.emit(MaintenanceEvent.Error(e.message ?: rh.gs(CoreUiR.string.error)))
            cloudDirectoryState.value = CloudDirectoryState.Hidden
        }
    }

    // Compose export flow

    sealed interface ExportState {
        data object Idle : ExportState
        data object MasterPasswordMissing : ExportState
        data class ConfirmExport(
            val fileName: String,
            val destination: ExportDestination = ExportDestination.LOCAL,
            val cloudDisplayName: String? = null
        ) : ExportState

        data object AskPassword : ExportState
    }

    val exportState: StateFlow<ExportState>
        field = MutableStateFlow<ExportState>(ExportState.Idle)

    fun startExport() {
        uel.log(Action.EXPORT_SETTINGS, Sources.Maintenance)

        if (!importExportPrefs.isMasterPasswordSet()) {
            exportState.value = ExportState.MasterPasswordMissing
            return
        }

        val preparation = importExportPrefs.prepareExport()
        if (preparation == null) {
            viewModelScope.launch { _events.emit(MaintenanceEvent.Error(rh.gs(CoreUiR.string.error))) }
            return
        }

        val cached = preparation.cachedPassword
        if (cached != null) {
            // Cached password available — export directly
            doExport(cached)
        } else {
            // Need to show confirm + password dialogs
            exportState.value = ExportState.ConfirmExport(
                fileName = preparation.fileName,
                destination = preparation.destination,
                cloudDisplayName = preparation.cloudDisplayName
            )
        }
    }

    fun onExportConfirmed() {
        exportState.value = ExportState.AskPassword
    }

    fun onExportPasswordEntered(password: String) {
        exportState.value = ExportState.Idle
        val cached = importExportPrefs.cacheExportPassword(password)
        doExport(cached)
    }

    fun cancelExport() {
        exportState.value = ExportState.Idle
    }

    private fun doExport(password: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                importExportPrefs.executeExport(password)
            }
            val message = buildExportResultMessage(result)
            _events.emit(MaintenanceEvent.Snackbar(message))
        }
    }

    private fun buildExportResultMessage(result: ExportResult): String =
        buildResultMessage(result, CoreUiR.string.export_result_message_exported, CoreUiR.string.export_result_message_failed)

    private fun buildResultMessage(result: ExportResult, @StringRes localSuccess: Int, @StringRes localFailed: Int): String {
        val parts = mutableListOf<String>()
        result.localSuccess?.let { ok ->
            parts += if (ok) rh.gs(localSuccess) else rh.gs(localFailed)
        }
        result.cloudSuccess?.let { ok ->
            parts += if (ok) rh.gs(CoreUiR.string.export_cloud_success)
            else rh.gs(CoreUiR.string.export_cloud_failed)
        }
        return parts.joinToString("\n").ifEmpty { rh.gs(localFailed) }
    }
}
