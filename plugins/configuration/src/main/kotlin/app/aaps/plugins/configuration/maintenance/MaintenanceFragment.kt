package app.aaps.plugins.configuration.maintenance

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.toSpanned
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.extensions.runOnUiThread
import app.aaps.core.interfaces.extensions.toVisibility
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
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
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.HtmlHelper
import app.aaps.database.entities.UserEntry.Action
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.databinding.MaintenanceFragmentBinding
import app.aaps.plugins.configuration.maintenance.activities.LogSettingActivity
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
                                    rxBus.send(EventPreferenceChange(rh.gs(info.nightscout.core.utils.R.string.key_units)))
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
                importExportPrefs.importSharedPreferences(this)
            }
        }
        binding.navLogsettings.setOnClickListener { startActivity(Intent(activity, LogSettingActivity::class.java)) }
        binding.exportCsv.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.ue_export_to_csv) + "?") {
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
