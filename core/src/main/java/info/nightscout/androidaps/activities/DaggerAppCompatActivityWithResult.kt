package info.nightscout.androidaps.activities

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.interfaces.ImportExportPrefs
import info.nightscout.androidaps.plugins.general.maintenance.PrefsFileContract
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.permissions.OptimizationPermissionContract
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import javax.inject.Inject

open class DaggerAppCompatActivityWithResult : DaggerAppCompatActivity() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsLogger: AAPSLogger

    val callForPrefFile = registerForActivityResult(PrefsFileContract()) {
        it?.let {
            importExportPrefs.importSharedPreferences(this, it)
        }
    }

    val callForBatteryOptimization = registerForActivityResult(OptimizationPermissionContract()) {
        updateButtons()
    }

    val requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach {
            aapsLogger.info(LTag.CORE, "Permission ${it.key} ${it.value}")
            if (it.value)
                if (ActivityCompat.checkSelfPermission(this, it.key) == PackageManager.PERMISSION_GRANTED) {
                    when (it.key) {
                        Manifest.permission.WRITE_EXTERNAL_STORAGE ->
                            //show dialog after permission is granted
                            OKDialog.show(this, "", rh.gs(R.string.alert_dialog_storage_permission_text))
                        //  ignore the rest
                    }
                }
        }
        updateButtons()
    }

    // Used for SetupWizardActivity
    open fun updateButtons() {}
}