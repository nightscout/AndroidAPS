package info.nightscout.androidaps.plugins.general.maintenance

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.interfaces.ImportExportPrefsInterface
import info.nightscout.androidaps.plugins.general.food.FoodPlugin
import info.nightscout.androidaps.plugins.general.maintenance.activities.LogSettingActivity
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.maintenance_fragment.*
import javax.inject.Inject

class MaintenanceFragment : DaggerFragment() {

    @Inject lateinit var maintenancePlugin: MaintenancePlugin
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var foodPlugin: FoodPlugin
    @Inject lateinit var importExportPrefs: ImportExportPrefsInterface

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.maintenance_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        log_send.setOnClickListener { maintenancePlugin.sendLogs() }
        log_delete.setOnClickListener { maintenancePlugin.deleteLogs() }
        nav_resetdb.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.maintenance), resourceHelper.gs(R.string.reset_db_confirm), Runnable {
                    MainApp.getDbHelper().resetDatabases()
                    // should be handled by Plugin-Interface and
                    // additional service interface and plugin registry
                    foodPlugin.service?.resetFood()
                    treatmentsPlugin.service.resetTreatments()
                })
            }
        }
        nav_export.setOnClickListener {
            // start activity for checking permissions...
            importExportPrefs.verifyStoragePermissions(this) {
                importExportPrefs.exportSharedPreferences(this)
            }
        }
        nav_import.setOnClickListener {
            // start activity for checking permissions...
            importExportPrefs.verifyStoragePermissions(this) {
                importExportPrefs.importSharedPreferences(this)
            }
        }
        nav_logsettings.setOnClickListener { startActivity(Intent(activity, LogSettingActivity::class.java)) }
    }
}