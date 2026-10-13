package app.aaps.plugins.configuration.maintenance

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.toSpanned
import androidx.fragment.app.FragmentActivity
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.FileListProvider
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.OwnDatabasePlugin
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionCheck.Protection.PREFERENCES
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.sync.DataSyncSelectorXdrip
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.StringKey
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult
import app.aaps.plugins.configuration.databinding.MaintenanceFragmentBinding
import app.aaps.plugins.configuration.maintenance.activities.LogSettingActivity
import app.aaps.plugins.configuration.maintenance.cloud.CloudStorageManager
import app.aaps.plugins.configuration.maintenance.cloud.CloudDirectoryDialog
import app.aaps.plugins.configuration.maintenance.cloud.ExportOptionsDialog
import app.aaps.plugins.configuration.maintenance.cloud.events.EventCloudStorageStatusChanged
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class MaintenanceFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var dataSyncSelectorXdrip: DataSyncSelectorXdrip
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var fileListProvider: FileListProvider
    @Inject lateinit var cloudStorageManager: CloudStorageManager
    @Inject lateinit var cloudDirectoryDialog: CloudDirectoryDialog
    @Inject lateinit var exportOptionsDialog: ExportOptionsDialog

    private val disposable = CompositeDisposable()
    private var inMenu = false
    private var queryingProtection = false
    private var _binding: MaintenanceFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = MaintenanceFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parentClass = this.activity?.let { it::class.java }
        inMenu = parentClass == uiInteraction.singleFragmentActivity
        updateProtectedUi()
        binding.logSend.setOnClickListener { maintenancePlugin.sendLogs() }
        binding.logDelete.setOnClickListener {
            disposable +=
                Completable.fromAction { maintenancePlugin.deleteLogs(5) }
                    .subscribeOn(aapsSchedulers.io)
                    .subscribe({ uel.log(Action.DELETE_LOGS, Sources.Maintenance) }, fabricPrivacy::logException)
        }
        binding.navResetApsResults.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.maintenance), rh.gs(R.string.reset_aps_results_confirm), Runnable {
                    disposable +=
                        Completable.fromAction {
                            persistenceLayer.clearApsResults()
                        }
                            .subscribeOn(aapsSchedulers.io)
                            .subscribeBy(
                                onError = { aapsLogger.error("Error clearing aps results", it) },
                                onComplete = { aapsLogger.debug("Aps results cleared") }
                            )
                    uel.log(Action.RESET_APS_RESULTS, Sources.Maintenance)
                })
            }
        }
        binding.navResetdb.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.maintenance), rh.gs(R.string.reset_db_confirm), Runnable {
                    disposable +=
                        Completable.fromAction {
                            persistenceLayer.clearDatabases()
                            for (plugin in activePlugin.getSpecificPluginsListByInterface(OwnDatabasePlugin::class.java)) {
                                (plugin as OwnDatabasePlugin).clearAllTables()
                            }
                            activePlugin.activeNsClient?.dataSyncSelector?.resetToNextFullSync()
                            dataSyncSelectorXdrip.resetToNextFullSync()
                            pumpSync.connectNewPump()
                            overviewData.reset()
                            iobCobCalculator.ads.reset()
                            iobCobCalculator.clearCache()
                        }
                            .subscribeOn(aapsSchedulers.io)
                            .subscribeBy(
                                onError = { aapsLogger.error("Error clearing databases", it) },
                                onComplete = {
                                    rxBus.send(EventPreferenceChange(StringKey.GeneralUnits.key))
                                    runOnUiThread { activity.recreate() }
                                }
                            )
                    uel.log(Action.RESET_DATABASES, Sources.Maintenance)
                })
            }
        }
        binding.cleanupDb.setOnClickListener {
            activity?.let { activity ->
                var result = ""
                OKDialog.showConfirmation(activity, rh.gs(R.string.maintenance), rh.gs(app.aaps.core.ui.R.string.cleanup_db_confirm), Runnable {
                    disposable += Completable.fromAction { result = persistenceLayer.cleanupDatabase(93, deleteTrackedChanges = true) }
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.main)
                        .subscribeBy(
                            onError = { aapsLogger.error("Error cleaning up databases", it) },
                            onComplete = {
                                if (result.isNotEmpty())
                                    OKDialog.show(
                                        activity,
                                        rh.gs(app.aaps.core.ui.R.string.result),
                                        HtmlHelper.fromHtml("<b>" + rh.gs(app.aaps.core.ui.R.string.cleared_entries) + "</b><br>" + result)
                                            .toSpanned()
                                    )
                                aapsLogger.info(LTag.CORE, "Cleaned up databases with result: $result")
                            }
                        )
                    uel.log(Action.CLEANUP_DATABASES, Sources.Maintenance)
                })
            }
        }
        binding.navExport.setOnClickListener {
            uel.log(Action.EXPORT_SETTINGS, Sources.Maintenance)
            // start activity for checking permissions...
            importExportPrefs.verifyStoragePermissions(this) {
                importExportPrefs.exportSharedPreferences(this)
            }
        }
        binding.navImport.setOnClickListener {
            uel.log(Action.IMPORT_SETTINGS, Sources.Maintenance)
            // start activity for checking permissions...
            importExportPrefs.verifyStoragePermissions(this) {
                importExportPrefs.importSharedPreferences(activity as FragmentActivity)
            }
        }
        // Local directory: only used for selecting AAPS base folder
        binding.directory.setOnClickListener {
            (requireActivity() as? DaggerAppCompatActivityWithResult)?.let { act ->
                maintenancePlugin.selectAapsDirectory(act)
            }
        }
        // Cloud directory: choose not to use or Google Drive
        binding.cloudDirectory.setOnClickListener {
            (requireActivity() as? DaggerAppCompatActivityWithResult)?.let { act ->
                cloudDirectoryDialog.showCloudDirectoryDialog(
                    act,
                    onLocalSelected = { /* Choose not to use cloud: set storage type to local, no action */ },
                    onCloudSelected = { /* Authorization and folder selection handled in dialog */ },
                    onStorageChanged = { 
                        updateStorageErrorState()
                        updateDynamicButtonText()
                        updateExportOptionsButtonState()
                    }
                )
            }
        }
        
        // Cloud directory error icon click - show toast with error info
        binding.cloudDirectoryErrorIcon.setOnClickListener {
            app.aaps.core.ui.toast.ToastUtils.warnToast(requireContext(), rh.gs(R.string.cloud_token_expired_or_invalid))
        }
        
        // Export destination: configure destination for various export functions
        binding.exportOptions.setOnClickListener {
            val hasCloudDirectory = cloudStorageManager.isCloudStorageActive()
            if (!hasCloudDirectory) {
                // Show message if cloud directory is not set up
                app.aaps.core.ui.toast.ToastUtils.warnToast(requireContext(), rh.gs(R.string.setup_cloud_directory_first))
                return@setOnClickListener
            }
            (requireActivity() as? DaggerAppCompatActivityWithResult)?.let { act ->
                exportOptionsDialog.showExportOptionsDialog(act) {
                    // Settings changed callback - update button text
                    updateDynamicButtonText()
                }
            }
        }
        binding.navLogsettings.setOnClickListener { startActivity(Intent(activity, LogSettingActivity::class.java)) }
        binding.exportCsv.setOnClickListener {
            aapsLogger.info(LTag.CORE, "CSV_EXPORT exportCsv button clicked")
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.ue_export_to_csv) + "?") {
                    aapsLogger.info(LTag.CORE, "CSV_EXPORT user confirmed, calling exportUserEntriesCsv")
                    uel.log(Action.EXPORT_CSV, Sources.Maintenance)
                    importExportPrefs.exportUserEntriesCsv(activity)
                }
            }
        }

        binding.unlock.setOnClickListener { queryProtection() }
    }

    override fun onResume() {
        super.onResume()
        // Check and restore cloud settings (prevent settings loss after app update)
        checkAndRestoreCloudSettings()
        
        // Subscribe to cloud storage status changes to update UI immediately
        disposable += rxBus
            .toObservable(EventCloudStorageStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateStorageErrorState() }, fabricPrivacy::logException)
        
        if (inMenu) queryProtection() else {
            updateProtectedUi()
            updateStorageErrorState()
            updateDynamicButtonText()
            updateExportOptionsButtonState()
        }
    }
    
    /**
     * Check and restore cloud storage settings
     */
    private fun checkAndRestoreCloudSettings() {
        try {
            // Trigger auto-restore logic
            cloudStorageManager.getActiveStorageType()
        } catch (e: Exception) {
            aapsLogger.warn(LTag.CORE, "Failed to check cloud storage settings", e)
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    private fun updateProtectedUi() {
        val isLocked = protectionCheck.isLocked(PREFERENCES)
        binding.mainLayout.visibility = isLocked.not().toVisibility()
        binding.unlock.visibility = isLocked.toVisibility()
        
        // Update storage error state when UI becomes available
        if (!isLocked) {
            updateStorageErrorState()
            updateDynamicButtonText()
        }
    }

    private fun updateStorageErrorState() {
        // Local directory - no error icon needed (local storage doesn't have connection errors)
        binding.directoryErrorIcon.visibility = View.GONE
        
        // Cloud directory error - show when cloud is active but token is invalid/expired
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        val provider = cloudStorageManager.getActiveProvider()
        val hasValidCredentials = provider?.hasValidCredentials() ?: false
        val hasCloudConnectionError = provider?.hasConnectionError() ?: false
        
        // Show error icon if cloud is active but credentials are invalid or there's a connection error
        val showCloudError = isCloudActive && (!hasValidCredentials || hasCloudConnectionError)
        binding.cloudDirectoryErrorIcon.visibility = if (showCloudError) View.VISIBLE else View.GONE
    }
    
    /**
     * Update export options button state based on cloud directory selection
     */
    private fun updateExportOptionsButtonState() {
        val hasCloudDirectory = cloudStorageManager.isCloudStorageActive()
        binding.exportOptions.alpha = if (hasCloudDirectory) 1.0f else 0.5f
    }
    
    /**
     * Update dynamic button text based on export destination settings
     */
    private fun updateDynamicButtonText() {
        val isAllCloud = exportOptionsDialog.isAllCloudEnabled()
        val isCloudActive = cloudStorageManager.isCloudStorageActive()
        
        // Log button text
        val isLogCloud = isAllCloud || exportOptionsDialog.isLogCloudEnabled()
        binding.logSend.text = rh.gs(
            if (isLogCloud) R.string.send_logs_to_cloud else R.string.send_all_logs
        )
        
        // CSV button text
        val isCsvCloud = isAllCloud || exportOptionsDialog.isCsvCloudEnabled()
        binding.exportCsv.text = rh.gs(
            if (isCsvCloud) R.string.export_csv_to_cloud else R.string.export_csv_to_local
        )
        
        // Settings export/import destinations
        val isSettingsLocal = exportOptionsDialog.isSettingsLocalEnabled()
        val isSettingsCloud = exportOptionsDialog.isSettingsCloudEnabled()
        val bothEnabled = isSettingsLocal && isSettingsCloud && isCloudActive
        val cloudOnly = isSettingsCloud && isCloudActive && !isSettingsLocal
        
        // Export button text
        binding.navExport.text = rh.gs(
            when {
                bothEnabled -> R.string.export_settings_both
                cloudOnly -> R.string.export_settings_cloud
                else -> R.string.export_settings_local
            }
        )
        
        // Import button text
        binding.navImport.text = rh.gs(
            when {
                bothEnabled -> R.string.import_settings_both
                cloudOnly -> R.string.import_settings_cloud
                else -> R.string.import_settings_local
            }
        )
    }

    private fun queryProtection() {
        val isLocked = protectionCheck.isLocked(PREFERENCES)
        if (isLocked && !queryingProtection) {
            activity?.let { activity ->
                queryingProtection = true
                val doUpdate = { activity.runOnUiThread { queryingProtection = false; updateProtectedUi() } }
                protectionCheck.queryProtection(activity, PREFERENCES, doUpdate, doUpdate, doUpdate)
            }
        }
    }
}
