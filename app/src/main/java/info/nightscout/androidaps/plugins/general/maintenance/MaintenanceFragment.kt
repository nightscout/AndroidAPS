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
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.insight.database.InsightDatabase
import info.nightscout.androidaps.interfaces.DataSyncSelector
import info.nightscout.androidaps.interfaces.ImportExportPrefs
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.interfaces.PumpSync
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity
import info.nightscout.androidaps.plugins.general.overview.OverviewData
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.Completable.fromAction
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

class MaintenanceFragment : DaggerFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var danaHistoryDatabase: DanaHistoryDatabase
    @Inject lateinit var insightDatabase: InsightDatabase
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
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.maintenance), resourceHelper.gs(R.string.reset_db_confirm), Runnable {
                    compositeDisposable.add(
                        fromAction {
                            repository.clearDatabases()
                            danaHistoryDatabase.clearAllTables()
                            insightDatabase.clearAllTables()
                            dataSyncSelector.resetToNextFullSync()
                            pumpSync.connectNewPump()
                            overviewData.reset()
                            iobCobCalculator.ads.reset()
                            iobCobCalculator.clearCache()
                        }
                            .subscribeOn(aapsSchedulers.io)
                            .observeOn(aapsSchedulers.main)
                            .subscribeBy(
                                onError = { aapsLogger.error("Error clearing databases", it) },
                                onComplete = {
                                    rxBus.send(EventNewBG(null))
                                    rxBus.send(EventNewHistoryData(0, true))
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
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.ue_export_to_csv) + "?") {
                    uel.log(Action.EXPORT_CSV, Sources.Maintenance)
                    importExportPrefs.exportUserEntriesCsv(activity, repository.getAllUserEntries())
                }
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