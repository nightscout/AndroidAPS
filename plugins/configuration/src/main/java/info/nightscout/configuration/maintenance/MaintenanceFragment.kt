package info.nightscout.configuration.maintenance

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.toSpanned
import dagger.android.support.DaggerFragment
import info.nightscout.configuration.R
import info.nightscout.configuration.databinding.MaintenanceFragmentBinding
import info.nightscout.configuration.maintenance.activities.LogSettingActivity
import info.nightscout.core.graph.OverviewData
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.maintenance.ImportExportPrefs
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.plugin.OwnDatabasePlugin
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.protection.ProtectionCheck.Protection.PREFERENCES
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.sync.DataSyncSelector
import info.nightscout.interfaces.ui.ActivityNames
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventPreferenceChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
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
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activityNames: ActivityNames
    @Inject lateinit var activePlugin: ActivePlugin

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
        inMenu = parentClass == activityNames.singleFragmentActivity
        updateProtectedUi()
        binding.logSend.setOnClickListener { maintenancePlugin.sendLogs() }
        binding.logDelete.setOnClickListener {
            disposable +=
                Completable.fromAction { maintenancePlugin.deleteLogs(5) }
                    .subscribeOn(aapsSchedulers.io)
                    .subscribe({ uel.log(Action.DELETE_LOGS, Sources.Maintenance) }, fabricPrivacy::logException)
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
                            dataSyncSelector.resetToNextFullSync()
                            pumpSync.connectNewPump()
                            overviewData.reset()
                            iobCobCalculator.ads.reset()
                            iobCobCalculator.clearCache()
                        }
                            .subscribeOn(aapsSchedulers.io)
                            .subscribeBy(
                                onError = { aapsLogger.error("Error clearing databases", it) },
                                onComplete = { rxBus.send(EventPreferenceChange(rh.gs(R.string.key_units))) }
                            )
                    uel.log(Action.RESET_DATABASES, Sources.Maintenance)
                })
            }
        }
        binding.cleanupDb.setOnClickListener {
            activity?.let { activity ->
                var result = ""
                OKDialog.showConfirmation(activity, rh.gs(R.string.maintenance), rh.gs(R.string.cleanup_db_confirm), Runnable {
                    disposable += Completable.fromAction { result = persistenceLayer.cleanupDatabase(93, deleteTrackedChanges = true) }
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.main)
                        .subscribeBy(
                            onError = { aapsLogger.error("Error cleaning up databases", it) },
                            onComplete = {
                                if (result.isNotEmpty())
                                    OKDialog.show(activity, rh.gs(R.string.result), HtmlHelper.fromHtml("<b>" + rh.gs(R.string.cleared_entries) + "</b>\n" + result).toSpanned())
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
                importExportPrefs.importSharedPreferences(this)
            }
        }
        binding.navLogsettings.setOnClickListener { startActivity(Intent(activity, LogSettingActivity::class.java)) }
        binding.exportCsv.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.ue_export_to_csv) + "?") {
                    uel.log(Action.EXPORT_CSV, Sources.Maintenance)
                    importExportPrefs.exportUserEntriesCsv(activity)
                }
            }
        }

        binding.unlock.setOnClickListener { queryProtection() }
    }

    override fun onResume() {
        super.onResume()
        if (inMenu) queryProtection() else updateProtectedUi()
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
