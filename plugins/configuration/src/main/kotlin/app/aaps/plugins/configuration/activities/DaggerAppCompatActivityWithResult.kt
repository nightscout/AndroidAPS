package app.aaps.plugins.configuration.activities

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAAPSDirectorySelected
import app.aaps.core.interfaces.rx.events.EventThemeSwitch
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.dialogs.WarningDialog
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.CustomWatchfaceFileContract
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import app.aaps.plugins.configuration.maintenance.PrefsFileContract
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

open class DaggerAppCompatActivityWithResult : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var androidPermission: AndroidPermission

    private val compositeDisposable = CompositeDisposable()

    var accessTree: ActivityResultLauncher<Uri?>? = null
    var callForPrefFile: ActivityResultLauncher<Void?>? = null
    var callForCustomWatchfaceFile: ActivityResultLauncher<Void?>? = null
    var callForBatteryOptimization: ActivityResultLauncher<Void?>? = null
    var requestMultiplePermissions: ActivityResultLauncher<Array<String>>? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        compositeDisposable.add(rxBus.toObservable(EventThemeSwitch::class.java).subscribe {
            recreate()
        })

        accessTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                // Check if user selected a subdirectory instead of root AAPS directory
                val lastPathSegment = uri.lastPathSegment ?: ""

                // Extract directory name from the path
                // First remove the storage prefix (e.g., "primary:")
                val pathAfterColon = when {
                    lastPathSegment.contains(":") -> lastPathSegment.substringAfterLast(":")
                    else -> lastPathSegment
                }
                // Then get just the last directory name (e.g., "AAPS/preferences" -> "preferences")
                val directoryName = pathAfterColon.substringAfterLast("/", pathAfterColon)

                // Warn if user selected a subdirectory instead of root AAPS directory
                // These subdirectories are managed by the app
                val managedSubdirectories = listOf("preferences", "extra", "exports", "temp")
                if (managedSubdirectories.any { it.equals(directoryName, ignoreCase = true) }) {
                    WarningDialog.showWarning(
                        this,
                        rh.gs(R.string.warning_wrong_directory_selected),
                        rh.gs(R.string.warning_wrong_directory_message, directoryName),
                        android.R.string.ok
                    )
                    return@registerForActivityResult
                }

                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                preferences.put(StringKey.AapsDirectoryUri, uri.toString())
                rxBus.send(EventAAPSDirectorySelected(uri.path ?: "UNKNOWN"))
            }
        }
        callForPrefFile = registerForActivityResult(PrefsFileContract()) {
            // Do not pass full file through intent. It crash on large file
            // it?.let {
            //     importExportPrefs.importSharedPreferences(this, it)
            // }
            importExportPrefs.doImportSharedPreferences(this)
        }
        callForCustomWatchfaceFile = registerForActivityResult(CustomWatchfaceFileContract()) { }

        callForBatteryOptimization = registerForActivityResult(OptimizationPermissionContract()) {
            updateButtons()
        }

        requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                aapsLogger.info(LTag.CORE, "Permission ${it.key} ${it.value}")
                when (it.key) {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION     ->
                        if (!it.value || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            androidPermission.notifyForLocationPermissions(this)
                            ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.location_permission_not_granted))
                        }

                    Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                        if (!it.value || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            androidPermission.notifyForLocationPermissions(this)
                            ToastUtils.errorToast(this, getString(app.aaps.core.ui.R.string.location_permission_not_granted))
                        }
                }
            }
            updateButtons()
        }
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        accessTree = null
        callForPrefFile = null
        callForCustomWatchfaceFile = null
        callForBatteryOptimization = null
        requestMultiplePermissions = null
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        // Handle cloud import result
        if (requestCode == CloudConstants.CLOUD_IMPORT_REQUEST_CODE && resultCode == RESULT_OK) {
            importExportPrefs.doImportSharedPreferences(this)
        }
    }

    // Used for SetupWizardActivity
    open fun updateButtons() {}
}