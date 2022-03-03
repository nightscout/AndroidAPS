package info.nightscout.androidaps.plugins.general.maintenance

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.dana.database.DanaHistoryDatabase
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.MaintenanceFragmentBinding
import info.nightscout.androidaps.diaconn.database.DiaconnHistoryDatabase
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.insight.database.InsightDatabase
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.ImportExportPrefs
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.database.DashHistoryDatabase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryDatabase
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.rxjava3.core.Completable.fromAction
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class MaintenanceFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var danaHistoryDatabase: DanaHistoryDatabase
    @Inject lateinit var insightDatabase: InsightDatabase
    @Inject lateinit var diaconnDatabase: DiaconnHistoryDatabase
    @Inject lateinit var erosDatabase: ErosHistoryDatabase
    @Inject lateinit var dashDatabase: DashHistoryDatabase
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var dataSyncSelector: DataSyncSelector
    @Inject lateinit var pumpSync: PumpSync
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var overviewData: OverviewData

    private val compositeDisposable = CompositeDisposable()

    private var _binding: MaintenanceFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = MaintenanceFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.logSend.setOnClickListener { maintenancePlugin.sendLogs() }
        binding.logDelete.setOnClickListener {
            uel.log(Action.DELETE_LOGS, Sources.Maintenance)
            Thread {
                maintenancePlugin.deleteLogs(5)
            }.start()
        }
        binding.navResetdb.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.maintenance), rh.gs(R.string.reset_db_confirm), Runnable {
                    compositeDisposable.add(
                        fromAction {
                            repository.clearDatabases()
                            danaHistoryDatabase.clearAllTables()
                            insightDatabase.clearAllTables()
                            diaconnDatabase.clearAllTables()
                            erosDatabase.clearAllTables()
                            dashDatabase.clearAllTables()
                            dataSyncSelector.resetToNextFullSync()
                            pumpSync.connectNewPump()
                            overviewData.reset()
                            iobCobCalculator.ads.reset()
                            iobCobCalculator.clearCache()
                        }
                            .subscribeOn(aapsSchedulers.io)
                            .subscribeBy(
                                onError = { aapsLogger.error("Error clearing databases", it) },
                                onComplete = {
                                    rxBus.send(EventPreferenceChange(rh, R.string.key_units))
                                }
                            )
                    )
                    uel.log(Action.RESET_DATABASES, Sources.Maintenance)
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

        if (protectionCheck.isLocked(ProtectionCheck.Protection.PREFERENCES)) {
            binding.mainLayout.visibility = View.GONE
        } else {
            binding.unlock.visibility = View.GONE
        }

        binding.unlock.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.PREFERENCES, {
                    activity.runOnUiThread {
                        binding.mainLayout.visibility = View.VISIBLE
                        binding.unlock.visibility = View.GONE
                    }
                })
            }
        }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
        _binding = null
    }
}