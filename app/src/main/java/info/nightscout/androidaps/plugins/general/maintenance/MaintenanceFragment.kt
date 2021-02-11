package info.nightscout.androidaps.plugins.general.maintenance

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.databinding.MaintenanceFragmentBinding
import info.nightscout.androidaps.events.EventNewBG
import info.nightscout.androidaps.interfaces.ImportExportPrefsInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.food.FoodPlugin
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
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
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var foodPlugin: FoodPlugin
    @Inject lateinit var importExportPrefs: ImportExportPrefsInterface
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var uel: UserEntryLogger

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
            uel.log("DELETE LOGS")
            maintenancePlugin.deleteLogs()
        }
        binding.navResetdb.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.maintenance), resourceHelper.gs(R.string.reset_db_confirm), Runnable {
                    uel.log("RESET DATABASES")
                    compositeDisposable.add(
                        fromAction {
                            MainApp.getDbHelper().resetDatabases()
                            // should be handled by Plugin-Interface and
                            // additional service interface and plugin registry
                            foodPlugin.service?.resetFood()
                            treatmentsPlugin.service.resetTreatments()
                            repository.clearDatabases()
                        }
                            .subscribeOn(aapsSchedulers.io)
                            .observeOn(aapsSchedulers.main)
                            .subscribeBy(
                                onError = { aapsLogger.error("Error clearing databases", it) },
                                onComplete = { rxBus.send(EventNewBG(null)) }
                            )
                    )
                })
            }
        }
        binding.navExport.setOnClickListener {
            uel.log("EXPORT SETTINGS")
            // start activity for checking permissions...
            importExportPrefs.verifyStoragePermissions(this) {
                importExportPrefs.exportSharedPreferences(this)
            }
        }
        binding.navImport.setOnClickListener {
            uel.log("IMPORT SETTINGS")
            // start activity for checking permissions...
            importExportPrefs.verifyStoragePermissions(this) {
                importExportPrefs.importSharedPreferences(this)
            }
        }
        binding.navLogsettings.setOnClickListener { startActivity(Intent(activity, LogSettingActivity::class.java)) }
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        compositeDisposable.clear()
        _binding = null
    }
}