package app.aaps.plugins.configuration.activities

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.locale.LocaleHelper
import app.aaps.plugins.configuration.R
import app.aaps.plugins.configuration.maintenance.PrefsFileContract
import app.aaps.plugins.configuration.maintenance.cloud.CloudConstants
import dagger.android.support.DaggerAppCompatActivity
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

open class DaggerAppCompatActivityWithResult : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var uiInteraction: UiInteraction
    private val compositeDisposable = CompositeDisposable()

    var accessTree: ActivityResultLauncher<Uri?>? = null
    var callForPrefFile: ActivityResultLauncher<Void?>? = null
    var callForBatteryOptimization: ActivityResultLauncher<Void?>? = null
    var requestMultiplePermissions: ActivityResultLauncher<Array<String>>? = null
    var onPermissionResultDenied: ((List<String>) -> Unit)? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        accessTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                // Check if user selected a subdirectory instead of root AAPS directory
                val lastPathSegment = uri.lastPathSegment ?: ""

                // Extract directory name from the path
                // First remove the storage prefix (e.g., "primary:")
                val pathAfterColon = when {
                    lastPathSegment.contains(":") -> lastPathSegment.substringAfterLast(":")
                    else                          -> lastPathSegment
                }
                // Then get just the last directory name (e.g., "AAPS/preferences" -> "preferences")
                val directoryName = pathAfterColon.substringAfterLast("/", pathAfterColon)

                // Warn if user selected a subdirectory instead of root AAPS directory
                // These subdirectories are managed by the app
                val managedSubdirectories = listOf("preferences", "extra", "exports", "temp")
                if (managedSubdirectories.any { it.equals(directoryName, ignoreCase = true) }) {
                    uiInteraction.showError(
                        this,
                        rh.gs(R.string.warning_wrong_directory_selected),
                        rh.gs(R.string.warning_wrong_directory_message, directoryName)
                    )
                    return@registerForActivityResult
                }

                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                preferences.put(StringKey.AapsDirectoryUri, uri.toString())
            }
        }
        callForPrefFile = registerForActivityResult(PrefsFileContract()) {
            // Do not pass full file through intent. It crashes on large file
            // it?.let {
            //     importExportPrefs.importSharedPreferences(this, it)
            // }
            importExportPrefs.doImportSharedPreferences(this)
        }
        callForBatteryOptimization = registerForActivityResult(OptimizationPermissionContract()) {
            updateButtons()
        }

        requestMultiplePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val denied = mutableListOf<String>()
            permissions.entries.forEach {
                aapsLogger.info(LTag.CORE, "Permission ${it.key} ${it.value}")
                if (!it.value) denied.add(it.key)
            }
            if (denied.isNotEmpty()) onPermissionResultDenied?.invoke(denied)
            updateButtons()
        }
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        accessTree = null
        callForPrefFile = null
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